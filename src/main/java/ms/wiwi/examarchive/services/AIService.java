package ms.wiwi.examarchive.services;

import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.ai.ExamAIJob;
import ms.wiwi.examarchive.ai.ExamAIStatus;
import ms.wiwi.examarchive.ai.OpenAIClient;
import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.Module;
import ms.wiwi.examarchive.model.Professor;
import ms.wiwi.examarchive.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, ExamAIJob> jobs = new ConcurrentHashMap<>();
    private final Repository repository;
    private final S3Service s3Service;
    private final QuartoSandboxService quartoSandboxService;
    private final AzureService azureService;
    private final OpenAIClient openAIClient;
    private final PdfService pdfService;

    public AIService(Repository repository, S3Service s3Service, QuartoSandboxService quartoSandboxService, PdfService pdfService, OpenAIClient openAIClient, AzureService azureService) {
        this.repository = repository;
        this.s3Service = s3Service;
        this.azureService = azureService;
        this.pdfService = pdfService;
        this.quartoSandboxService = quartoSandboxService;
        this.openAIClient = openAIClient;
    }

    /**
     * AI Exam flow:
     * 1.) Filter year, prof, ... in ui
     * 2.) Filter send to endpoint
     * 3.) Vision Scans fetched from DB
     * 4.) If Scan not available in DB, Scan via Azure Foundry or Azure Vision (Maybe async in parralel)
     * 5.) Send query to Azure GPT-5.4-Tera to create LaTeX
     * 6.) Send LaTeX to render service (some container, to be chosen)
     * 7.) Get PDF
     * 8.) Upload PDF to S3
     * 9.) Save entry in DB for generated Exam (Delete when user is deleted!)
     * 10.) Send presinged download link to client
     */


    public String generateExam(int untilYear, List<Professor> professors, String moduleid, User user, Consumer<ExamAIJob> onUpdate) {
        String id = UUID.randomUUID().toString();
        ExamAIJob job = new ExamAIJob(id, moduleid, ExamAIStatus.FETCH_EXAMS, user);
        for(ExamAIJob i : jobs.values()){
            if(i.user().id().equals(user.id()) && !(i.status() == ExamAIStatus.FAILED || i.status() == ExamAIStatus.DONE)){
                updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "You already have an exam generation in progress");
                return id;
            }
        }
        jobs.put(id, job);
        executor.submit(() -> {
            try {
                onUpdate.accept(job);
                if(azureService.getCurrentAmount() <= 150){
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "There seems to be a problem with Azure. Please contact the administrator");
                    return;
                }
                List<Exam> exams = repository.queryExamsFilterByDateAndProf(moduleid, untilYear, professors);
                if (exams.isEmpty()) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "No exams found with applied filters");
                    return;
                }
                List<Exam> examsWithoutScan = exams.stream().filter(exam -> exam.scan() == null).toList();
                if (!examsWithoutScan.isEmpty()) {
                    updateJobStatusAndNotify(job, ExamAIStatus.SCAN, onUpdate);
                    boolean success = scanExams(examsWithoutScan);
                    if (!success) {
                        updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "Could not scan exams");
                        return;
                    }
                    exams = repository.queryExamsFilterByDateAndProf(moduleid, untilYear, professors);
                }
                updateJobStatusAndNotify(job, ExamAIStatus.GENERATING, onUpdate);
                OpenAIClient.GenerationResult result = openAIClient.generateExam(exams);
                if (!result.success()) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, result.error());
                    return;
                }
                updateJobStatusAndNotify(job, ExamAIStatus.COMPILING, onUpdate);
                CompilationResult compilationResult = compileExam(result.latex(), job);
                if (!compilationResult.success()) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "Could not compile exam");
                    return;
                }
                updateJobStatusAndNotify(job, ExamAIStatus.UPLOADING, onUpdate);
                boolean uploadSuccess = uploadUserExamAndSaveToDB(compilationResult.pdfFile(), job.id(), user, result.inputToken(), result.outputToken(), moduleid);
                if (!uploadSuccess) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "Could not upload exam");
                    return;
                }
                updateJobStatusAndNotify(job, ExamAIStatus.DONE, onUpdate);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, e.getMessage());
            }
        });
        return id;
    }

    private CompilationResult compileExam(String quarto, ExamAIJob job) {
        Module module = repository.getModule(job.moduleid());
        String yaml = """
                ---
                title: "%s"
                subtitle: "FS WiWi | KI generierte Aufgaben können unlösbar oder nicht eindeutig sein"
                format:
                  typst:
                    papersize: a4
                    margin:
                      x: 2.5cm
                      y: 2.5cm
                    mainfont: "Latin Modern Roman"
                execute:
                  echo: false
                  warning: false
                  message: false
                  error: true
                ---
                
                """.formatted(module.name() != null ? module.name() : "Klausur");
        try {
            byte[] bytes = quartoSandboxService.renderQmd(yaml + quarto);
            return new CompilationResult(true, bytes);
        } catch (Exception e) {
            logger.error("Failed to compile exam. Latex: " + yaml + quarto, e);
            return new CompilationResult(false, null);
        }
    }

    private boolean scanExams(List<Exam> examsWithoutScan) {
        AtomicBoolean success = new AtomicBoolean(true);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Exam item : examsWithoutScan) {
                executor.submit(() -> {
                    try {
                        logger.info("Scanning Exam " + item.examID() + " (" + item.name() + ")");
                        byte[] examPDF = s3Service.downloadFile(item.fileID(), S3Service.Bucket.EXAMS);
                        List<byte[]> images = pdfService.convertPdfToImages(examPDF, 150, 50);
                        String scan = openAIClient.scanExamWithAI(images);
                        Exam newExam = new Exam(item.name(),
                                item.examID(),
                                item.moduleID(),
                                item.year(),
                                item.semester(),
                                item.uploadDate(),
                                item.fileID(),
                                item.uploaderID(),
                                item.status(),
                                item.professorID(),
                                scan);
                        repository.updateExam(newExam);
                        logger.info("Exam " + item.examID() + " (" + item.name() + ") scanned scanned successfully");
                    } catch (Exception e) {
                        success.set(false);
                        logger.error("Could not process exam {} : {}", item.examID(), e.getMessage(), e);
                    }
                });
            }
        }
        return success.get();
    }

    /**
     * Uploads the exam to a user exam specific bucket and writes it to the db
     *
     * @param pdfFile Exam to upload
     * @return true if successfull
     */
    private boolean uploadUserExamAndSaveToDB(byte[] pdfFile, String id, User user, int inputToken, int outputToken, String moduleid) {
        File tempfile = null;
        boolean success;
        try {
            tempfile = File.createTempFile(id, ".pdf");
            Files.write(tempfile.toPath(), pdfFile, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            s3Service.uploadPDF(tempfile, id, S3Service.Bucket.USER_EXAMS);
            success = true;
            Module module = repository.getModule(moduleid);
            repository.addUserExam(id, user, inputToken, outputToken, module.name() + "-" + id.substring(5));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (tempfile != null) {
                try {
                    Files.deleteIfExists(tempfile.toPath());
                } catch (IOException e) {
                    logger.error("Could not delete temp file " + tempfile.getAbsolutePath(), e);
                }
            }
        }
        return success;
    }

    private void updateJobStatusAndNotify(ExamAIJob job, ExamAIStatus status, Consumer<ExamAIJob> onUpdate) {
        updateJobStatusAndNotify(job, status, onUpdate, null);
    }

    private void updateJobStatusAndNotify(ExamAIJob job, ExamAIStatus status, Consumer<ExamAIJob> onUpdate, String error) {
        jobs.put(job.id(), job);
        if (error != null) {
            job.errorMessage(error);
        }
        job.status(status);
        onUpdate.accept(job);
        if (status == ExamAIStatus.DONE || status == ExamAIStatus.FAILED) {
            String jobId = job.id();
            Thread.ofVirtual().name("job-cleanup-" + jobId).start(() -> {
                try {
                    Thread.sleep(Duration.ofMinutes(15));
                } catch (InterruptedException _) {
                } finally {
                    jobs.remove(jobId);
                }
            });
        }
    }

    private record CompilationResult(boolean success, byte[] pdfFile) {}

    public ExamAIJob getJob(String id) {
        return jobs.get(id);
    }
}

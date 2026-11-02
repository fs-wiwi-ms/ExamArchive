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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private static final int MAX_CONCURRENT_GENERATIONS_PER_USER = 4;
    private static final Pattern EXAM_HEADER_PATTERN = Pattern.compile("(?s)\\A===EXAM START===\\R(.*?)\\R===EXAM END===");
    private static final Pattern TASK_PLAN_PATTERN = Pattern.compile("(?s)===TASK START===\\R(.*?)\\R===TASK END===");
    private static final Pattern LEADING_OR_TRAILING_SEPARATOR = Pattern.compile("(?:\\A|\\z)\\s*(?:(?:---|\\*\\*\\*|___)\\s*)+");
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, ExamAIJob> jobs = new ConcurrentHashMap<>();
    private final Repository repository;
    private final S3Service s3Service;
    private final QuartoSandboxService quartoSandboxService;
    private final OpenAIClient openAIClient;
    private final PdfService pdfService;
    private final ApiKeyEncryptionService apiKeyEncryptionService;

    public AIService(Repository repository, S3Service s3Service, QuartoSandboxService quartoSandboxService, PdfService pdfService, OpenAIClient openAIClient, ApiKeyEncryptionService apiKeyEncryptionService) {
        this.repository = repository;
        this.s3Service = s3Service;
        this.pdfService = pdfService;
        this.quartoSandboxService = quartoSandboxService;
        this.openAIClient = openAIClient;
        this.apiKeyEncryptionService = apiKeyEncryptionService;
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
                String apiKey = repository.getApiKeyForUser(user);
                apiKey = apiKeyEncryptionService.decrypt(apiKey);
                if(!openAIClient.testAPIKey(apiKey)){
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "Invalid API key");
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
                    boolean success = scanExams(examsWithoutScan, apiKey);
                    if (!success) {
                        updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "Could not scan exams");
                        return;
                    }
                    exams = repository.queryExamsFilterByDateAndProf(moduleid, untilYear, professors);
                }
                updateJobStatusAndNotify(job, ExamAIStatus.PLANNING, onUpdate);
                OpenAIClient.GenerationResult planResult = openAIClient.planExam(exams, apiKey);
                if (!planResult.success()) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, planResult.error());
                    return;
                }
                ExamPlan examPlan = parseExamPlan(planResult.latex());
                if (examPlan == null) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "The exam plan did not follow the required structure");
                    return;
                }
                updateJobStatusAndNotify(job, ExamAIStatus.GENERATING_TASKS, onUpdate);
                OpenAIClient.GenerationResult result = generateTasks(examPlan.taskPlans(), apiKey);
                if (!result.success()) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, result.error());
                    return;
                }
                result = new OpenAIClient.GenerationResult(
                        true,
                        stitchExam(examPlan.header(), result.latex()),
                        planResult.inputToken() + result.inputToken(),
                        planResult.outputToken() + result.outputToken(),
                        null
                );
                updateJobStatusAndNotify(job, ExamAIStatus.COMPILING, onUpdate);
                CompilationResult compilationResult = compileExam(result.latex(), job);
                if (!compilationResult.success()) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, compilationResult.error());
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

    private ExamPlan parseExamPlan(String plan) {
        Matcher headerMatcher = EXAM_HEADER_PATTERN.matcher(plan);
        if (!headerMatcher.find() || headerMatcher.group(1).isBlank()) {
            return null;
        }
        String remainingPlan = plan.substring(headerMatcher.end());
        Matcher matcher = TASK_PLAN_PATTERN.matcher(remainingPlan);
        List<String> taskPlans = new ArrayList<>();
        int previousEnd = 0;
        while (matcher.find()) {
            if (!remainingPlan.substring(previousEnd, matcher.start()).isBlank() || matcher.group(1).isBlank()) {
                return null;
            }
            taskPlans.add(matcher.group(1).trim());
            previousEnd = matcher.end();
        }
        if (!remainingPlan.substring(previousEnd).isBlank() || taskPlans.isEmpty()) {
            return null;
        }
        String header = stripOuterSeparators(headerMatcher.group(1));
        return header.isBlank() ? null : new ExamPlan(header, taskPlans);
    }

    private OpenAIClient.GenerationResult generateTasks(List<String> taskPlans, String apiKey) throws InterruptedException {
        Semaphore perUserLimit = new Semaphore(MAX_CONCURRENT_GENERATIONS_PER_USER);
        List<Future<OpenAIClient.GenerationResult>> futures = new ArrayList<>();
        for (int i = 0; i < taskPlans.size(); i++) {
            String taskPlan = taskPlans.get(i);
            int taskNumber = i + 1;
            futures.add(executor.submit(() -> {
                perUserLimit.acquire();
                try {
                    return openAIClient.generateTask(taskPlan, taskNumber, apiKey);
                } finally {
                    perUserLimit.release();
                }
            }));
        }

        StringBuilder quarto = new StringBuilder();
        int inputTokens = 0;
        int outputTokens = 0;
        for (Future<OpenAIClient.GenerationResult> future : futures) {
            try {
                OpenAIClient.GenerationResult result = future.get();
                if (!result.success()) {
                    cancelTasks(futures);
                    return OpenAIClient.GenerationResult.fail(result.error());
                }
                if (!quarto.isEmpty()) {
                    quarto.append("\n\n");
                }
                quarto.append(stripOuterSeparators(result.latex()));
                inputTokens += result.inputToken();
                outputTokens += result.outputToken();
            } catch (ExecutionException e) {
                logger.error("Task generation failed", e.getCause());
                cancelTasks(futures);
                return OpenAIClient.GenerationResult.fail("Task generation exception: " + e.getCause().getMessage());
            } catch (InterruptedException e) {
                cancelTasks(futures);
                throw e;
            }
        }
        return new OpenAIClient.GenerationResult(true, quarto.toString(), inputTokens, outputTokens, null);
    }

    private void cancelTasks(List<Future<OpenAIClient.GenerationResult>> futures) {
        futures.forEach(future -> future.cancel(true));
    }

    private String stitchExam(String header, String tasks) {
        return stripOuterSeparators(header) + "\n\n---\n\n" + stripOuterSeparators(tasks);
    }

    private String stripOuterSeparators(String content) {
        String stripped = content.trim();
        while (true) {
            String withoutLeadingSeparator = LEADING_OR_TRAILING_SEPARATOR.matcher(stripped).replaceFirst("").trim();
            if (withoutLeadingSeparator.equals(stripped)) {
                return stripped;
            }
            stripped = withoutLeadingSeparator;
        }
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
            return new CompilationResult(true, bytes, null);
        } catch (Exception e) {
            logger.error("Failed to compile exam. Latex: " + yaml + quarto, e);
            return new CompilationResult(false, null, "Could not compile exam: " + shortenError(e.getMessage()));
        }
    }

    private String shortenError(String error) {
        if (error == null || error.isBlank()) {
            return "Quarto returned no error details";
        }
        return error.length() <= 1_000 ? error : error.substring(0, 1_000) + "...";
    }

    private boolean scanExams(List<Exam> examsWithoutScan, String apiKey) {
        AtomicBoolean success = new AtomicBoolean(true);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Exam item : examsWithoutScan) {
                executor.submit(() -> {
                    try {
                        logger.info("Scanning Exam " + item.examID() + " (" + item.name() + ")");
                        byte[] examPDF = s3Service.downloadFile(item.fileID(), S3Service.Bucket.EXAMS);
                        List<byte[]> images = pdfService.convertPdfToImages(examPDF, 150, 50);
                        String scan = openAIClient.scanExamWithAI(images, apiKey);
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

    private record ExamPlan(String header, List<String> taskPlans) {}

    private record CompilationResult(boolean success, byte[] pdfFile, String error) {}

    public ExamAIJob getJob(String id) {
        return jobs.get(id);
    }
}

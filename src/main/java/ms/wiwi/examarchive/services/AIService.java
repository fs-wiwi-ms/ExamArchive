package ms.wiwi.examarchive.services;

import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.ai.ExamAIJob;
import ms.wiwi.examarchive.ai.ExamAIStatus;
import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.Professor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Repository repository;

    public AIService(Repository repository) {
        this.repository = repository;
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


    public void generateExam(int untilYear, List<Professor> professors, Consumer<ExamAIJob> onUpdate) {
        executor.submit(() -> {
            ExamAIJob job = new ExamAIJob(UUID.randomUUID().toString(), ExamAIStatus.FETCH_EXAMS);
            try {
                onUpdate.accept(job);
                List<Exam> exams = repository.queryExamsFilterByDateAndProf(untilYear, professors);
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
                    exams = repository.queryExamsFilterByDateAndProf(untilYear, professors);
                }
                updateJobStatusAndNotify(job, ExamAIStatus.GENERATING, onUpdate);
                GenerationResult result = generateExamsFromList(exams);
                if (!result.success()) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "Could not generate exams. Please contact us");
                    return;
                }
                updateJobStatusAndNotify(job, ExamAIStatus.COMPILING, onUpdate);
                CompilationResult compilationResult = compileExam(result.latex());
                if(!compilationResult.success()) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "Could not compile exam");
                    //TODO maybe retry with ai to fix LaTeX code?
                    return;
                }
                updateJobStatusAndNotify(job, ExamAIStatus.UPLOADING, onUpdate);
                boolean uploadSuccess = uploadUserExam(compilationResult.exam(), job.id());
                if (!uploadSuccess) {
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, "Could not upload exam");
                    return;
                }
                updateJobStatusAndNotify(job, ExamAIStatus.DONE, onUpdate);
            } catch (RuntimeException e) {
                logger.error(e.getMessage(), e);
                updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, e.getMessage());
                return;
            }
        });
    }

    /**
     * Uploads the exam to a user exam specific bucket and writes it to the db
     * @param exam Exam to upload
     * @return true if successfull
     */
    private boolean uploadUserExam(File exam, String id) {
        return false;
    }

    private void updateJobStatusAndNotify(ExamAIJob job, ExamAIStatus status, Consumer<ExamAIJob> onUpdate) {
        updateJobStatusAndNotify(job, status, onUpdate, null);
    }

    private void updateJobStatusAndNotify(ExamAIJob job, ExamAIStatus status, Consumer<ExamAIJob> onUpdate, String error) {
        job.status(status);
        if (error != null) {
            job.errorMessage(error);
        }
        onUpdate.accept(job);
    }

    private record GenerationResult(boolean success, String latex, int inputToken, int outputToken) {}
    private record CompilationResult(boolean success, File exam) {}
}

package ms.wiwi.examarchive.services;

import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.ai.ExamAIJob;
import ms.wiwi.examarchive.ai.ExamAIStatus;
import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.Professor;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Repository repository;
    private final S3Service s3Service;
    private final OkHttpClient httpClient;
    private static final Semaphore scanSemaphore = new Semaphore(3);
    private static final Semaphore genSemaphore = new Semaphore(3);
    private final String aiEndpoint;
    private final String apiKey;
    private String scanPrompt;
    private String genPrompt;

    public AIService(Repository repository, S3Service s3Service, String openAIEndpoint, String apiKey) throws IOException {
        this.repository = repository;
        this.s3Service = s3Service;
        this.aiEndpoint = openAIEndpoint;
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder().callTimeout(Duration.of(6, ChronoUnit.MINUTES)).readTimeout(Duration.of(5, ChronoUnit.MINUTES)).connectTimeout(Duration.of(15, ChronoUnit.SECONDS)).connectionPool(new ConnectionPool(10, 6, TimeUnit.MINUTES)).build();
        try (InputStream scanPromptStream = AIService.class.getResourceAsStream("promts/scan.md");
             InputStream genPromptStream = AIService.class.getResourceAsStream("prompts/gen.md")) {
            scanPrompt = IOUtils.toString(scanPromptStream, StandardCharsets.UTF_8);
            genPrompt = IOUtils.toString(genPromptStream, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
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
                if (!compilationResult.success()) {
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

    private boolean scanExams(List<Exam> examsWithoutScan) {
        AtomicBoolean success = new AtomicBoolean(true);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Exam item : examsWithoutScan) {
                executor.submit(() -> {
                    try {
                        scanSemaphore.acquire();
                        scanExamWithAI(item);
                    } catch (Exception e) {
                        success.set(false);
                        logger.error("Could not process exam " + item.examID(), e);
                    } finally {
                        scanSemaphore.release();
                    }
                });
            }
        }
        return success.get();
    }

    /**
     * Scan the exam with a Kimi model and saves the scanned exam to the db
     *
     * @param exam Exam to scan
     */
    private void scanExamWithAI(Exam exam) { //TODO: Refactor this and S3service pdf serialization to PDFService
        byte[] rawFileData = s3Service.downloadFile(exam.examID(), S3Service.Bucket.EXAMS);
        List<byte[]> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(rawFileData)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 150);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "JPEG", baos);
                    images.add(baos.toByteArray());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (RuntimeException | IOException e) {
            logger.error("Error while converting PDF to JPEG", e);
            throw new RuntimeException(e);
        }
        JsonMapper mapper = new JsonMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("model", "Kimi-K2.6");
        root.put("temperature", 0.1);
        root.put("max_tokens", 8000);
        ArrayNode messages = root.putArray("messages");
        ObjectNode systemMessage = mapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", scanPrompt);
        messages.add(systemMessage);
        ObjectNode userMessage = mapper.createObjectNode();
        userMessage.put("role", "user");
        ArrayNode examContent = userMessage.putArray("content");
        ObjectNode contentInstruction = examContent.addObject();
        contentInstruction.put("type", "text");
        contentInstruction.put("text", "These are all pages you should scan");
        for(byte [] image: images) {
            ObjectNode imageNode = examContent.addObject();
            imageNode.put("type", "image_url");
            ObjectNode imageUrl = imageNode.putObject("image_url");
            imageUrl.put("url", "data:image/jpeg;base64," +  Base64.getEncoder().encodeToString(image));
            imageUrl.put("detail", "high");
        }
        Request request = new Request.Builder()
                .url(aiEndpoint)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(mapper.writeValueAsString(root), MediaType.parse("application/json")))
                .build();
        String markdown = null;
        try {
            try (Response response = httpClient.newCall(request).execute()) {
                if(!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                JsonNode responseRoot = mapper.readTree(response.body().string());
                String finishReason = responseRoot.path("choices").path(0).path("finish_reason").asString();
                if (!"stop".equalsIgnoreCase(finishReason)) {
                   logger.error("Finish Reason: " + finishReason + " for exam " + exam.examID() + " (" + exam.name() + ")");
                   throw new RuntimeException("Invalid Finish Reason: " + finishReason);
                }
                markdown = responseRoot.path("choices").path(0).path("message").path("content").asString();
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(markdown == null) {
            throw new IllegalStateException("No markdown found for exam " + exam.examID());
        }
        Exam scannedExam = new Exam(exam.name(), exam.examID(), exam.moduleID(), exam.year(), exam.semester(), exam.uploadDate(), exam.fileID(), exam.uploaderID(), exam.status(), exam.professorID(), markdown);
        repository.updateExam(scannedExam);
    }

    /**
     * Uploads the exam to a user exam specific bucket and writes it to the db
     *
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

    private record GenerationResult(boolean success, String latex, int inputToken, int outputToken) {
    }

    private record CompilationResult(boolean success, File exam) {
    }
}

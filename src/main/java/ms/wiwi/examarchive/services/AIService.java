package ms.wiwi.examarchive.services;

import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.ai.ExamAIJob;
import ms.wiwi.examarchive.ai.ExamAIStatus;
import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.Professor;
import ms.wiwi.examarchive.model.User;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
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
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final Semaphore scanSemaphore = new Semaphore(3);
    private static final Semaphore genSemaphore = new Semaphore(3);
    private final Map<String, ExamAIJob> jobs = new ConcurrentHashMap<>();
    private final Repository repository;
    private final S3Service s3Service;
    private final OkHttpClient httpClient;
    private final JsonMapper mapper;
    private final String aiEndpoint;
    private final String apiKey;
    private final String restlatexURL;
    private String scanPrompt;
    private String genPrompt;

    public AIService(Repository repository, S3Service s3Service, String openAIEndpoint, String apiKey, String restlatexURL) throws IOException {
        this.repository = repository;
        this.s3Service = s3Service;
        this.restlatexURL = restlatexURL;
        this.mapper = new JsonMapper();
        this.aiEndpoint = openAIEndpoint;
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder().callTimeout(Duration.of(6, ChronoUnit.MINUTES)).readTimeout(Duration.of(5, ChronoUnit.MINUTES)).connectTimeout(Duration.of(15, ChronoUnit.SECONDS)).connectionPool(new ConnectionPool(10, 6, TimeUnit.MINUTES)).build();
        try (InputStream scanPromptStream = AIService.class.getResourceAsStream("/prompts/scan.md");
             InputStream genPromptStream = AIService.class.getResourceAsStream("/prompts/gen.md")) {
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


    public String generateExam(int untilYear, List<Professor> professors, User user, Consumer<ExamAIJob> onUpdate) {
        String id = UUID.randomUUID().toString();
        ExamAIJob job = new ExamAIJob(id, ExamAIStatus.FETCH_EXAMS);
        jobs.put(id, job);
        executor.submit(() -> {
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
                    updateJobStatusAndNotify(job, ExamAIStatus.FAILED, onUpdate, result.error());
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
                boolean uploadSuccess = uploadUserExamAndSaveToDB(compilationResult.pdfFile(), job.id(), user, result.inputToken(), result.outputToken());
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

    private CompilationResult compileExam(String latex) {
        try {
            Request request = new Request.Builder()
                    .url(restlatexURL + "/api/compile")
                    .post(RequestBody.create(latex, MediaType.parse("text/plain")))
                    .build();
            try(Response response = httpClient.newCall(request).execute()){
                if(!response.isSuccessful()){
                    logger.error("Error trying to compile exam: " + response.body().string());
                    throw new IOException("Unexpected code " + response);
                }
                byte[] bytes = response.body().bytes();
                return new CompilationResult(true, bytes);
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            logger.error("Failed to compile exam. Latex: " + latex, e);
            return new CompilationResult(false, null);
        }
    }

    private GenerationResult generateExamsFromList(List<Exam> exams) throws InterruptedException {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", "Phi-4-reasoning");
        root.put("max_tokens", 10000);
        root.put("temperature", 0.35);

        ArrayNode messages = root.putArray("messages");
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", genPrompt);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        StringBuilder userContent = new StringBuilder();
        userContent.append("Generate a new, equivalent exam based on the reference exams provided below.\n\n");
        for (int i = 0; i < exams.size(); i++) {
            Exam exam = exams.get(i);
            userContent.append("Exam #").append(i + 1).append(" [year=").append(exam.year()).append("] : ").append(exam.scan());
            userContent.append("\n\n");
        }
        userMessage.put("content", userContent.toString());

        logger.info("Generating exam. Prompt length in chars: {}", userContent.length());

        Request request = new Request.Builder()
                .url(aiEndpoint)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(mapper.writeValueAsString(root), MediaType.parse("application/json")))
                .build();

        genSemaphore.acquire();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                logger.error("Azure AI call failed with HTTP {}: {}", response.code(), responseBody);
                return GenerationResult.fail("Azure HTTP error " + response.code() + ": " + responseBody);
            }

            JsonNode jsonNode = mapper.readTree(responseBody);
            if (jsonNode.has("error")) {
                String errorMsg = jsonNode.get("error").path("message").asString("Unknown error");
                logger.error("Azure payload returned error: {}", errorMsg);
                return GenerationResult.fail("Azure error: " + errorMsg);
            }

            JsonNode choices = jsonNode.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                logger.error("Empty choices array in response: {}", responseBody);
                return GenerationResult.fail("Model returned no choices");
            }

            JsonNode firstChoice = choices.get(0);
            String finishReason = firstChoice.path("finish_reason").asString("");

            if ("length".equalsIgnoreCase(finishReason)) {
                logger.error("Model generation truncated: max_tokens reached");
                return GenerationResult.fail("Max tokens exceeded during generation");
            }
            if (!"stop".equalsIgnoreCase(finishReason)) {
                logger.error("Unexpected finish_reason: '{}'. Full choice: {}", finishReason, firstChoice);
                return GenerationResult.fail("Unexpected stop condition: " + finishReason);
            }

            String rawContent = firstChoice.path("message").path("content").asString(null);
            if (rawContent == null || rawContent.isBlank()) {
                logger.error("Empty content in model response. Raw: {}", responseBody);
                return GenerationResult.fail("No content generated by model");
            }

            String latex = cleanLatex(rawContent);
            JsonNode usage = jsonNode.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);

            return new GenerationResult(true, latex, promptTokens, completionTokens, null);

        } catch (Exception e) {
            logger.error("Exception occurred during exam generation", e);
            return GenerationResult.fail("Generation exception: " + e.getMessage());
        } finally {
            genSemaphore.release();
        }
    }

    private static String cleanLatex(String input) {
        String cleaned = input;
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "");
        cleaned = cleaned.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\R?", "");
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }
        return cleaned;
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
        logger.info("Scanning exam " + exam.name());
        byte[] rawFileData = s3Service.downloadFile(exam.fileID(), S3Service.Bucket.EXAMS);
        List<byte[]> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(rawFileData)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 150, ImageType.RGB);
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
        messages.add(userMessage);
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
                    logger.error("Could not scan exam " + exam.name() + ": " + response.body().string());
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
        logger.info("Exam scan: " + scannedExam.scan());
    }

    /**
     * Uploads the exam to a user exam specific bucket and writes it to the db
     *
     * @param pdfFile Exam to upload
     * @return true if successfull
     */
    private boolean uploadUserExamAndSaveToDB(byte[] pdfFile, String id, User user, int inputToken, int outputToken) {
        File tempfile = null;
        boolean success;
        try {
            tempfile = File.createTempFile(id, ".pdf");
            Files.write(tempfile.toPath(), pdfFile, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            s3Service.uploadPDF(tempfile, id, S3Service.Bucket.USER_EXAMS);
            success = true;
            repository.addUserExam(id, user, inputToken, outputToken);
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

    private record GenerationResult(boolean success, String latex, int inputToken, int outputToken, String error) {
        public static GenerationResult fail(String error) {
            return new GenerationResult(false, null, 0, 0, error);
        }
    }

    private record CompilationResult(boolean success, byte[] pdfFile) {
    }

    public ExamAIJob getJob(String id) {
        return jobs.get(id);
    }
}

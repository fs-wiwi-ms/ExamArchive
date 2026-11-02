package ms.wiwi.examarchive.ai;

import ms.wiwi.examarchive.model.Exam;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * A client for creating requests for ExamAI to an OpenAI compatible API.
 */
public class OpenAIClient {
    private final String apiBaseUrl;
    private final OkHttpClient httpClient;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String scanPrompt;
    private final String planPrompt;
    private final String generatePrompt;
    private final Semaphore semaphore;
    private final String llmModel;
    private final boolean verbose;
    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);

    public OpenAIClient(String apiBaseUrl, String scanPrompt, String planPrompt, String generatePrompt, int concurrentRequests, String llmModel, boolean verbose) {
        this.apiBaseUrl = apiBaseUrl;
        this.httpClient = new OkHttpClient.Builder().callTimeout(Duration.of(10, ChronoUnit.MINUTES)).readTimeout(Duration.of(10, ChronoUnit.MINUTES)).connectTimeout(Duration.of(15, ChronoUnit.SECONDS)).connectionPool(new ConnectionPool(10, 6, TimeUnit.MINUTES)).build();
        this.scanPrompt = scanPrompt;
        this.planPrompt = planPrompt;
        this.generatePrompt = generatePrompt;
        this.semaphore = new Semaphore(concurrentRequests);
        this.llmModel = llmModel;
        this.verbose = verbose;
    }

    /**
     * Plans an exam based on the provided reference exams.
     * @param exams The reference exams.
     * @return The generated task plan.
     * @throws Exception If an error occurs during planning.
     */
    public GenerationResult planExam(List<Exam> exams, String apiKey) throws Exception {
        return generate(createPlanningPrompt(exams), apiKey, "exam planning");
    }

    public GenerationResult generateTask(String taskPlan, int taskNumber, String apiKey) throws Exception {
        return generate(createTaskPrompt(taskPlan, taskNumber), apiKey, "task generation");
    }

    private GenerationResult generate(String prompt, String apiKey, String operation) throws Exception {
        semaphore.acquire();
        try {
            logger.info("{} prompt length in chars: {}", operation, prompt.length());
            logRequest(operation, prompt);
            Request request = new Request.Builder()
                    .url(apiBaseUrl + "/v1/chat/completions")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(prompt, MediaType.parse("application/json")))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                logResponse(operation, responseBody);
                if (!response.isSuccessful()) {
                    logger.error("Open AI call failed with HTTP {}: {}", response.code(), responseBody);
                    return GenerationResult.fail("HTTP error " + response.code() + ": " + responseBody);
                }

                JsonNode jsonNode = mapper.readTree(responseBody);
                if (jsonNode.has("error")) {
                    String errorMsg = jsonNode.get("error").path("message").asString("Unknown error");
                    logger.error("Open AI payload returned error: {}", errorMsg);
                    return GenerationResult.fail("Error: " + errorMsg);
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

                String quarto = rawContent.trim();
                JsonNode usage = jsonNode.path("usage");
                int promptTokens = usage.path("prompt_tokens").asInt(0);
                int completionTokens = usage.path("completion_tokens").asInt(0);

                return new GenerationResult(true, quarto, promptTokens, completionTokens, null);

            } catch (Exception e) {
                    logger.error("Exception occurred during {}", operation, e);
                    return GenerationResult.fail(operation + " exception: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error during {}", operation, e);
            return GenerationResult.fail("Error during " + operation + ": " + e.getMessage());
        } finally {
            semaphore.release();
        }
    }

    /**
     * Creates a planning prompt from the provided reference exams.
     * @param exams The reference exams
     * @return The generated prompt
     */
    private String createPlanningPrompt(List<Exam> exams){
        ObjectNode root = mapper.createObjectNode();
        root.put("model", llmModel);
        root.put("reasoning_effort", "medium");
        ArrayNode messages = root.putArray("messages");
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", planPrompt);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        StringBuilder userContent = new StringBuilder();
        userContent.append("Plan a new, equivalent exam based on the reference exams provided below.\n\n");
        for (int i = 0; i < exams.size(); i++) {
            Exam exam = exams.get(i);
            userContent.append("Exam #").append(i + 1).append(" [year=").append(exam.year()).append("] : ").append(exam.scan());
            userContent.append("\n\n");
        }
        userMessage.put("content", userContent.toString());
        return mapper.writeValueAsString(root);
    }

    private String createTaskPrompt(String taskPlan, int taskNumber) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", llmModel);
        root.put("reasoning_effort", "medium");
        ArrayNode messages = root.putArray("messages");
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", generatePrompt);
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", "This is task number " + taskNumber + ". Use that number in the task heading and generate and verify exactly this planned task.\n\n" + taskPlan);
        return mapper.writeValueAsString(root);
    }

    /**
     * scans the exam with a GPT model and returns the scan
     *
     * @param examPictures Exam pages to scan as image
     */
    public String scanExamWithAI(List<byte[]> examPictures, String apiKey) throws Exception{
        String payload = createScanPayload(examPictures);
        logRequest("exam scan", payload);
        Request request = new Request.Builder()
                .url(apiBaseUrl + "/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(payload, MediaType.parse("application/json")))
                .build();
        String markdown;
        try {
            semaphore.acquire();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                logResponse("exam scan", responseBody);
                if(!response.isSuccessful()) {
                    logger.error("Could not scan exam " + responseBody);
                    throw new IOException("Unexpected code " + responseBody);
                }
                JsonNode responseRoot = mapper.readTree(responseBody);
                String finishReason = responseRoot.path("choices").path(0).path("finish_reason").asString();
                if (!"stop".equalsIgnoreCase(finishReason)) {
                    logger.error("Finish Reason: " + finishReason + " for exam");
                    throw new RuntimeException("Invalid Finish Reason: " + finishReason);
                }
                markdown = responseRoot.path("choices").path(0).path("message").path("content").asString();
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
        if(markdown == null) {
            throw new IllegalStateException("No markdown found for exam");
        }
        return markdown;
    }

    private String createScanPayload(List<byte[]> pages){
        ObjectNode root = mapper.createObjectNode();
        root.put("model", llmModel);
        root.put("reasoning_effort", "low");
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
        for(int i = 0; i < pages.size() && i < 50; i++) {
            byte[] image = pages.get(i);
            ObjectNode imageNode = examContent.addObject();
            imageNode.put("type", "image_url");
            ObjectNode imageUrl = imageNode.putObject("image_url");
            imageUrl.put("url", "data:image/jpeg;base64," +  Base64.getEncoder().encodeToString(image));
            imageUrl.put("detail", "high");
        }
        messages.add(userMessage);
        return mapper.writeValueAsString(root);
    }

    public boolean testAPIKey(String apiKey) {
        Request request = new Request.Builder()
                .url(apiBaseUrl + "/key/info")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        logRequest("API key test", "GET " + apiBaseUrl + "/key/info");
        try (Response response = httpClient.newCall(request).execute()){
            String responseBody = response.body().string();
            logResponse("API key test", responseBody);
            if(!response.isSuccessful()){
                return false;
            }
            if(responseBody.isEmpty()){
                return false;
            }
            JsonNode root = mapper.readTree(responseBody);
            JsonNode infoNode = root.get("info");
            if(infoNode == null){
                return false;
            }
            JsonNode status = infoNode.get("status");
            return status != null && status.asString().equals("active");
        } catch (IOException e) {
            return false;
        }
    }

    private void logRequest(String operation, String payload) {
        if (verbose) {
            logger.info("OpenAI {} request: {}", operation, payload);
        }
    }

    private void logResponse(String operation, String response) {
        if (verbose) {
            logger.info("OpenAI {} response: {}", operation, response);
        }
    }


    public record GenerationResult(boolean success, String latex, int inputToken, int outputToken, String error) {
        public static GenerationResult fail(String error) {
            return new GenerationResult(false, null, 0, 0, error);
        }
    }
}

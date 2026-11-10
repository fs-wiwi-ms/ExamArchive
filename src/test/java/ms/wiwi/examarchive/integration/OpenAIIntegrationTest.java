package ms.wiwi.examarchive.integration;

import ms.wiwi.examarchive.ai.OpenAIClient;
import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.ExamStatus;
import ms.wiwi.examarchive.model.Semester;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

@Testcontainers
public class OpenAIIntegrationTest {

    @Container
    static MockServerContainer mockServerContainer = new MockServerContainer(DockerImageName.parse("mockserver/mockserver:9.2.0"));

    private MockServerClient mockClient;
    private static OpenAIClient openAIClient;
    private static final String SCAN_PROMT = "SCAN";
    private static final String PLAN_PROMT = "PLAN";
    private static final String GEN_PROMT = "GEN";
    private static final String API_KEY = "test-api-key";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @BeforeAll
    static void setUpOpenAIClient() {
        openAIClient = new OpenAIClient("http://" + mockServerContainer.getHost() + ":" + mockServerContainer.getServerPort(),
                SCAN_PROMT,
                PLAN_PROMT,
                GEN_PROMT,
                10,
                "Test",
                "Test",
                "Test",
                true
                );
    }

    @BeforeEach
    void setUp() {
        mockClient = new MockServerClient(mockServerContainer.getHost(), mockServerContainer.getServerPort());
        mockClient.reset();
    }

    @Test
    void testKey_validKey_returnTrue(){
        String validKey = UUID.randomUUID().toString();
        mockClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/key/info")
                        .withHeader("Authorization", "Bearer " + validKey)
        ).respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(json("""
                        {
                          "info": {
                            "status": "active"
                          }
                        }
                        """)));
        assertTrue(openAIClient.testAPIKey(validKey));
    }

    @Test
    void testKey_invalidKey_returnFalse(){
        String validKey = UUID.randomUUID().toString();
        mockClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/key/info")
                        .withHeader("Authorization", "Bearer " + validKey)
        ).respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(json("""
                        {
                          "info": {
                            "status": "expired"
                          }
                        }
                        """)));
        assertFalse(openAIClient.testAPIKey(validKey));
    }

    @Test
    void planExam_sendsPlanningPayloadAndParsesCompletion() throws Exception {
        respondToCompletion(successfulCompletion("planned exam", 17, 23));

        OpenAIClient.GenerationResult result = openAIClient.planExam(List.of(exam(2024, "Reference scan")), API_KEY);

        assertTrue(result.success());
        assertEquals("planned exam", result.latex());
        assertEquals(17, result.inputToken());
        assertEquals(23, result.outputToken());

        JsonNode payload = recordedChatCompletionPayload();
        assertEquals("Test", payload.path("model").asString());
        assertEquals("medium", payload.path("reasoning_effort").asString());
        assertEquals("PLAN", payload.path("messages").path(0).path("content").asString());
        assertTrue(payload.path("messages").path(1).path("content").asString().contains("Exam #1 [year=2024] : Reference scan"));
    }

    @Test
    void generateTask_sendsTaskPayloadAndParsesCompletion() throws Exception {
        respondToCompletion(successfulCompletion("generated task", 11, 19));

        OpenAIClient.GenerationResult result = openAIClient.generateTask("Task plan", 3, API_KEY);

        assertTrue(result.success());
        assertEquals("generated task", result.latex());
        assertEquals(11, result.inputToken());
        assertEquals(19, result.outputToken());

        JsonNode payload = recordedChatCompletionPayload();
        assertEquals("GEN", payload.path("messages").path(0).path("content").asString());
        assertTrue(payload.path("messages").path(1).path("content").asString().contains("This is task number 3"));
        assertTrue(payload.path("messages").path(1).path("content").asString().contains("Task plan"));
    }

    @Test
    void planExam_returnsFailureForHttpError() throws Exception {
        respondToCompletion("{\"message\":\"invalid key\"}", 401);

        OpenAIClient.GenerationResult result = openAIClient.planExam(List.of(exam(2024, "scan")), API_KEY);

        assertFalse(result.success());
        assertEquals("HTTP error 401: {\"message\":\"invalid key\"}", result.error());
    }

    @Test
    void planExam_returnsFailureForErrorPayload() throws Exception {
        respondToCompletion("{\"error\": {\"message\": \"rate limited\"}}", 200);

        OpenAIClient.GenerationResult result = openAIClient.planExam(List.of(exam(2024, "scan")), API_KEY);

        assertFalse(result.success());
        assertEquals("Error: rate limited", result.error());
    }

    @Test
    void planExam_returnsFailureForEmptyChoices() throws Exception {
        respondToCompletion("{\"choices\": []}", 200);

        OpenAIClient.GenerationResult result = openAIClient.planExam(List.of(exam(2024, "scan")), API_KEY);

        assertFalse(result.success());
        assertEquals("Model returned no choices", result.error());
    }

    @Test
    void planExam_returnsFailureForLengthFinishReason() throws Exception {
        respondToCompletion(completion("partial", "length"), 200);

        OpenAIClient.GenerationResult result = openAIClient.planExam(List.of(exam(2024, "scan")), API_KEY);

        assertFalse(result.success());
        assertEquals("Max tokens exceeded during generation", result.error());
    }

    @Test
    void planExam_returnsFailureForUnexpectedFinishReason() throws Exception {
        respondToCompletion(completion("filtered", "content_filter"), 200);

        OpenAIClient.GenerationResult result = openAIClient.planExam(List.of(exam(2024, "scan")), API_KEY);

        assertFalse(result.success());
        assertEquals("Unexpected stop condition: content_filter", result.error());
    }

    @Test
    void planExam_returnsFailureForBlankContent() throws Exception {
        respondToCompletion(completion("   ", "stop"), 200);

        OpenAIClient.GenerationResult result = openAIClient.planExam(List.of(exam(2024, "scan")), API_KEY);

        assertFalse(result.success());
        assertEquals("No content generated by model", result.error());
    }

    @Test
    void scanExamWithAI_sendsImagesAndReturnsMarkdown() throws Exception {
        respondToCompletion(successfulCompletion("# Scanned exam", 0, 0));

        String markdown = openAIClient.scanExamWithAI(List.of(new byte[]{1, 2, 3}), API_KEY);

        assertEquals("# Scanned exam", markdown);
        JsonNode payload = recordedChatCompletionPayload();
        assertEquals("low", payload.path("reasoning_effort").asString());
        assertEquals("SCAN", payload.path("messages").path(0).path("content").asString());
        assertEquals("These are all pages you should scan", payload.path("messages").path(1).path("content").path(0).path("text").asString());
        assertEquals("data:image/jpeg;base64,AQID", payload.path("messages").path(1).path("content").path(1).path("image_url").path("url").asString());
    }

    @Test
    void scanExamWithAI_throwsForHttpError() {
        respondToCompletion("{\"message\":\"bad request\"}", 400);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> openAIClient.scanExamWithAI(List.of(new byte[]{1}), API_KEY));

        assertTrue(exception.getCause() instanceof java.io.IOException);
    }

    @Test
    void scanExamWithAI_throwsForUnexpectedFinishReason() {
        respondToCompletion(completion("partial", "length"), 200);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> openAIClient.scanExamWithAI(List.of(new byte[]{1}), API_KEY));

        assertEquals("Invalid Finish Reason: length", exception.getCause().getMessage());
    }

    @Test
    void scanExamWithAI_throwsForMissingContent() {
        respondToCompletion("{\"choices\": [{\"finish_reason\": \"stop\", \"message\": {}}]}", 200);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> openAIClient.scanExamWithAI(List.of(new byte[]{1}), API_KEY));

        assertEquals("No markdown found for exam", exception.getMessage());
    }

    private void respondToCompletion(String body, int statusCode) {
        mockClient.when(request().withMethod("POST").withPath("/v1/chat/completions"))
                .respond(response().withStatusCode(statusCode).withHeader("Content-Type", "application/json").withBody(body));
    }

    private void respondToCompletion(String body) {
        respondToCompletion(body, 200);
    }

    private JsonNode recordedChatCompletionPayload() throws Exception {
        var requests = mockClient.retrieveRecordedRequests(request().withMethod("POST").withPath("/v1/chat/completions"));
        assertEquals(1, requests.length);
        assertEquals("Bearer " + API_KEY, requests[0].getFirstHeader("Authorization"));
        assertTrue(requests[0].getFirstHeader("Content-Type").startsWith("application/json"));
        return MAPPER.readTree(requests[0].getBodyAsString());
    }

    private static String successfulCompletion(String content, int promptTokens, int completionTokens) {
        return """
                {
                  "choices": [{"finish_reason": "stop", "message": {"content": "%s"}}],
                  "usage": {"prompt_tokens": %d, "completion_tokens": %d}
                }
                """.formatted(content, promptTokens, completionTokens);
    }

    private static String completion(String content, String finishReason) {
        return """
                {"choices": [{"finish_reason": "%s", "message": {"content": "%s"}}]}
                """.formatted(finishReason, content);
    }

    private static Exam exam(int year, String scan) {
        return new Exam("Exam", "exam-id", "module-id", year, Semester.SUMMER, Instant.EPOCH,
                "file-id", "user-id", ExamStatus.ACCEPTED, "professor-id", scan);
    }

}

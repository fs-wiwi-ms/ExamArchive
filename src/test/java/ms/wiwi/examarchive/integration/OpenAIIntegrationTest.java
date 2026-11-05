package ms.wiwi.examarchive.integration;

import ms.wiwi.examarchive.ai.OpenAIClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

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

    @BeforeAll
    static void setUpOpenAIClient() {
        openAIClient = new OpenAIClient("http://" + mockServerContainer.getHost() + ":" + mockServerContainer.getServerPort(),
                SCAN_PROMT,
                PLAN_PROMT,
                GEN_PROMT,
                10,
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

}

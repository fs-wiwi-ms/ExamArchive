package ms.wiwi.examarchive.integration;

import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.ai.ExamAIJob;
import ms.wiwi.examarchive.ai.ExamAIStatus;
import ms.wiwi.examarchive.ai.OpenAIClient;
import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.ExamStatus;
import ms.wiwi.examarchive.model.Module;
import ms.wiwi.examarchive.model.Professor;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.Semester;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.services.AIService;
import ms.wiwi.examarchive.services.ApiKeyEncryptionService;
import ms.wiwi.examarchive.services.DatabaseService;
import ms.wiwi.examarchive.services.PdfService;
import ms.wiwi.examarchive.services.QuartoSandboxService;
import ms.wiwi.examarchive.services.S3Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(MockitoExtension.class)
public class AIServiceFullTripIntegrationTest {

    private static final String VALID_KEY = "VALID";
    private static final String MODULE_NAME = "AI Test Module";
    private static final byte[] PDF_BYTES = "dummy pdf content".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IMAGE_BYTES = new byte[]{1, 2, 3};

    private static final int PLAN_INPUT_TOKENS = 10;
    private static final int PLAN_OUTPUT_TOKENS = 5;
    private static final int TASK_INPUT_TOKENS = 3;
    private static final int TASK_OUTPUT_TOKENS = 2;
    private static final int TASK_COUNT = 2;
    private static final int EXPECTED_INPUT_TOKENS = PLAN_INPUT_TOKENS + TASK_COUNT * TASK_INPUT_TOKENS;
    private static final int EXPECTED_OUTPUT_TOKENS = PLAN_OUTPUT_TOKENS + TASK_COUNT * TASK_OUTPUT_TOKENS;

    private static final int SCAN_DPI = 150;
    private static final int SCAN_JPEG_QUALITY = 50;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.3");

    @Mock
    OpenAIClient openAIClient;
    @Mock
    S3Service s3Service;
    @Mock
    PdfService pdfService;
    @Mock
    QuartoSandboxService quartoSandboxService;
    @Mock
    ApiKeyEncryptionService apiKeyEncryptionService;

    static DatabaseService databaseService;
    static Repository repository;
    AIService aiService;

    @BeforeAll
    static void setUpDatabase() {
        databaseService = new DatabaseService(postgres.getHost(), postgres.getUsername(),
                postgres.getPassword(), postgres.getDatabaseName(), postgres.getMappedPort(5432));
        databaseService.migrateDatabase();
        repository = new Repository(databaseService);
    }

    @AfterAll
    static void tearDownDatabase() {
        if (databaseService != null) {
            databaseService.close();
        }
    }

    @BeforeEach
    void setUp() {
        aiService = new AIService(repository, s3Service, quartoSandboxService, pdfService, openAIClient,
                apiKeyEncryptionService);
    }

    @Test
    void generateExam_happyPath_withScan_persistsAndUploads() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        Exam exam = seedExam(module, user, professor, 2024, null);

        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(s3Service.downloadFile(anyString(), eq(S3Service.Bucket.EXAMS))).thenReturn(PDF_BYTES);
        when(pdfService.convertPdfToImages(any(byte[].class), anyInt(), anyInt())).thenReturn(List.of(IMAGE_BYTES));
        when(openAIClient.scanExamWithAI(anyList(), eq(VALID_KEY))).thenReturn("Scanned exam content");
        when(openAIClient.planExam(anyList(), eq(VALID_KEY))).thenReturn(success(VALID_PLAN, PLAN_INPUT_TOKENS, PLAN_OUTPUT_TOKENS));
        when(openAIClient.generateTask(anyString(), anyInt(), eq(VALID_KEY))).thenReturn(success("Task latex", TASK_INPUT_TOKENS, TASK_OUTPUT_TOKENS));
        when(quartoSandboxService.renderQmd(anyString())).thenReturn(PDF_BYTES);

        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);

        assertEquals(ExamAIStatus.DONE, result.job().status(),
                "Expected DONE but was " + result.job().status() + " (" + result.job().errorMessage() + ")");
        assertNull(result.job().errorMessage());
        assertEquals(List.of(
                        ExamAIStatus.FETCH_EXAMS,
                        ExamAIStatus.SCAN,
                        ExamAIStatus.PLANNING,
                        ExamAIStatus.GENERATING_TASKS,
                        ExamAIStatus.COMPILING,
                        ExamAIStatus.UPLOADING,
                        ExamAIStatus.DONE),
                result.statuses());

        verify(s3Service).downloadFile(anyString(), eq(S3Service.Bucket.EXAMS));
        verify(pdfService).convertPdfToImages(any(byte[].class), eq(SCAN_DPI), eq(SCAN_JPEG_QUALITY));
        verify(openAIClient).scanExamWithAI(anyList(), eq(VALID_KEY));
        verify(s3Service).uploadPDF(any(File.class), eq(result.job().id()), eq(S3Service.Bucket.USER_EXAMS));

        Exam updatedExam = repository.getExam(exam.examID());
        assertNotNull(updatedExam);
        assertEquals("Scanned exam content", updatedExam.scan());

        UserExamRow row = getUserExamRow(result.job().id());
        assertNotNull(row);
        assertEquals(user.id(), row.userId());
        assertEquals(EXPECTED_INPUT_TOKENS, row.inputTokens());
        assertEquals(EXPECTED_OUTPUT_TOKENS, row.outputTokens());
        assertTrue(row.fileId().startsWith(module.name() + "-"));
    }

    @Test
    void generateExam_happyPath_noScan_skipsScanAndSucceeds() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        seedExam(module, user, professor, 2024, "already scanned");

        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(openAIClient.planExam(anyList(), eq(VALID_KEY)))
                .thenReturn(success(VALID_PLAN, PLAN_INPUT_TOKENS, PLAN_OUTPUT_TOKENS));
        when(openAIClient.generateTask(anyString(), anyInt(), eq(VALID_KEY)))
                .thenReturn(success("Task latex", TASK_INPUT_TOKENS, TASK_OUTPUT_TOKENS));
        when(quartoSandboxService.renderQmd(anyString())).thenReturn(PDF_BYTES);

        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);

        assertEquals(ExamAIStatus.DONE, result.job().status(),
                "Expected DONE but was " + result.job().status() + " (" + result.job().errorMessage() + ")");
        assertNull(result.job().errorMessage());
        assertEquals(List.of(
                        ExamAIStatus.FETCH_EXAMS,
                        ExamAIStatus.PLANNING,
                        ExamAIStatus.GENERATING_TASKS,
                        ExamAIStatus.COMPILING,
                        ExamAIStatus.UPLOADING,
                        ExamAIStatus.DONE),
                result.statuses());

        // The SCAN branch must not run when every reference exam is already scanned.
        verify(s3Service, never()).downloadFile(anyString(), eq(S3Service.Bucket.EXAMS));
        verify(openAIClient, never()).scanExamWithAI(anyList(), eq(VALID_KEY));
        verify(s3Service).uploadPDF(any(File.class), eq(result.job().id()), eq(S3Service.Bucket.USER_EXAMS));

        UserExamRow row = getUserExamRow(result.job().id());
        assertNotNull(row);
        assertEquals(user.id(), row.userId());
        assertEquals(EXPECTED_INPUT_TOKENS, row.inputTokens());
        assertEquals(EXPECTED_OUTPUT_TOKENS, row.outputTokens());
        assertTrue(row.fileId().startsWith(module.name() + "-"));
    }

    @Test
    void generateExam_invalidApiKey_fails() throws Exception {
        User user = seedUser();
        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(false);

        JobResult result = runGeneration(user, seedModule().moduleID(), List.of(), 2024);

        assertFailed(result.job(), "Invalid API key");
    }

    @Test
    void generateExam_userAlreadyHasJob_fails() throws Exception {
        User user = seedUser();
        stubValidKey();
        CountDownLatch reachedTestApiKey = new CountDownLatch(1);
        CountDownLatch releaseTestApiKey = new CountDownLatch(1);
        when(openAIClient.testAPIKey(VALID_KEY)).thenAnswer(invocation -> {
            reachedTestApiKey.countDown();
            releaseTestApiKey.await();
            return true;
        });
        aiService.generateExam(2024, List.of(), seedModule().moduleID(), user, job -> {
        });
        assertTrue(reachedTestApiKey.await(10, TimeUnit.SECONDS), "First job did not reach testAPIKey");

        try {
            JobResult second = runGeneration(user, seedModule().moduleID(), List.of(), 2024);
            assertFailed(second.job(), "You already have an exam generation in progress");
        } finally {
            releaseTestApiKey.countDown();
        }
    }

    @Test
    void generateExam_noExamsFound_fails() throws Exception {
        User user = seedUser();
        Module module = seedModule();
        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);

        JobResult result = runGeneration(user, module.moduleID(), List.of(), 2024);

        assertFailed(result.job(), "No exams found with applied filters");
    }

    @Test
    void generateExam_scanFails_fails() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        seedExam(module, user, professor, 2024, null);
        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(s3Service.downloadFile(anyString(), eq(S3Service.Bucket.EXAMS)))
                .thenThrow(new RuntimeException("S3 unavailable"));
        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);
        assertFailed(result.job(), "Could not scan exams");
    }

    @Test
    void generateExam_openAINotReachable_fails() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        seedExam(module, user, professor, 2024, "already scanned");
        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(openAIClient.planExam(anyList(), eq(VALID_KEY)))
                .thenReturn(OpenAIClient.GenerationResult.fail("HTTP error 500: upstream down"));
        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);
        assertFailed(result.job(), "HTTP error 500: upstream down");
    }

    @Test
    void generateExam_planDoesNotFollowStructure_fails() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        seedExam(module, user, professor, 2024, "already scanned");

        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(openAIClient.planExam(anyList(), eq(VALID_KEY))).thenReturn(success("not a valid plan", 1, 1));

        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);

        assertFailed(result.job(), "The exam plan did not follow the required structure");
    }

    @Test
    void generateExam_taskGenerationFails_fails() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        seedExam(module, user, professor, 2024, "already scanned");

        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(openAIClient.planExam(anyList(), eq(VALID_KEY))).thenReturn(success(VALID_PLAN, PLAN_INPUT_TOKENS, PLAN_OUTPUT_TOKENS));
        when(openAIClient.generateTask(anyString(), anyInt(), eq(VALID_KEY)))
                .thenReturn(OpenAIClient.GenerationResult.fail("model refused"));

        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);

        assertFailed(result.job(), "model refused");
    }

    @Test
    void generateExam_taskGenerationThrows_fails() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        seedExam(module, user, professor, 2024, "already scanned");

        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(openAIClient.planExam(anyList(), eq(VALID_KEY)))
                .thenReturn(success(VALID_PLAN, PLAN_INPUT_TOKENS, PLAN_OUTPUT_TOKENS));
        when(openAIClient.generateTask(anyString(), anyInt(), eq(VALID_KEY)))
                .thenThrow(new RuntimeException("task worker crashed"));

        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);

        assertFailed(result.job(), "Task generation exception: task worker crashed");
    }

    @Test
    void generateExam_compilationFails_fails() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        seedExam(module, user, professor, 2024, "already scanned");

        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(openAIClient.planExam(anyList(), eq(VALID_KEY))).thenReturn(success(VALID_PLAN, PLAN_INPUT_TOKENS, PLAN_OUTPUT_TOKENS));
        when(openAIClient.generateTask(anyString(), anyInt(), eq(VALID_KEY))).thenReturn(success("Task latex", TASK_INPUT_TOKENS, TASK_OUTPUT_TOKENS));
        when(quartoSandboxService.renderQmd(anyString())).thenThrow(new RuntimeException("quarto crashed"));

        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);

        assertFailed(result.job(), "Could not compile exam: quarto crashed");
    }

    @Test
    void generateExam_uploadFails_fails() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        seedExam(module, user, professor, 2024, "already scanned");

        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(openAIClient.planExam(anyList(), eq(VALID_KEY))).thenReturn(success(VALID_PLAN, PLAN_INPUT_TOKENS, PLAN_OUTPUT_TOKENS));
        when(openAIClient.generateTask(anyString(), anyInt(), eq(VALID_KEY))).thenReturn(success("Task latex", TASK_INPUT_TOKENS, TASK_OUTPUT_TOKENS));
        when(quartoSandboxService.renderQmd(anyString())).thenReturn(PDF_BYTES);
        doThrow(new RuntimeException("S3 upload failed"))
                .when(s3Service).uploadPDF(any(File.class), anyString(), eq(S3Service.Bucket.USER_EXAMS));

        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);

        assertFailed(result.job(), "S3 upload failed");
    }

    @Test
    void generateExam_unexpectedException_fails() throws Exception {
        Module module = seedModule();
        User user = seedUser();
        Professor professor = seedProfessor();
        seedExam(module, user, professor, 2024, "already scanned");

        stubValidKey();
        when(openAIClient.testAPIKey(VALID_KEY)).thenReturn(true);
        when(openAIClient.planExam(anyList(), eq(VALID_KEY)))
                .thenThrow(new RuntimeException("unexpected boom"));

        JobResult result = runGeneration(user, module.moduleID(), List.of(professor), 2024);

        assertFailed(result.job(), "unexpected boom");
    }

    private static final String VALID_PLAN = """
            ===EXAM START===
            Generated Exam Header
            ===EXAM END===
            ===TASK START===
            Task one plan
            ===TASK END===
            ===TASK START===
            Task two plan
            ===TASK END===
            """;

    private record JobResult(ExamAIJob job, List<ExamAIStatus> statuses) {
    }

    private JobResult runGeneration(User user, String moduleId, List<Professor> professors, int untilYear)
            throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        List<ExamAIStatus> statuses = Collections.synchronizedList(new ArrayList<>());
        Consumer<ExamAIJob> onUpdate = job -> {
            statuses.add(job.status());
            if (job.status() == ExamAIStatus.DONE || job.status() == ExamAIStatus.FAILED) {
                done.countDown();
            }
        };
        String jobId = aiService.generateExam(untilYear, professors, moduleId, user, onUpdate);
        assertTrue(done.await(30, TimeUnit.SECONDS), "Job " + jobId + " did not complete in time");
        return new JobResult(aiService.getJob(jobId), statuses);
    }

    private void stubValidKey() throws Exception {
        when(apiKeyEncryptionService.decrypt(anyString())).thenReturn(VALID_KEY);
    }

    private static OpenAIClient.GenerationResult success(String latex, int input, int output) {
        return new OpenAIClient.GenerationResult(true, latex, input, output, null);
    }

    private static void assertFailed(ExamAIJob job, String message) {
        assertEquals(ExamAIStatus.FAILED, job.status());
        assertTrue(job.errorMessage().contains(message),
                "Expected error to contain '" + message + "' but was: " + job.errorMessage());
    }

    private static Module seedModule() {
        String id = UUID.randomUUID().toString();
        repository.addModule(MODULE_NAME, id);
        return new Module(MODULE_NAME, id);
    }

    private static User seedUser() {
        String id = UUID.randomUUID().toString();
        User user = new User(id, "First", "Last", Instant.now(), Instant.now(), id + "@example.com", Role.USER);
        repository.addOrUpdateUser(user);
        repository.setApiKeyForUser(user, "encrypted-key");
        return user;
    }

    private static Professor seedProfessor() {
        return repository.getOrCreateProfessor("Prof", "One");
    }

    private static Exam seedExam(Module module, User uploader, Professor professor, int year, String scan) {
        Exam exam = new Exam("Exam " + year, UUID.randomUUID().toString(), module.moduleID(), year,
                Semester.SUMMER, Instant.now(), UUID.randomUUID().toString(), uploader.id(),
                ExamStatus.ACCEPTED, professor.professorID(), scan);
        repository.addExam(exam);
        return exam;
    }

    private static UserExamRow getUserExamRow(String id) throws SQLException {
        String sql = "SELECT user_id, file_id, input_tokens, output_tokens FROM user_exams WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new UserExamRow(resultSet.getString("user_id"), resultSet.getString("file_id"),
                            resultSet.getInt("input_tokens"), resultSet.getInt("output_tokens"));
                }
            }
        }
        return null;
    }

    private record UserExamRow(String userId, String fileId, int inputTokens, int outputTokens) {
    }
}

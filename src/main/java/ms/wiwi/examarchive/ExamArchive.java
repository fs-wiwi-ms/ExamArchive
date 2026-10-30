package ms.wiwi.examarchive;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.rendering.template.JavalinJte;
import ms.wiwi.examarchive.admin.*;
import ms.wiwi.examarchive.ai.OpenAIClient;
import ms.wiwi.examarchive.auth.AuthController;
import ms.wiwi.examarchive.auth.OIDCService;
import ms.wiwi.examarchive.controller.*;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.model.UserExam;
import ms.wiwi.examarchive.services.*;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.HttpCookie;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExamArchive {

    static void main() {
        new ExamArchive().start();
    }

    private final DatabaseService dbManager;
    private final Repository repository;
    private final OIDCService oidcService;
    private final S3Service s3Service;
    private final MotdService motdService;
    private final EmailService emailService;
    private final AIService aiService;
    private final OpenAIClient openAiClient;
    private final QuartoSandboxService quartoSandboxService;
    private final ApiKeyEncryptionService apiKeyEncryptionService;
    private final PdfService pdfService = new PdfService();
    private final AppConfig appConfig;
    private final Logger logger = LoggerFactory.getLogger(ExamArchive.class);

    public ExamArchive(){
        this.appConfig = AppConfig.fromEnv();
        logger.info("Starting ExamArchive");
        if(appConfig.developmentMode()){
            logger.info("Running in development mode");
        }

        logger.info("Initializing OIDC service");
        oidcService = createOIDCService();

        logger.info("Initializing database connection");
        this.dbManager = createDatabaseManager();
        repository = new Repository(dbManager);
        Runtime.getRuntime().addShutdownHook(new Thread(dbManager::close));

        logger.info("Connecting to S3");
        s3Service = createS3Service();
        logger.info("S3 connection established");

        motdService = new MotdService(repository);

        emailService = createEmailService();
        logger.info("Email service initialized");

        logger.info("Initializing Quarto sandbox service");
        quartoSandboxService = createQuartoSandboxService();
        logger.info("Quarto sandbox service initialized");

        logger.info("Loading API key encryption service");
        apiKeyEncryptionService = new ApiKeyEncryptionService(appConfig.encryptionKey());
        logger.info("Initializing AI service");
        openAiClient = createOpenAiClient();
        aiService = createAiService();
        logger.info("AI service initialized. Ready to start!");
    }

    private OpenAIClient createOpenAiClient() {
        String scanPrompt;
        String genPrompt;
        try (InputStream scanPromptStream = AIService.class.getResourceAsStream("/prompts/scan.md");
             InputStream genPromptStream = AIService.class.getResourceAsStream("/prompts/gen.md")) {
            scanPrompt = IOUtils.toString(Objects.requireNonNull(scanPromptStream), StandardCharsets.UTF_8);
            genPrompt = IOUtils.toString(Objects.requireNonNull(genPromptStream), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to load AI prompts", e);
            throw new RuntimeException(e);
        }
        return new OpenAIClient(
                appConfig.aiEndpoint(),
                scanPrompt,
                genPrompt,
                10,
                appConfig.aiModel()
        );
    }

    private AIService createAiService() {
        return new AIService(
                repository,
                s3Service,
                quartoSandboxService,
                pdfService,
                openAiClient,
                apiKeyEncryptionService
        );
    }

    private @NonNull QuartoSandboxService createQuartoSandboxService() {
        return new QuartoSandboxService(
                appConfig.dockerSock(),
                appConfig.dockerRuntime()
        );
    }

    /**
     * Creates an email service instance and tests its connection.
     * @return emailService
     */
    private @NonNull EmailService createEmailService() {
        final EmailService emailService;
        emailService = new EmailService(
                appConfig.smtpUsername(),
                appConfig.smtpPassword(),
                appConfig.smtpHost(),
                appConfig.smtpPort(),
                appConfig.smtpFrom()
        );
        if(!emailService.testConnection()){
            logger.error("Could not connect to SMTP server. Proceed starting without email service.");
        }
        return emailService;
    }

    /**
     * Creates an S3 service instance.
     * @return s3service
     */
    private @NonNull S3Service createS3Service() {
        final S3Service s3Service;
        S3Service.Bucket.EXAMS.setName(appConfig.s3ExamBucket());
        S3Service.Bucket.USER_EXAMS.setName(appConfig.s3UserBucket());
        s3Service = new S3Service(
                appConfig.s3Endpoint(),
                appConfig.s3AccessKey(),
                appConfig.s3SecretKey());
        if(!s3Service.testConnection()){
            throw new RuntimeException("Could not connect to S3");
        }
        s3Service.createBucketIfNotExists(S3Service.Bucket.EXAMS);
        s3Service.createBucketIfNotExists(S3Service.Bucket.USER_EXAMS);
        return s3Service;
    }

    /**
     * Creates an OIDC service instance.
     * @return oidcService
     */
    private OIDCService createOIDCService(){
        try {
            return new OIDCService(
                    appConfig.keycloakIssuer(),
                    appConfig.keycloakClientId(),
                    appConfig.keycloakClientSecret(),
                    appConfig.keycloakRedirectUri()
            );
        } catch (Exception e) {
            logger.error("Failed to initialize OIDC service", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an active instance of the database manager and checks if the connection is valid.
     * @return Database manager
     */
    private DatabaseService createDatabaseManager(){
        String hostname = appConfig.dbHost();
        String username = appConfig.dbUser();
        String password = appConfig.dbPassword();
        String database = appConfig.dbName();
        int port = appConfig.dbPort();
        DatabaseService dbManager = new DatabaseService(hostname, username, password, database, port);
        if(dbManager.getConnection() == null){
            throw new RuntimeException("Could not establish database connection");
        }
        try (Connection connection = dbManager.getConnection()) {
            if(connection.isValid(30)){
                return dbManager;
            }
        } catch (SQLException _) {}
        return dbManager;
    }

    /**
     * Starts the webserver and loads all of its dependencies
     */
    private void start() {
        dbManager.migrateDatabase();
        logger.info("Starting webserver");
        Javalin javalin = Javalin.create(this::configureJavalin);
        scheduleUserDeletion();
        javalin.start(1910);
    }

    /**
     * Deletes users from the database that have not logged in for two years.
     */
    private void scheduleUserDeletion() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            logger.info("Deleting users that have not logged in for two years");
            List<User> users = repository.getAccountsToDelete();
            if(users.isEmpty())
                return;
            List<UserExam> userExams = users.stream().map(repository::getAllUserExams).flatMap(List::stream).toList();
            userExams.forEach(userExam -> {
                try {
                    logger.info("Deleting user exam with id {} ({})", userExam.id(), userExam.fileID());
                    s3Service.deleteFile(userExam.id(), S3Service.Bucket.USER_EXAMS);
                    repository.deleteUserExam(userExam.id());
                } catch (Exception e) {
                    logger.error("Failed to delete user exam with id {} ({})", userExam.id(), userExam.fileID(), e);
                }
            });
            users.forEach(user -> logger.info("Deleting user with id {} ({})", user.id(), user.email()));
            repository.deleteOldAccounts(users.stream().map(User::id).toList());
        }, 0, 12, TimeUnit.HOURS);
    }

    /**
     * COnfigures all routes and settings for the Javalin web server.
     * @param config javalin config
     */
    private void configureJavalin(JavalinConfig config) {
        config.fileRenderer(new JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)));
        config.routes.get("/", ctx -> {
            if (ctx.sessionAttribute("user") != null) {
                ctx.redirect("/exams/search");
                return;
            }
            ctx.render("index.jte");
        });
        config.staticFiles.add("/public");
        config.staticFiles.enableWebjars();
        config.concurrency.useVirtualThreads = true;
        AuthController authController = new AuthController(oidcService, repository, appConfig.keycloakUserAffiliation(), appConfig.keycloakAdminAffiliation(), appConfig.adminEmail());
        config.routes.get("/login/{type}", authController::login);
        config.routes.get("/auth/callback", authController::callback);
        config.routes.get("/logout", authController::logout);
        SearchExamController searchExamController = new SearchExamController(repository);
        config.routes.get("/exams/search", searchExamController);
        config.routes.post("/exams/search", searchExamController);
        ShowModuleController showModuleHandler = new ShowModuleController(repository);
        config.routes.get("/exams/module/{moduleid}", showModuleHandler::handleGet);
        config.routes.post("/exams/module/{moduleid}/filter", showModuleHandler::handleFilter);
        ExamAIController examAIController = new ExamAIController(repository, aiService, openAiClient, apiKeyEncryptionService);
        config.routes.get("/exams/module/{moduleid}/examai", examAIController::handleGet);
        config.routes.post("/exams/module/{moduleid}/examai", examAIController::handlePost);
        config.routes.sse("/exams/ai/job/{jobid}", examAIController::handleSse);
        AddExamController addExamController = new AddExamController(repository, s3Service, emailService, pdfService);
        config.routes.get("/exams/upload", addExamController::handleGet);
        config.routes.post("/exams/upload", addExamController::handlePost);
        config.routes.get("/exams/upload/form-content", addExamController::handleFormContent);
        config.routes.post("/exams/upload/professors/search", addExamController::handleSearchProfessors);
        config.routes.get("/exams/upload/professors/select", addExamController::handleSelectProfessor);
        config.routes.get("/exams/upload/professors/clear", addExamController::handleClearProfessor);
        config.routes.get("/exams/view/{examid}", new ExamViewController(repository, s3Service));
        config.routes.get("/admin/admin", new AdminIndexController());
        AdminExamsController adminExamsController = new AdminExamsController(repository, s3Service, emailService);
        config.routes.get("/admin/exams", adminExamsController::handleGet);
        config.routes.post("/admin/exams/deletemodule", adminExamsController::handleRemoveModule);
        config.routes.post("/admin/exams/addmodule", adminExamsController::handleAddModule);
        config.routes.post("/admin/exams/acceptexam", adminExamsController::acceptExam);
        config.routes.post("/admin/exams/declineexam", adminExamsController::deleteExam);
        config.routes.post("/admin/exams/editexam/{examid}", adminExamsController::handleEditExam);
        config.routes.post("/admin/exams/deleteexam/{examid}", adminExamsController::handleDeleteExam);
        config.routes.get("/admin/exams/edit/{examid}", adminExamsController::handleEditGet);
        config.routes.post("/admin/exams/deletekeyword/{moduleid}/{keyword}", adminExamsController::handleDeleteKeyword);
        config.routes.post("/admin/exams/addkeyword/{moduleid}", adminExamsController::handleAddKeyword);
        config.routes.post("/admin/exams/deletedegree/{degreeid}", adminExamsController::handleDeleteDegree);
        config.routes.post("/admin/exams/updatemoduledegree", adminExamsController::handleModuleDegreeUpdate);
        config.routes.post("/admin/exams/adddegree", adminExamsController::handleAddDegree);
        AdminUsersController adminUsersController = new AdminUsersController(repository);
        config.routes.get("/admin/users", adminUsersController::handleGet);
        config.routes.post("/admin/users/search", adminUsersController::handleSearch);
        config.routes.post("/admin/users/block", adminUsersController::handleBlock);
        config.routes.post("/admin/users/unblock", adminUsersController::handleUnblock);
        AdminSettingsController adminSettingsController = new AdminSettingsController(motdService, repository);
        config.routes.get("/admin/settings", adminSettingsController::handleGet);
        config.routes.post("/admin/updatemotd", adminSettingsController::handleUpdateMotdPost);
        config.routes.get("/admin/ai", new AdminAIController(repository));
        config.routes.get("/dropdown", new HeaderController());
        config.routes.get("/exams/usercontent/download/{userexamid}", new UserExamDownloadController(repository, s3Service));
        config.routes.get("/exams/usercontent/list", new UserExamListController(repository));
        config.routes.before("/exams/*", ctx -> {
            if (ctx.sessionAttribute("user") == null) {
                ctx.skipRemainingHandlers();
                if (ctx.header("HX-Request") != null) {
                    ctx.header("HX-Redirect", "/login/user");
                    ctx.status(401);
                    return;
                }
                ctx.redirect("/login/user");
            }
        });
        config.routes.before("/admin/*", ctx -> {
            if (ctx.sessionAttribute("user") == null || ((User) ctx.sessionAttribute("user")).role() != Role.ADMIN) {
                ctx.skipRemainingHandlers();
                if (ctx.header("HX-Request") != null) {
                    ctx.header("HX-Redirect", "/login/admin");
                    ctx.status(401);
                    return;
                }
                ctx.redirect("/login/admin");
            }
        });
        config.routes.before(ctx -> {
            String acceptLanguage = ctx.header("Accept-Language");
            Locale locale = Locale.GERMAN;
            if (acceptLanguage != null && acceptLanguage.toLowerCase().startsWith("en")) {
                locale = Locale.ENGLISH;
            }
            if (ctx.cookie("lang") != null) {
                if (ctx.cookie("lang").equals("german")) {
                    locale = Locale.GERMAN;
                }
                if (ctx.cookie("lang").equals("english")) {
                    locale = Locale.ENGLISH;
                }
            }
            JteLocalizer.setLocale(locale);
        });
        if (!appConfig.developmentMode()) {
            config.jetty.modifyServletContextHandler(handler -> {
                handler.getSessionHandler().getSessionCookieConfig().setHttpOnly(true);
                handler.getSessionHandler().getSessionCookieConfig().setSecure(true);
                handler.getSessionHandler().setSameSite(HttpCookie.SameSite.LAX);
            });
        }
        config.routes.after("/*", ctx -> {
            if (ctx.statusCode() < 400 || ctx.header("HX-Request") != null) {
                return;
            }
            String message = ctx.body();
            if (!message.isBlank()) {
                ctx.render("error.jte", Map.of("status", ctx.statusCode()));
                return;
            }
            message = JteLocalizer.lookup("error.notfound");
            ctx.render("error.jte", Map.of("status", ctx.statusCode(), "message", message));
        });
        config.routes.after(_ -> JteLocalizer.clear());
    }
}

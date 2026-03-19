package ms.wiwi.examarchive;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinJte;
import ms.wiwi.examarchive.admin.AdminExamsController;
import ms.wiwi.examarchive.admin.AdminIndexController;
import ms.wiwi.examarchive.admin.AdminSettingsController;
import ms.wiwi.examarchive.admin.AdminUsersController;
import ms.wiwi.examarchive.auth.AuthController;
import ms.wiwi.examarchive.auth.OIDCService;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.User;
import org.eclipse.jetty.http.HttpCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

public class ExamArchive {

    static void main() {
        new ExamArchive().start();
    }

    private final boolean developmentMode;
    private final DBManager dbManager;
    private final Repository repository;
    private final OIDCService oidcService;
    private final S3Service s3Service;
    private final Logger logger = LoggerFactory.getLogger(ExamArchive.class);

    public ExamArchive(){
        logger.info("Starting ExamArchive");
        if(System.getenv("EXAMARCHIVE_DEV_MODE") != null && System.getenv("EXAMARCHIVE_DEV_MODE").equals("true")){
            developmentMode = true;
            logger.info("Running in development mode");
        } else {
            developmentMode = false;
        }
        logger.info("Initializing OIDC service");
        try {
            this.oidcService = new OIDCService(
                    System.getenv("KEYCLOAK_ISSUER"),
                    System.getenv("KEYCLOAK_CLIENT_ID"),
                    System.getenv("KEYCLOAK_CLIENT_SECRET"),
                    System.getenv("KEYCLOAK_REDIRECT_URI")
            );
        } catch (Exception e) {
            logger.error("Failed to initialize OIDC service", e);
            throw new RuntimeException(e);
        }
        logger.info("Initializing database connection");
        this.dbManager = createDatabaseManager();
        repository = new Repository(dbManager);
        Runtime.getRuntime().addShutdownHook(new Thread(dbManager::close));
        logger.info("Connecting to S3");
        s3Service = new S3Service(
                System.getenv("EXAMARCHIVE_STORAGE_ENDPOINT"),
                System.getenv("EXAMARCHIVE_STORAGE_ACCESS_KEY"),
                System.getenv("EXAMARCHIVE_STORAGE_SECRET_KEY"),
                System.getenv("EXAMARCHIVE_STORAGE_BUCKET"));
        if(!s3Service.testConnection()){
            throw new RuntimeException("Could not connect to S3");
        }
        s3Service.createBucketIfNotExists();
        logger.info("S3 connection established");
    }

    /**
     * Starts the webserver and loads all of its dependencies
     */
    private void start() {
        dbManager.migrateDatabase();
        logger.info("Starting webserver");
        AuthController authController = new AuthController(oidcService, repository, System.getenv("KEYCLOAK_USER_AFFILIATION"), System.getenv("KEYCLOAK_ADMIN_AFFILIATION"));
        Javalin javalin = Javalin.create(config -> {
            config.fileRenderer(new JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)));
            config.routes.get("/", ctx -> ctx.render("index.jte"));
            config.routes.after(_ -> JteLocalizer.clear());
            config.staticFiles.add("/public");
            config.routes.get("/login/{type}", authController::login);
            config.routes.get("/auth/callback", authController::callback);
            config.routes.get("/logout", authController::logout);
            SearchExamController searchExamController = new SearchExamController(repository);
            config.routes.get("/exams/search", searchExamController);
            config.routes.post("/exams/search", searchExamController);
            ShowModuleHandler showModuleHandler = new ShowModuleHandler(repository);
            config.routes.get("/exams/module/{moduleid}", showModuleHandler::handleGet);
            config.routes.post("/exams/module/{moduleid}/filter", showModuleHandler::handleFilter);
            AddExamController addExamController = new AddExamController(repository, s3Service);
            config.routes.get("/exams/upload", addExamController::handleGet);
            config.routes.post("/exams/upload", addExamController::handlePost);
            config.routes.get("/exams/upload/form-content", addExamController::handleFormContent);
            config.routes.post("/exams/upload/professors/search", addExamController::handleSearchProfessors);
            config.routes.get("/exams/upload/professors/select", addExamController::handleSelectProfessor);
            config.routes.get("/exams/upload/professors/clear", addExamController::handleClearProfessor);
            config.routes.get("/admin/admin", new AdminIndexController());
            AdminExamsController adminExamsController = new AdminExamsController(repository, s3Service);
            config.routes.get("/admin/exams", adminExamsController::handleGet);
            config.routes.post("/admin/exams/deletemodule", adminExamsController::handleRemoveModule);
            config.routes.post("/admin/exams/addmodule", adminExamsController::handleAddModule);
            config.routes.post("/admin/exams/acceptexam", adminExamsController::acceptExam);
            config.routes.post("/admin/exams/declineexam", adminExamsController::deleteExam);
            AdminUsersController adminUsersController = new AdminUsersController(repository);
            config.routes.get("/admin/users", adminUsersController::handleGet);
            config.routes.post("/admin/users/search", adminUsersController::handleSearch);
            config.routes.post("/admin/users/block", adminUsersController::handleBlock);
            config.routes.post("/admin/users/unblock", adminUsersController::handleUnblock);
            config.routes.get("/admin/settings", new AdminSettingsController());
            config.routes.before("/exams/*", ctx -> {
                if(ctx.sessionAttribute("user") == null){
                    ctx.skipRemainingHandlers();
                    if(ctx.header("HX-Request") != null){
                        ctx.header("HX-Redirect", "/login/user");
                        ctx.status(401);
                        return;
                    }
                    ctx.redirect("/login/user");
                }
            });
            config.routes.before("/admin/*", ctx -> {
                if(ctx.sessionAttribute("user") == null || ((User)ctx.sessionAttribute("user")).role() != Role.ADMIN){
                    ctx.skipRemainingHandlers();
                    if(ctx.header("HX-Request") != null){
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
                JteLocalizer.setLocale(locale);
            });
            if(!developmentMode){
                config.jetty.modifyServletContextHandler(handler -> {
                    handler.getSessionHandler().getSessionCookieConfig().setHttpOnly(true);
                    handler.getSessionHandler().getSessionCookieConfig().setSecure(true);
                    handler.getSessionHandler().setSameSite(HttpCookie.SameSite.LAX);
                });
            }
        });
        javalin.start(1910);
    }

    /**
     * Creates an active instance of the database manager and checks if the connection is valid.
     * @return Database manager
     */
    private DBManager createDatabaseManager(){
        String hostname = System.getenv("EXAMARCHIVE_DB_HOSTNAME");
        String username = System.getenv("EXAMARCHIVE_DB_USERNAME");
        String password = System.getenv("EXAMARCHIVE_DB_PASSWORD");
        String database = System.getenv("EXAMARCHIVE_DB_DATABASE");
        int port = Integer.parseInt(System.getenv("EXAMARCHIVE_DB_PORT"));
        DBManager dbManager = new DBManager(hostname, username, password, database, port);
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
}

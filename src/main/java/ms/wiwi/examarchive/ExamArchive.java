package ms.wiwi.examarchive;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinJte;
import ms.wiwi.examarchive.auth.AuthController;
import ms.wiwi.examarchive.auth.OIDCService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

public class ExamArchive {

    static void main() {
        new ExamArchive().start();
    }

    private final DBManager dbManager;
    private final Repository repository;
    private final OIDCService oidcService;
    private final Logger logger = LoggerFactory.getLogger(ExamArchive.class);

    public ExamArchive(){
        logger.info("Starting ExamArchive");
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
    }

    private void start() {
        dbManager.migrateDatabase();
        logger.info("Starting webserver");
        AuthController authController = new AuthController(oidcService, repository);
        Javalin javalin = Javalin.create(config -> {
            config.fileRenderer(new JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)));
            config.routes.get("/", ctx -> ctx.render("index.jte"));
            config.routes.after(_ -> JteLocalizer.clear());
            config.staticFiles.add("/public");
            config.routes.get("/login/{type}", authController::login);
            config.routes.get("/auth/callback", authController::callback);
            config.routes.get("/logout", authController::logout);
            config.routes.get("/exams/search", ctx -> {
                if(ctx.sessionAttribute("userId") == null){
                    ctx.redirect("/login/user");
                    return;
                }
                ctx.result("Hello " + ctx.sessionAttribute("userId") + " from Javalin");
            });
            config.routes.before(ctx -> {
                String acceptLanguage = ctx.header("Accept-Language");
                Locale locale = Locale.GERMAN;
                if (acceptLanguage != null && acceptLanguage.toLowerCase().startsWith("en")) {
                    locale = Locale.ENGLISH;
                }
                JteLocalizer.setLocale(locale);
            });
        });
        javalin.start(1910);
    }

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
        } catch (SQLException e) {}
        return dbManager;
    }
}

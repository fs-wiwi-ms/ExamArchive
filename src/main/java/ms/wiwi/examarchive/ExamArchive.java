package ms.wiwi.examarchive;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinJte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class ExamArchive {

    static void main() {
        new ExamArchive().start();
    }

    private final DBManager dbManager;
    private final Repository examRepository;
    private final Logger logger = LoggerFactory.getLogger(ExamArchive.class);
    public ExamArchive(){
        logger.info("Starting ExamArchive");
        logger.info("Initializing database connection");
        this.dbManager = createDatabaseManager();
        examRepository = new Repository(dbManager);
        Runtime.getRuntime().addShutdownHook(new Thread(dbManager::close));
    }

    private void start() {
        dbManager.migrateDatabase();
        logger.info("Starting webserver");
        Javalin javalin = Javalin.create(config -> {
            config.fileRenderer(new JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)));
            config.routes.get("/", ctx -> ctx.render("helloWorld.jte"));
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

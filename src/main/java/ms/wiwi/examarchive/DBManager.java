package ms.wiwi.examarchive;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DBManager {

    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(DBManager.class);

    /**
     * Initializes a new database connection
     * @param hostname Postgresql hostname
     * @param username Postgresql username
     * @param password Postgresql password
     * @param database Postgresql database
     * @param port Postgresql port
     */
    public DBManager(String hostname, String username, String password, String database, int port) {
        if(hostname == null || username == null || password == null || database == null){
            throw new RuntimeException("Missing database credentials");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + hostname + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        dataSource = new HikariDataSource(config);
    }

    public void migrateDatabase(){
        logger.info("Migrating database");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        logger.info("Database migration complete");
    }

    /**
     * Closes the database connection
     */
    public void close(){
        logger.info("Closing database connection");
        if(dataSource != null && dataSource.isRunning()){
            dataSource.close();
        }
    }

    /**
     * Gets a new database connection. Returns null if no connection could be established.
     * @return Database connection
     */
    public @Nullable Connection getConnection(){
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            logger.error("Could not get connection from database", e);
            return null;
        }
    }
}

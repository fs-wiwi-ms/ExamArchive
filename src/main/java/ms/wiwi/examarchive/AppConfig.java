package ms.wiwi.examarchive;

public record AppConfig(
        boolean developmentMode,
        String dbHost,
        String dbUser,
        String dbPassword,
        String dbName,
        int dbPort,
        String keycloakIssuer,
        String keycloakClientId,
        String keycloakClientSecret,
        String keycloakRedirectUri,
        String keycloakUserAffiliation,
        String keycloakAdminAffiliation,
        String adminEmail,
        String s3Endpoint,
        String s3AccessKey,
        String s3SecretKey,
        String s3ExamBucket,
        String s3UserBucket,
        String smtpUsername,
        String smtpPassword,
        String smtpHost,
        String smtpPort,
        String smtpFrom,
        String dockerSock,
        String dockerRuntime,
        String aiEndpoint,
        String aiModel,
        String encryptionKey) {
    public static AppConfig fromEnv() {
        return new AppConfig(
                "true".equalsIgnoreCase(System.getenv("EXAMARCHIVE_DEV_MODE")),
                getEnv("EXAMARCHIVE_DB_HOSTNAME", "localhost"),
                getEnv("EXAMARCHIVE_DB_USERNAME", ""),
                getEnv("EXAMARCHIVE_DB_PASSWORD", ""),
                getEnv("EXAMARCHIVE_DB_DATABASE", "examarchive"),
                getIntEnv("EXAMARCHIVE_DB_PORT", "5432"),
                getEnv("KEYCLOAK_ISSUER", ""),
                getEnv("KEYCLOAK_CLIENT_ID", ""),
                getEnv("KEYCLOAK_CLIENT_SECRET", ""),
                getEnv("KEYCLOAK_REDIRECT_URI", ""),
                getEnv("KEYCLOAK_USER_AFFILIATION", ""),
                getEnv("KEYCLOAK_ADMIN_AFFILIATION", ""),
                getEnv("EXAMARCHIVE_ADMIN_EMAIL", ""),
                getEnv("EXAMARCHIVE_STORAGE_ENDPOINT", ""),
                getEnv("EXAMARCHIVE_STORAGE_ACCESS_KEY", ""),
                getEnv("EXAMARCHIVE_STORAGE_SECRET_KEY", ""),
                getEnv("EXAMARCHIVE_STORAGE_BUCKET", "exams"),
                getEnv("EXAMARCHIVE_USER_STORAGE_BUCKET", "userexams"),
                getEnv("EXAMARCHIVE_SMTP_USERNAME", ""),
                getEnv("EXAMARCHIVE_SMTP_PASSWORD", ""),
                getEnv("EXAMARCHIVE_SMTP_HOST", ""),
                getEnv("EXAMARCHIVE_SMTP_PORT", ""),
                getEnv("EXAMARCHIVE_SMTP_FROM", ""),
                getEnv("EXAMARCHIVE_DOCKER_SOCK", "/var/run/docker.sock"),
                getEnv("EXAMARCHIVE_DOCKER_RUNTIME", "runc"),
                getEnv("EXAMARCHIVE_AI_ENDPOINT", ""),
                getEnv("EXAMARCHIVE_AI_MODEL", "Qwen3.8-27B"),
                getEnv("EXAMARCHIVE_ENCRYPTION_KEY", ""));
    }

    private static String getEnv(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    private static int getIntEnv(String name, String defaultValue) {
        return Integer.parseInt(getEnv(name, defaultValue));
    }
}

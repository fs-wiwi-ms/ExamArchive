package ms.wiwi.examarchive.controller;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.model.UserExam;
import ms.wiwi.examarchive.services.ApiKeyEncryptionService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class UserExamListController implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(UserExamListController.class.getName());

    private final Repository repository;
    private final ApiKeyEncryptionService apiKeyEncryptionService;

    public UserExamListController(Repository repository, ApiKeyEncryptionService apiKeyEncryptionService) {
        this.repository = repository;
        this.apiKeyEncryptionService = apiKeyEncryptionService;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        User user = ctx.sessionAttribute("user");
        List<UserExam> userExams = repository.getAllUserExams(user);
        ctx.render("viewmyexams.jte", Map.of("user", user, "userexams", userExams, "hasapikey", hasApiKey(user)));
    }

    private boolean hasApiKey(User user) {
        String apiKey = repository.getApiKeyForUser(user);
        if (apiKey == null) {
            return false;
        }
        try {
            return !apiKeyEncryptionService.decrypt(apiKey).isBlank();
        } catch (Exception e) {
            logger.error("Could not decrypt api key for user {}", user, e);
            return false;
        }
    }
}

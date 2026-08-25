package ms.wiwi.examarchive.controller;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import ms.wiwi.examarchive.Repository;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ExamAIController implements Handler {

    private final Repository repository;

    public ExamAIController(Repository repo) {
        this.repository = repo;
    }

    @Override
    public void handle(@NotNull Context context) throws Exception {
        String moduleID = context.pathParam("moduleid");
        context.render("generateExam.jte", Map.of("professors", repository.searchProfessorsForModule(moduleID, null, null)));
    }
}

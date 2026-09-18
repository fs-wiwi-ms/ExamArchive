package ms.wiwi.examarchive.controller;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.model.UserExam;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class UserExamListController implements Handler {

    private final Repository repository;

    public UserExamListController(Repository repository) {
        this.repository = repository;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        User user = ctx.sessionAttribute("user");
        List<UserExam> userExams = repository.getAllUserExams(user);
        ctx.render("viewmyexams.jte", Map.of("user", user, "userexams", userExams));
    }
}

package ms.wiwi.examarchive.admin;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import ms.wiwi.examarchive.Repository;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AdminAIController implements Handler {

    private final Repository repository;

    public AdminAIController(Repository repository) {
        this.repository = repository;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        int weeklyTotalToken = repository.countWeeklyUserExmasToken();
        int weeklyExams = repository.countWeeklyUserExmas();
        int semesterToken = repository.countSemesterUserExamsToken();
        int semesterExams = repository.countSemesterUserExams();
        ctx.render("adminAi.jte", Map.of(
                "weeklyExamCount", weeklyExams,
                "weeklyToken", weeklyTotalToken,
                "semesterToken", semesterToken,
                "semesterExamCount", semesterExams));
    }
}

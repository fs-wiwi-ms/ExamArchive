package ms.wiwi.examarchive.controller;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.ai.ExamAIJob;
import ms.wiwi.examarchive.ai.ExamAIStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ExamAIController {

    private final Repository repository;
    private final TemplateEngine templateEngine;

    public ExamAIController(Repository repo) {
        this.repository = repo;
        this.templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);
    }

    public void handleGet(@NotNull Context context) {
        String moduleID = context.pathParam("moduleid");
        context.render("generateExam.jte", Map.of("professors", repository.searchProfessorsForModule(moduleID, null, null), "moduleid",moduleID));
    }

    ExamAIJob testJob = new ExamAIJob("123", ExamAIStatus.FETCH_EXAMS);

    public void handlePost(@NotNull Context context) {
        //TODO Handle rate limit
        context.render("examAILoading.jte", Map.of("job", testJob, "isSseWrapper", true));
    }

    public void handleSse(@NotNull SseClient client) {
        String jobID = client.ctx().pathParam("jobid");
        client.keepAlive();
        new Thread(() -> {
            try {
                System.out.println("Start");
                Thread.sleep(3000);
                testJob.status(ExamAIStatus.SCAN);
                client.sendEvent("message", renderJob(testJob));
                System.out.println("GEBBBBB");
                Thread.sleep(3000);
                testJob.status(ExamAIStatus.GENERATING);
                client.sendEvent("message", renderJob(testJob));
                Thread.sleep(3000);
                testJob.status(ExamAIStatus.COMPILING);
                client.sendEvent("message", renderJob(testJob));
                Thread.sleep(3000);
                testJob.status(ExamAIStatus.DONE);
                client.sendEvent("message", renderJob(testJob));
                Thread.sleep(3000);
                testJob.status(ExamAIStatus.FAILED);
                client.sendEvent("message", renderJob(testJob));
                Thread.sleep(3000);
                testJob.status(ExamAIStatus.FAILED);
                testJob.errorMessage("Tja, da haben wir den salat");
                client.sendEvent("message", renderJob(testJob));
                Thread.sleep(6000);
                client.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).start();
    }

    private String renderJob(ExamAIJob job) {
        StringOutput output = new StringOutput();
        templateEngine.render("examAILoading.jte", Map.of("job", testJob), output);
        return output.toString();
    }
}

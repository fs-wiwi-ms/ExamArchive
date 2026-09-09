package ms.wiwi.examarchive.controller;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.ai.ExamAIJob;
import ms.wiwi.examarchive.ai.ExamAIStatus;
import ms.wiwi.examarchive.model.Professor;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.services.AIService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExamAIController {

    private final Repository repository;
    private final TemplateEngine templateEngine;
    private final AIService aiService;
    private final Map<String, SseClient>  sseClients = new ConcurrentHashMap<>();

    public ExamAIController(Repository repo, AIService aiService) {
        this.repository = repo;
        this.aiService = aiService;
        this.templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);
    }

    public void handleGet(@NotNull Context context) {
        String moduleID = context.pathParam("moduleid");
        context.render("generateExam.jte", Map.of("professors", repository.searchProfessorsForModule(moduleID, null, null), "moduleid",moduleID));
    }

    public void handlePost(@NotNull Context context) {
        // TODO: Handle rate limit
        String moduleID = context.pathParam("moduleid");
        int year = 0;
        if (context.formParam("year") != null) {
            try {
                year = Integer.parseInt(context.formParam("year"));
            } catch (NumberFormatException _) {
            }
        }

        List<String> profIDs = context.formParams("profid");
        List<Professor> professors = repository.searchProfessorsForModule(moduleID, null, null);
        professors = professors.stream().filter(professor -> profIDs.contains(professor.professorID())).toList();
        User user = context.sessionAttribute("user");

        String jobID = aiService.generateExam(year, professors, user, job -> {
            SseClient sseClient = sseClients.get(job.id());
            if (sseClient == null) {
                return;
            }
            sseClient.sendEvent("message", renderJob(job));
            if (job.status() == ExamAIStatus.DONE || job.status() == ExamAIStatus.FAILED) {
                sseClient.close();
            }
        });
        context.render("examAILoading.jte", Map.of(
                "job", new ExamAIJob(jobID, ExamAIStatus.FETCH_EXAMS),
                "isSseWrapper", true
        ));
    }

    public void handleSse(@NotNull SseClient client) {
        String jobID = client.ctx().pathParam("jobid");
        client.keepAlive();
        sseClients.put(jobID, client);
        client.onClose(() -> sseClients.remove(jobID));
        ExamAIJob currentJob = aiService.getJob(jobID);
        if (currentJob != null) {
            client.sendEvent("message", renderJob(currentJob));
            if (currentJob.status() == ExamAIStatus.DONE || currentJob.status() == ExamAIStatus.FAILED) {
                client.close();
            }
        }
    }

    private String renderJob(ExamAIJob job) {
        StringOutput output = new StringOutput();
        templateEngine.render("examAILoading.jte", Map.of("job", job), output);
        return output.toString();
    }
}

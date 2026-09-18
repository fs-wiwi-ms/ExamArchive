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
    private final int maxExamsPerSemester;
    private final int maxTokenPerWeek;
    private final Map<String, SseClient>  sseClients = new ConcurrentHashMap<>();

    public ExamAIController(Repository repo, AIService aiService, int maxExamsPerSemester, int maxTokenPerWeek) {
        this.repository = repo;
        this.aiService = aiService;
        this.maxExamsPerSemester = maxExamsPerSemester;
        this.maxTokenPerWeek = maxTokenPerWeek;
        this.templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);
    }

    public void handleGet(@NotNull Context context) {
        String moduleID = context.pathParam("moduleid");
        context.render("generateExam.jte", Map.of("professors", repository.searchProfessorsForModule(moduleID, null, null),
                "moduleid",moduleID,
                "userLimit", maxExamsPerSemester,
                "userUsage", repository.calculateUserUsage(context.sessionAttribute("user"))));
    }

    public void handlePost(@NotNull Context context) {
        User user = context.sessionAttribute("user");
        int usage = repository.calculateUserUsage(user);
        int globalUsage = repository.calculateNetTokenUsage();
        if (usage >= maxExamsPerSemester) {
            ExamAIJob errorJob = new ExamAIJob("error", "error", ExamAIStatus.FAILED);
            errorJob.errorMessage("You have reached the maximum number of exams per semester");
            context.result(renderJob(errorJob));
            return;
        }
        if (globalUsage >= maxTokenPerWeek) {
            ExamAIJob errorJob = new ExamAIJob("error", "error", ExamAIStatus.FAILED);
            errorJob.errorMessage("The global token limit has been reached. Please try again tomorrow");
            context.result(renderJob(errorJob));
            return;
        }
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
        String jobID = aiService.generateExam(year, professors, moduleID, user, job -> {
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
                "job", new ExamAIJob(jobID, moduleID, ExamAIStatus.FETCH_EXAMS),
                "moduleid", moduleID,
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

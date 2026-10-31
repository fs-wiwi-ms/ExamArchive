package ms.wiwi.examarchive.controller;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.ai.ExamAIJob;
import ms.wiwi.examarchive.ai.ExamAIStatus;
import ms.wiwi.examarchive.ai.OpenAIClient;
import ms.wiwi.examarchive.model.Professor;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.services.AIService;
import ms.wiwi.examarchive.services.ApiKeyEncryptionService;
import ms.wiwi.examarchive.services.JteLocalizer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExamAIController {

    private final Repository repository;
    private final TemplateEngine templateEngine;
    private final AIService aiService;
    private final OpenAIClient openAIClient;
    private final ApiKeyEncryptionService apiKeyEncryptionService;
    private final Map<String, SseClient>  sseClients = new ConcurrentHashMap<>();

    public ExamAIController(Repository repo, AIService aiService, OpenAIClient openAIClient, ApiKeyEncryptionService apiKeyEncryptionService) {
        this.repository = repo;
        this.aiService = aiService;
        this.templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);
        this.openAIClient = openAIClient;
        this.apiKeyEncryptionService = apiKeyEncryptionService;
    }

    public void handleGet(@NotNull Context context) {
        String moduleID = context.pathParam("moduleid");
        String apiKey = repository.getApiKeyForUser(context.sessionAttribute("user"));
        if (apiKey == null) {
            context.render("enterApiKey.jte", Map.of("moduleid", moduleID));
            return;
        }
        try {
            apiKey = apiKeyEncryptionService.decrypt(apiKey);
            if(apiKey.isBlank()) {
                context.render("enterApiKey.jte", Map.of("moduleid", moduleID));
                return;
            }
            if(!openAIClient.testAPIKey(apiKey)) {
                context.render("enterApiKey.jte", Map.of("moduleid", moduleID));
                return;
            }
        } catch (Exception e) {
            context.render("enterApiKey.jte", Map.of("moduleid", moduleID));
            return;
        }
        context.render("generateExam.jte", Map.of("professors", repository.searchProfessorsForModule(moduleID, null, null),
                "moduleid",moduleID));
    }

    public void handleGetApiKey(@NotNull Context context) {
        context.render("enterApiKey.jte", Map.of("moduleid", ""));
    }

    public void handlePostApiKey(@NotNull Context context) throws Exception {
        User user = context.sessionAttribute("user");
        String apiKey = context.formParam("apikey");
        String moduleid = context.formParam("moduleid");
        if (apiKey == null || apiKey.isBlank()) {
            context.render("enterApiKey.jte", Map.of("moduleid", moduleid == null ? "" : moduleid,
                    "error", JteLocalizer.lookup("apikey.error.required")));
            return;
        }
        if (!openAIClient.testAPIKey(apiKey.trim())) {
            context.render("enterApiKey.jte", Map.of("moduleid", moduleid == null ? "" : moduleid,
                    "error", JteLocalizer.lookup("apikey.error.invalid")));
            return;
        }
        repository.setApiKeyForUser(user, apiKeyEncryptionService.encrypt(apiKey.trim()));
        if (moduleid == null || moduleid.isBlank()) {
            context.redirect("/exams/usercontent/list");
        } else {
            context.redirect("/exams/module/" + moduleid + "/examai");
        }
    }

    public void handleDeleteApiKey(@NotNull Context context) {
        repository.deleteApiKeyForUser(context.sessionAttribute("user"));
        context.redirect("/exams/usercontent/list");
    }

    public void handlePost(@NotNull Context context) {
        User user = context.sessionAttribute("user");
        String moduleID = context.pathParam("moduleid");
        int year = 0;
        if (context.formParam("year") != null && !context.formParam("year").isBlank()) {
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
                sseClient.sendEvent("sse-stop", "sse-stop");
                sseClient.close();
            }
        });
        context.render("examAILoading.jte", Map.of(
                "job", new ExamAIJob(jobID, moduleID, ExamAIStatus.FETCH_EXAMS, user),
                "moduleid", moduleID,
                "isSseWrapper", true
        ));
    }

    public void handleSse(@NotNull SseClient client) {
        String jobID = client.ctx().pathParam("jobid");
        ExamAIJob currentJob = aiService.getJob(jobID);
        if(currentJob == null){
            client.close();
            return;
        }
        client.keepAlive();
        sseClients.put(jobID, client);
        client.onClose(() -> sseClients.remove(jobID));
        client.sendEvent("message", renderJob(currentJob));
        if (currentJob.status() == ExamAIStatus.DONE || currentJob.status() == ExamAIStatus.FAILED) {
            client.sendEvent("sse-stop", "sse-stop");
            client.close();
        }
    }

    private String renderJob(ExamAIJob job) {
        StringOutput output = new StringOutput();
        templateEngine.render("examAILoading.jte", Map.of("job", job), output);
        return output.toString();
    }
}

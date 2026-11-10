package ms.wiwi.examarchive.ai;

import ms.wiwi.examarchive.model.User;

public final class ExamAIJob {
    private final String id;
    private final String moduleid;
    private String errorMessage;
    private User user;
    private ExamAIStatus status;

    public ExamAIJob(String id, String moduleID, ExamAIStatus status, User user) {
        this.id = id;
        this.status = status;
        this.moduleid = moduleID;
        this.user = user;
    }

    public String id() {
        return id;
    }

    public User user() {
        return user;
    }

    public String moduleid() {
        return moduleid;
    }

    public ExamAIStatus status() {
        return status;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public void status(ExamAIStatus status){
        this.status = status;
    }

    public void errorMessage(String errorMessage){
        this.errorMessage = errorMessage;
    }
}

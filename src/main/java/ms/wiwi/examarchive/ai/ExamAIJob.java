package ms.wiwi.examarchive.ai;

import ms.wiwi.examarchive.model.User;

import java.util.concurrent.atomic.AtomicInteger;

public final class ExamAIJob {
    private final String id;
    private final String moduleid;
    private String errorMessage;
    private final User user;
    private ExamAIStatus status;
    private int taskCount;
    private final AtomicInteger doneTaskCount = new AtomicInteger(0);
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

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }

    public int getDoneTaskCount() {
        return doneTaskCount.get();
    }

    public void increateDoneTaskCount(){
        doneTaskCount.incrementAndGet();
    }
}

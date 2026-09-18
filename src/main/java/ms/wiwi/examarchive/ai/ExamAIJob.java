package ms.wiwi.examarchive.ai;

public final class ExamAIJob {
    private final String id;
    private final String moduleid;
    private String errorMessage;
    private ExamAIStatus status;

    public ExamAIJob(String id, String moduleID, ExamAIStatus status) {
        this.id = id;
        this.status = status;
        this.moduleid = moduleID;
    }

    public String id() {
        return id;
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

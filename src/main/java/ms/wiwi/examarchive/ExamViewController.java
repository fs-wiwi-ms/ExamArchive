package ms.wiwi.examarchive;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.ExamStatus;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.User;

public class ExamViewController implements Handler {

    private final Repository repository;
    private final S3Service s3Service;

    public ExamViewController(Repository repository, S3Service s3Service) {
        this.repository = repository;
        this.s3Service = s3Service;
    }

    public void handle(Context ctx){
        String examId = ctx.pathParam("examid");
        if(examId.isBlank()){
            ctx.status(404);
            return;
        }
        Exam exam = repository.getExam(examId);
        if(exam == null){
            ctx.status(404);
            return;
        }
        User user = ctx.sessionAttribute("user");
        if(user == null){
            ctx.status(404);
            return;
        }
        if(exam.status() != ExamStatus.ACCEPTED && user.role() != Role.ADMIN){
            ctx.status(404);
            return;
        }
        String fileId = exam.fileID();
        String url = s3Service.createPresignedUrl(fileId, exam.name() + ".pdf");
        if(url == null){
            ctx.status(404);
            return;
        }
        if(ctx.header("HX-Request") != null){
            ctx.header("HX-Redirect", url);
            ctx.status(302);
            return;
        }
        ctx.redirect(url);
    }
}

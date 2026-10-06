package ms.wiwi.examarchive.controller;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.services.S3Service;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class UserExamDownloadController implements Handler {
    private final Repository repository;
    private final S3Service s3Service;

    public UserExamDownloadController(Repository repository, S3Service s3Service) {
        this.repository = repository;
        this.s3Service = s3Service;
    }

    @Override
    public void handle(@NotNull Context ctx) {
        User user = ctx.sessionAttribute("user");
        if(user == null){
            ctx.render("error.jte", Map.of("message", "Forbidden", "code", 403));
            return;
        }
        String userexamid = ctx.pathParam("userexamid");
        boolean isOnwer = repository.isUserExamOwner(user, userexamid);
        if(!isOnwer){
            ctx.render("error.jte", Map.of("message", "File not found", "code", 404));
            return;
        }
        String url = s3Service.createPresignedUrl(userexamid, "FSWIWI-Probeklausur- " + userexamid.substring(5) + ".pdf", S3Service.Bucket.USER_EXAMS);
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

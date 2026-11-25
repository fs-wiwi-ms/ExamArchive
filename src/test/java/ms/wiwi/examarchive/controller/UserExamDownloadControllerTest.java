package ms.wiwi.examarchive.controller;

import io.javalin.http.Context;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.services.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserExamDownloadController} ownership checks, using a mocked Javalin
 * {@link Context}, {@link Repository} and {@link S3Service}. These lock down that only the owner of a
 * user-generated exam can download it, and that a user who is not logged in gets a 403.
 */
@ExtendWith(MockitoExtension.class)
class UserExamDownloadControllerTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private Context ctx;
    @Mock
    private Repository repository;
    @Mock
    private S3Service s3Service;

    private UserExamDownloadController controller;

    @BeforeEach
    void setUp() {
        controller = new UserExamDownloadController(repository, s3Service);
    }

    private static User user() {
        return new User("u1", "John", "Doe", Instant.now(), Instant.now(), "john@example.com", Role.USER);
    }

    @Test
    void handle_noUser_renders403() {
        when(ctx.sessionAttribute("user")).thenReturn(null);

        controller.handle(ctx);

        verify(ctx).render("error.jte", Map.of("message", "Forbidden", "code", 403));
        verifyNoInteractions(s3Service);
    }

    @Test
    void handle_notOwner_renders404() {
        User user = user();
        when(ctx.sessionAttribute("user")).thenReturn(user);
        when(ctx.pathParam("userexamid")).thenReturn("uexam12345");
        when(repository.isUserExamOwner(user, "uexam12345")).thenReturn(false);

        controller.handle(ctx);

        verify(ctx).render("error.jte", Map.of("message", "File not found", "code", 404));
        verify(s3Service, never()).createPresignedUrl(anyString(), anyString(), any());
    }

    @Test
    void handle_owner_redirectsToPresignedUrl() {
        User user = user();
        when(ctx.sessionAttribute("user")).thenReturn(user);
        when(ctx.pathParam("userexamid")).thenReturn("uexam12345");
        when(repository.isUserExamOwner(user, "uexam12345")).thenReturn(true);
        when(ctx.header("HX-Request")).thenReturn(null);
        when(s3Service.createPresignedUrl("uexam12345", "FSWIWI-Probeklausur- 12345.pdf", S3Service.Bucket.USER_EXAMS))
                .thenReturn("https://s3/presigned");

        controller.handle(ctx);

        verify(ctx).redirect("https://s3/presigned");
        verify(ctx, never()).status(302);
    }

    @Test
    void handle_ownerWithHxRequest_returnsHxRedirectAnd302() {
        User user = user();
        when(ctx.sessionAttribute("user")).thenReturn(user);
        when(ctx.pathParam("userexamid")).thenReturn("uexam12345");
        when(repository.isUserExamOwner(user, "uexam12345")).thenReturn(true);
        when(ctx.header("HX-Request")).thenReturn("true");
        when(s3Service.createPresignedUrl("uexam12345", "FSWIWI-Probeklausur- 12345.pdf", S3Service.Bucket.USER_EXAMS))
                .thenReturn("https://s3/presigned");

        controller.handle(ctx);

        verify(ctx).header("HX-Redirect", "https://s3/presigned");
        verify(ctx).status(302);
        verify(ctx, never()).redirect(anyString());
    }

    @Test
    void handle_nullPresignedUrl_returns404() {
        User user = user();
        when(ctx.sessionAttribute("user")).thenReturn(user);
        when(ctx.pathParam("userexamid")).thenReturn("uexam12345");
        when(repository.isUserExamOwner(user, "uexam12345")).thenReturn(true);
        when(s3Service.createPresignedUrl("uexam12345", "FSWIWI-Probeklausur- 12345.pdf", S3Service.Bucket.USER_EXAMS))
                .thenReturn(null);

        controller.handle(ctx);

        verify(ctx).status(404);
    }
}

package ms.wiwi.examarchive.controller;

import io.javalin.http.Context;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.ExamStatus;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.Semester;
import ms.wiwi.examarchive.model.User;
import ms.wiwi.examarchive.services.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExamViewController} authorization/visibility logic, using a mocked Javalin
 * {@link Context}, {@link Repository} and {@link S3Service}. These lock down that only ACCEPTED exams
 * (or admins) can be viewed/downloaded, and that a download is only counted once access is granted.
 */
@ExtendWith(MockitoExtension.class)
class ExamViewControllerTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private Context ctx;
    @Mock
    private Repository repository;
    @Mock
    private S3Service s3Service;

    private ExamViewController controller;

    @BeforeEach
    void setUp() {
        controller = new ExamViewController(repository, s3Service);
    }

    private static User user(Role role) {
        return new User("u1", "John", "Doe", Instant.now(), Instant.now(), "john@example.com", role);
    }

    private static Exam exam(ExamStatus status) {
        return new Exam("Math Exam", "exam-1", "mod-1", 2024, Semester.SUMMER,
                Instant.now(), "file-1", "u1", status, "prof-1", "scan-1");
    }

    @Test
    void handle_blankExamId_returns404() {
        when(ctx.pathParam("examid")).thenReturn("");

        controller.handle(ctx);

        verify(ctx).status(404);
        verifyNoInteractions(repository, s3Service);
    }

    @Test
    void handle_unknownExam_returns404() {
        when(ctx.pathParam("examid")).thenReturn("exam-1");
        when(repository.getExam("exam-1")).thenReturn(null);

        controller.handle(ctx);

        verify(ctx).status(404);
        verifyNoInteractions(s3Service);
    }

    @Test
    void handle_noUser_returns404() {
        when(ctx.pathParam("examid")).thenReturn("exam-1");
        when(repository.getExam("exam-1")).thenReturn(exam(ExamStatus.ACCEPTED));
        when(ctx.sessionAttribute("user")).thenReturn(null);

        controller.handle(ctx);

        verify(ctx).status(404);
        verifyNoInteractions(s3Service);
    }

    @Test
    void handle_nonAcceptedExamAsUser_returns404AndDoesNotGenerateUrl() {
        when(ctx.pathParam("examid")).thenReturn("exam-1");
        when(repository.getExam("exam-1")).thenReturn(exam(ExamStatus.PENDING));
        when(ctx.sessionAttribute("user")).thenReturn(user(Role.USER));

        controller.handle(ctx);

        verify(ctx).status(404);
        verify(s3Service, never()).createPresignedUrl(anyString(), anyString(), any());
        verify(repository, never()).countDownload(any());
    }

    @Test
    void handle_acceptedExam_redirectsAndCountsDownload() {
        Exam accepted = exam(ExamStatus.ACCEPTED);
        when(ctx.pathParam("examid")).thenReturn("exam-1");
        when(repository.getExam("exam-1")).thenReturn(accepted);
        when(ctx.sessionAttribute("user")).thenReturn(user(Role.USER));
        when(ctx.header("HX-Request")).thenReturn(null);
        when(s3Service.createPresignedUrl("file-1", "Math Exam.pdf", S3Service.Bucket.EXAMS))
                .thenReturn("https://s3/presigned");

        controller.handle(ctx);

        verify(repository).countDownload(accepted);
        verify(ctx).redirect("https://s3/presigned");
        verify(ctx, never()).status(302);
    }

    @Test
    void handle_nonAcceptedExamAsAdmin_redirectsWithoutCountingDownload() {
        when(ctx.pathParam("examid")).thenReturn("exam-1");
        when(repository.getExam("exam-1")).thenReturn(exam(ExamStatus.PENDING));
        when(ctx.sessionAttribute("user")).thenReturn(user(Role.ADMIN));
        when(ctx.header("HX-Request")).thenReturn(null);
        when(s3Service.createPresignedUrl("file-1", "Math Exam.pdf", S3Service.Bucket.EXAMS))
                .thenReturn("https://s3/presigned");

        controller.handle(ctx);

        verify(repository, never()).countDownload(any());
        verify(ctx).redirect("https://s3/presigned");
    }

    @Test
    void handle_hxRequest_returnsHxRedirectAnd302() {
        when(ctx.pathParam("examid")).thenReturn("exam-1");
        when(repository.getExam("exam-1")).thenReturn(exam(ExamStatus.ACCEPTED));
        when(ctx.sessionAttribute("user")).thenReturn(user(Role.USER));
        when(ctx.header("HX-Request")).thenReturn("true");
        when(s3Service.createPresignedUrl("file-1", "Math Exam.pdf", S3Service.Bucket.EXAMS))
                .thenReturn("https://s3/presigned");

        controller.handle(ctx);

        verify(ctx).header("HX-Redirect", "https://s3/presigned");
        verify(ctx).status(302);
        verify(ctx, never()).redirect(anyString());
    }

    @Test
    void handle_nullPresignedUrl_returns404() {
        when(ctx.pathParam("examid")).thenReturn("exam-1");
        when(repository.getExam("exam-1")).thenReturn(exam(ExamStatus.ACCEPTED));
        when(ctx.sessionAttribute("user")).thenReturn(user(Role.USER));
        when(s3Service.createPresignedUrl("file-1", "Math Exam.pdf", S3Service.Bucket.EXAMS))
                .thenReturn(null);

        controller.handle(ctx);

        verify(ctx).status(404);
        verify(repository, never()).countDownload(any());
    }
}

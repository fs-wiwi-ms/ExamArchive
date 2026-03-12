package ms.wiwi.examarchive.admin;

import io.javalin.http.Context;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.*;
import ms.wiwi.examarchive.model.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class AdminExamsController {

    private final Logger logger = LoggerFactory.getLogger(AdminExamsController.class);
    private final Repository repository;

    public AdminExamsController(Repository repository) {
        this.repository = repository;
    }

    public void handleAddModule(Context ctx) {
        String body = ctx.formParam("name");
        if(body == null || body.isBlank()){
            ctx.result("Der Name darf nicht leer sein");
            return;
        }
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9ÄÖÜäöüß]+( [a-zA-Z0-9ÄÖÜäöüß]+)*$");
        if(!pattern.matcher(body).matches()){
            ctx.result("Der Name enthält ungültige Zeichen");
            return;
        }
        repository.addModule(body, UUID.randomUUID().toString());
        logger.info("Created module " + body);
        handleGet(ctx);
    }

    public void handleRemoveModule(Context ctx) {
        String body = ctx.formParam("id");
        if(body == null || body.isBlank()){
            ctx.result("Fehler beim entfernen des Moduls.");
            return;
        }
        boolean result = repository.deleteModule(body);
        if(!result){
            ctx.result("Das Modul konnte nicht gelöscht werden. Vermutlich hat es noch Klausuren :c");
            return;
        }
        logger.info("Deleted module " + body);
        handleGet(ctx);
    }

    public void acceptExam(Context ctx) {
        String body = ctx.formParam("id");
        if(body == null || body.isBlank()){
            ctx.result("Fehler beim akzeptieren der Klausur.");
            return;
        }
        logger.info("Accepting Exam: " + body);
        Exam exam = repository.getExam(body);
        if(exam == null){
            ctx.result("Klausur konnte nicht gefunden werden");
            return;
        }
        Exam newExam = new Exam(exam.name(), exam.examID(), exam.moduleID(), exam.year(), exam.semester(), exam.uploadDate(), exam.fileID(), exam.uploaderID(), ExamStatus.ACCEPTED, exam.professorID());
        repository.updateExam(newExam);
        handleGet(ctx);
    }

    public void deleteExam(Context ctx) {
        String body = ctx.formParam("id");
        if(body == null || body.isBlank()){
            ctx.result("Fehler beim löschen der Klausur");
            return;
        }
        logger.info("Deleting Exam: " + body);
        repository.deleteExam(body);
        handleGet(ctx);
    }

    public void handleGet(Context ctx) {
        List<AdminExamList> exams = repository.getAllExams();
        List<AdminExamList> acceptedExams = exams.stream().filter(exam -> exam.exam().status() == ExamStatus.ACCEPTED).toList();
        List<AdminExamList> pendingExams = exams.stream().filter(exam -> exam.exam().status() == ExamStatus.PENDING).toList();
        List<Module> modules = repository.getAllModules();
        ctx.render("adminExams.jte", Map.of("modules", modules, "acceptedExams", acceptedExams, "pendingExams", pendingExams));
    }
}

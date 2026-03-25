package ms.wiwi.examarchive.admin;

import io.javalin.http.Context;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.*;
import ms.wiwi.examarchive.model.Module;
import ms.wiwi.examarchive.services.EmailService;
import ms.wiwi.examarchive.services.S3Service;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class AdminExamsController {

    private final Logger logger = LoggerFactory.getLogger(AdminExamsController.class);
    private final Repository repository;
    private final S3Service s3Service;
    private final EmailService emailService;

    public AdminExamsController(Repository repository, S3Service s3Service, EmailService emailService) {
        this.repository = repository;
        this.s3Service = s3Service;
        this.emailService = emailService;
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
        User user = repository.getUser(exam.uploaderID());
        if (user != null && user.role() == Role.USER) {
            String subject = "Klausur akzeptiert";
            String message = """
                    Guten Tag %s,
                    Ihre eingereichte Klausur "%s" wurde erfolgreich in das Klausurarchiv aufgenommen.
                    Im Namen der Fachschaft Wirtschaftswissenschaften bedanken wir uns herzlich für Ihre Unterstützung. Mit Ihrem Beitrag leisten Sie einen wichtigen Teil zur Prüfungsvorbereitung Ihrer Mitstudierenden.
                    
                    Beste Grüße
                    Die Fachschaft WiWi
                    
                    Hinweis: Diese Nachricht wurde automatisch generiert. Bei Rückfragen stehen wir unter Klausurarchiv@fachschaft-wiwi.ms gerne zur Verfügung.
                    """.formatted(user.firstname() + " " + user.lastname(), exam.name());
            List<String> email = List.of(user.email());
            emailService.sendEmails(email, subject, message);
        }
        handleGet(ctx);
    }

    public void deleteExam(Context ctx) {
        String body = ctx.formParam("id");
        if(body == null || body.isBlank()){
            ctx.result("Fehler beim löschen der Klausur");
            return;
        }
        logger.info("Deleting Exam: " + body);
        Exam exam = repository.getExam(body);
        if(exam == null){
            ctx.result("Klausur konnte nicht gefunden werden");
            return;
        }
        s3Service.deleteFile(exam.fileID());
        repository.deleteExam(body);
        handleGet(ctx);
    }

    public void handleEditExam(Context ctx){
        String examID = ctx.pathParam("examid");
        if(examID.isBlank()){
            ctx.result("Fehler beim bearbeiten der Klausur");
            return;
        }
        Exam exam = repository.getExam(examID);
        if(exam == null){
            ctx.result("Klausur konnte nicht gefunden werden");
            return;
        }
        String moduleID = ctx.formParam("module") != null ? ctx.formParam("module") : exam.moduleID();
        int year = ctx.formParam("year") != null ? Integer.parseInt(ctx.formParam("year")) : exam.year();
        Semester semester = ctx.formParam("semester") != null ? Semester.valueOf(ctx.formParam("semester")) : exam.semester();
        String firstname = ctx.formParam("professor-firstname");
        String lastname = ctx.formParam("professor-lastname");
        Professor oldProfessor = repository.getProfessor(exam.professorID());
        if(firstname == null || lastname == null){
            firstname = oldProfessor.firstName();
            lastname = oldProfessor.lastName();
        }
        Professor professor = repository.getOrCreateProfessor(firstname, lastname);
        if(!moduleID.equals(exam.moduleID()) || year != exam.year() || semester != exam.semester() || !professor.professorID().equals(exam.professorID())){
            Exam newExam = new Exam(exam.name(), exam.examID(), moduleID, year, semester, exam.uploadDate(), exam.fileID(), exam.uploaderID(), ExamStatus.ACCEPTED, professor.professorID());
            repository.updateExam(newExam);
            logger.info("Updated Exam: {} ({})", exam.name(), exam.examID());
        }
        handleGet(ctx);
    }

    public void handleDeleteExam(Context ctx){
        String examID = ctx.pathParam("examid");
        if(examID.isBlank()){
            ctx.status(404);
            return;
        }
        Exam exam = repository.getExam(examID);
        if(exam == null){
            ctx.status(404);
            return;
        }
        s3Service.deleteFile(exam.fileID());
        repository.deleteExam(examID);
        handleGet(ctx);
    }

    public void handleEditGet(Context ctx){
        String examID = ctx.pathParam("examid");
        if(examID.isBlank()){
            ctx.status(404);
            return;
        }
        Exam exam = repository.getExam(examID);
        if(exam == null){
            ctx.status(404);
            return;
        }
        List<Module> modules = repository.getAllModules();
        Professor professor = repository.getProfessor(exam.professorID());
        ctx.render("adminEditExam.jte", Map.of("exam", exam, "modules", modules, "professor", professor));
    }

    public void handleDeleteKeyword(Context ctx){
        String keyword = ctx.pathParam("keyword");
        String moduleid = ctx.pathParam("moduleid");
        if(keyword.isBlank() || moduleid.isBlank()){
            handleGet(ctx);
            return;
        }
        KeyWord keyWord = new KeyWord(keyword, moduleid);
        repository.deleteKeyword(keyWord);
        ctx.status(200);
    }

    public void handleAddKeyword(Context ctx) {
        String keyword = ctx.formParam("keyword");
        String moduleid = ctx.pathParam("moduleid");
        if(keyword == null || keyword.isBlank() || moduleid.isBlank()){
            handleGet(ctx);
            return;
        }
        KeyWord keyWord = new KeyWord(keyword, moduleid);
        repository.addKeyword(keyWord);
        handleGet(ctx);
    }


    public void handleDeleteDegree(@NotNull Context ctx) {
        String majorID = ctx.pathParam("degreeid");
        if(majorID.isBlank()){
            ctx.status(404);
            return;
        }
        repository.deleteDegree(majorID);
        handleGet(ctx);
    }

    public void handleAddDegree(@NotNull Context ctx) {
        String degreeName = ctx.formParam("name");
        if(degreeName == null || degreeName.isBlank()){
            ctx.status(404);
            return;
        }
        Degree degree = new Degree(UUID.randomUUID().toString(), degreeName);
        repository.addDegree(degree);
        handleGet(ctx);
    }

    public void handleModuleDegreeUpdate(Context ctx){
        String moduleID = ctx.queryParam("moduleID");
        String degreeID = ctx.queryParam("degreeID");
        boolean isChecked = ctx.formParam("assigned") != null;
        logger.info("Updating degree assignment for module {} and degree {}", moduleID, degreeID);
        if(moduleID == null || moduleID.isBlank() || degreeID == null || degreeID.isBlank()){
            logger.error("Invalid request");
            logger.error("ModuleID: " + moduleID);
            logger.error("DegreeID: " + degreeID);
            ctx.status(400);
            return;
        }
        if(isChecked){
            logger.info("Adding degree to module");
            repository.addDegreeToModule(moduleID, degreeID);
            return;
        }
        logger.info("Removing degree from module");
        repository.removeDegreeFromModule(moduleID, degreeID);
    }

    public void handleGet(Context ctx) {
        List<AdminExamListDTO> exams = repository.getAllExams();
        List<AdminExamListDTO> acceptedExams = exams.stream().filter(exam -> exam.exam().status() == ExamStatus.ACCEPTED).toList();
        List<AdminExamListDTO> pendingExams = exams.stream().filter(exam -> exam.exam().status() == ExamStatus.PENDING).toList();
        List<Module> modules = repository.getAllModules();
        List<KeyWord> keyWords = repository.getKeywords();
        List<Degree> degrees = repository.getDegrees();
        List<ModuleDegreeDTO> degreesAndModules = repository.getDegreesAndModules();
        ctx.render("adminExams.jte", Map.of("modules", modules, "acceptedExams", acceptedExams, "pendingExams", pendingExams, "keyWords", keyWords, "degrees", degrees, "degreesAndModules", degreesAndModules));
    }
}

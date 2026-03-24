package ms.wiwi.examarchive.controller;

import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.*;
import ms.wiwi.examarchive.model.Module;
import ms.wiwi.examarchive.services.JteLocalizer;
import ms.wiwi.examarchive.services.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class AddExamController {

    private static final Logger logger = LoggerFactory.getLogger(AddExamController.class.getName());
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}\\s\\-]+$");
    private final Repository repository;
    private final S3Service s3Service;

    public AddExamController(Repository repository, S3Service s3Service) {
        this.repository = repository;
        this.s3Service = s3Service;
    }

    public void handleGet(Context ctx) {
        List<Module> modules = repository.getAllModules();
        ctx.render("addExam.jte", Map.of("modules", modules));
    }

    public void handlePost(Context ctx) {
        List<String> errors = new ArrayList<>();
        String moduleId = ctx.formParam("module");
        String firstName = ctx.formParam("firstName");
        String lastName = ctx.formParam("lastName");
        String yearStr = ctx.formParam("year");
        String semesterStr = ctx.formParam("semester");
        UploadedFile file = ctx.uploadedFile("file");
        if (moduleId == null || moduleId.isBlank()) {
            errors.add(JteLocalizer.lookup("addExam.error.moduleRequired"));
        }
        Module module = repository.getModule(moduleId);
        if (module == null) {
            errors.add(JteLocalizer.lookup("addExam.error.moduleRequired"));
        }
        if (firstName == null || !NAME_PATTERN.matcher(firstName).matches()) {
            errors.add(JteLocalizer.lookup("addExam.error.invalidFirstName"));
        }
        if (lastName == null || !NAME_PATTERN.matcher(lastName).matches()) {
            errors.add(JteLocalizer.lookup("addExam.error.invalidLastName"));
        }
        int year = 0;
        if (yearStr == null || yearStr.isBlank()) {
            errors.add(JteLocalizer.lookup("addExam.error.yearRequired"));
        } else {
            try {
                year = Integer.parseInt(yearStr);
                int currentYear = Year.now().getValue();
                if (year < currentYear - 30 || year > currentYear + 30) {
                    errors.add(JteLocalizer.lookup("addExam.error.yearOutOfRange"));
                }
            } catch (NumberFormatException e) {
                errors.add(JteLocalizer.lookup("addExam.error.invalidYearFormat"));
            }
        }
        Semester semester = null;
        if (semesterStr == null || semesterStr.isBlank()) {
            errors.add(JteLocalizer.lookup("addExam.error.invalidSemester"));
        }
        try {
            semester = Semester.valueOf(semesterStr);
        } catch (IllegalArgumentException e) {
            errors.add(JteLocalizer.lookup("addExam.error.semesterRequired"));
        }
        if (file == null || file.size() == 0) {
            errors.add(JteLocalizer.lookup("addExam.error.fileRequired"));
        }
        if (!errors.isEmpty()) {
            List<Module> modules = repository.getAllModules();
            ctx.render("addExamForm.jte", Map.of(
                    "modules", modules,
                    "errors", errors
            ));
            return;
        }
        User user = ctx.sessionAttribute("user");
        String examId = UUID.randomUUID().toString();
        String fileID = UUID.randomUUID().toString();
        try (InputStream content = file.content()) {
            File pdf = Files.createTempFile(examId, ".pdf").toFile();
            Files.copy(content, pdf.toPath(), StandardCopyOption.REPLACE_EXISTING);
            s3Service.uploadPDF(pdf, fileID);
            pdf.delete();
            Professor professor = repository.getOrCreateProfessor(firstName, lastName);
            String examName = module.name() + "-" + semesterStr + "-" + yearStr;
            Exam exam = new Exam(examName, examId, module.moduleID(), year, semester, Instant.now(), fileID, user.id(), ExamStatus.PENDING, professor.professorID());
            repository.addExam(exam);
        } catch (Exception e) {
            logger.error("Error uploading file: ", e);
            s3Service.deleteFile(fileID);
            errors.add(JteLocalizer.lookup("addExam.error.fileUploadFailed"));
        }
        if (!errors.isEmpty()) {
            List<Module> modules = repository.getAllModules();
            ctx.render("addExamForm.jte", Map.of(
                    "modules", modules,
                    "errors", errors
            ));
            return;
        }
        ctx.render("addExamSuccess.jte");
    }

    public void handleFormContent(Context ctx) {
        String moduleId = ctx.queryParam("module");
        List<Professor> suggestions = List.of();
        if (moduleId != null && !moduleId.isBlank()) {
            suggestions = repository.searchProfessorsForModule(moduleId, null, null);
        }
        ctx.render("formContent.jte", Map.of(
                "firstName", "",
                "lastName", "",
                "suggestions", suggestions,
                "locked", false
        ));
    }

    public void handleSearchProfessors(Context ctx) {
        String moduleId = ctx.formParam("module");
        String firstName = ctx.formParam("firstName");
        String lastName = ctx.formParam("lastName");
        List<Professor> suggestions = List.of();
        if (moduleId != null && !moduleId.isBlank()) {
            suggestions = repository.searchProfessorsForModule(moduleId, firstName, lastName);
        }
        ctx.render("professorSuggestions.jte", Map.of("suggestions", suggestions));
    }

    public void handleSelectProfessor(Context ctx) {
        String firstName = ctx.queryParam("firstName");
        String lastName = ctx.queryParam("lastName");
        ctx.render("professorSection.jte", Map.of(
                "firstName", firstName == null ? "" : firstName,
                "lastName", lastName == null ? "" : lastName,
                "suggestions", List.of(),
                "locked", true
        ));
    }

    public void handleClearProfessor(Context ctx) {
        String moduleId = ctx.queryParam("module");
        List<Professor> suggestions = List.of();
        if (moduleId != null && !moduleId.isBlank()) {
            suggestions = repository.searchProfessorsForModule(moduleId, null, null);
        }
        ctx.render("professorSection.jte", Map.of(
                "firstName", "",
                "lastName", "",
                "suggestions", suggestions,
                "locked", false
        ));
    }
}

package ms.wiwi.examarchive;

import io.javalin.http.Context;
import ms.wiwi.examarchive.model.Module;
import ms.wiwi.examarchive.model.Professor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class ShowModuleHandler {

    private final Repository repository;

    public ShowModuleHandler(Repository repository) {
        this.repository = repository;
    }

    public void handleGet(@NotNull Context ctx) throws Exception {
        String moduleID = ctx.pathParam("moduleid");
        if (moduleID.isBlank()) {
            ctx.status(404);
            return;
        }
        Module module = repository.getModule(moduleID);
        if(module == null){
            ctx.status(404);
            ctx.result("Module not found");
            return;
        }
        List<ProfessorExamDTO> exams = repository.getExamsAndProfessors(moduleID);
        List<Professor> professors = exams.stream().map(ProfessorExamDTO::professor).distinct().toList();
        ctx.render("module.jte", Map.of("exams", exams, "module", module, "professors", professors));
    }

    public void handleFilter(Context ctx){
        List<String> filter = ctx.formParams("professorFilter");
        String moduleID = ctx.pathParam("moduleid");
        List<ProfessorExamDTO> exams = repository.getExamsAndProfessors(moduleID);
        if(filter.isEmpty()){
            ctx.render("examCards.jte", Map.of("exams", exams));
            return;
        }
        ctx.render("examCards.jte", Map.of("exams", exams.stream().filter(exam -> filter.contains(exam.professor().professorID())).toList()));
    }
}

package ms.wiwi.examarchive;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class SearchExamController implements Handler {

    private final Repository repository;

    public SearchExamController(Repository repository) {
        this.repository = repository;
    }


    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        if(ctx.method() == HandlerType.GET){
            ctx.render("search.jte");
            return;
        }
        if(ctx.method() != HandlerType.POST){
            return;
        }
        if(ctx.formParam("search") == null || ctx.formParam("search").isBlank()){
            return;
        }
        List<ModuleSearchResult> resultList = repository.searchModules(ctx.formParam("search"));
        System.out.println(ctx.formParamMap());
        System.out.println(ctx.body());
        ctx.render("searchResult.jte", Map.of("results", resultList));
    }
}

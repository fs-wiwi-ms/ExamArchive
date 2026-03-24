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
    public void handle(@NotNull Context ctx) {
        if(ctx.method() == HandlerType.GET){
            ctx.render("search.jte", Map.of("degrees", repository.getDegrees()));
            return;
        }
        if(ctx.method() != HandlerType.POST){
            return;
        }
        String searchQuery = ctx.formParam("search");
        if(searchQuery == null || searchQuery.isBlank() || searchQuery.length() < 3){
            List<ModuleSearchResultDTO> resultList = repository.getMostDownloadedModules();
            ctx.render("searchResult.jte", Map.of("results", resultList));
            return;
        }
        List<String> degreeIds = ctx.formParams("degreeFilter");
        List<ModuleSearchResultDTO> resultList = repository.searchModules(searchQuery, degreeIds);
        ctx.render("searchResult.jte", Map.of("results", resultList));
    }
}
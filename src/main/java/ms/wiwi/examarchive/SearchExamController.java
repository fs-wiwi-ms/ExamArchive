package ms.wiwi.examarchive;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class SearchExamController implements Handler {


    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        if(ctx.method() == HandlerType.GET){
            ctx.render("search.jte");
            return;
        }
        if(ctx.method() != HandlerType.POST){
            return;
        }
        List<ModuleSearchResult> resultList = List.of(
                new ModuleSearchResult("Mathe 1", "12345", new String[]{"Prof. Dr. Pfingsten", "Dr. Ingolf Terveer"}, new Random().nextInt(1,99)),
                new ModuleSearchResult("Info 123", "12345", new String[]{"Prof. Dr. Pfingsten", "Dr. Ingolf Terveer", "Dr. Dr. Breeeeee"}, 10),
                new ModuleSearchResult("BWL I", "12345", new String[]{"Prof. Dr. Pfingsten", "Dr. Ingolf Fagradal"}, 10),
                new ModuleSearchResult("BWL 2", "12345", new String[]{"Prof. Dr. Pfingsten", "Dr. Ingolf Terveer"}, 10)
        );
        System.out.println(ctx.formParamMap());
        System.out.println(ctx.body());
        ctx.render("searchResult.jte", Map.of("results", resultList));
    }
}

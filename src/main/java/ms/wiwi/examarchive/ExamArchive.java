package ms.wiwi.examarchive;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinJte;

public class ExamArchive {

    static void main() {
        new ExamArchive().start();
    }

    private void start() {
        Javalin javalin = Javalin.create(config -> {
            config.fileRenderer(new JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)));
            config.routes.get("/", ctx -> ctx.render("helloWorld.jte"));
        });
        javalin.start(1910);
    }


}

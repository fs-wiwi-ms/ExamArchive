package ms.wiwi.examarchive;

import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinJte;

public class ExamArchive {

    static void main(String[] args) {
        new ExamArchive().start();
    }

    private void start() {
        Javalin javalin = Javalin.create(config -> {
            config.fileRenderer(new JavalinJte());
            config.routes.get("/", ctx -> ctx.render("helloWorld.jte"));
        });
        javalin.start(1910);
    }


}

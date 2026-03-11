package ms.wiwi.examarchive.admin;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class AdminIndexController implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.render("admin.jte");
    }
}

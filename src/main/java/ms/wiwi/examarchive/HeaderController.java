package ms.wiwi.examarchive;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import ms.wiwi.examarchive.model.User;

import java.util.Map;

public class HeaderController implements Handler {

    public void handle(Context ctx){
        User user = ctx.sessionAttribute("user");
        if(user == null){
            ctx.render("dropdownMenu.jte", Map.of("loggedIn", false));
            return;
        }
        ctx.render("dropdownMenu.jte", Map.of("loggedIn", true));
    }
}

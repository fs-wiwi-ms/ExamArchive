package ms.wiwi.examarchive;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import ms.wiwi.examarchive.model.User;

import java.util.Locale;
import java.util.Map;

public class HeaderController implements Handler {

    public void handle(Context ctx){
        User user = ctx.sessionAttribute("user");
        String language = ctx.cookie("lang") == null ? getDefaultLocale(ctx) : ctx.cookie("lang");
        if(ctx.queryParam("language") != null){
            language = ctx.queryParam("language").equals("german") ? "german" : "english";
            ctx.cookie("lang", language, 365 * 24 * 60 * 60);
            ctx.redirect("/");
            return;
        }
        if(user == null){
            ctx.render("dropdownMenu.jte", Map.of("loggedIn", false, "lang", language));
            return;
        }
        ctx.render("dropdownMenu.jte", Map.of("loggedIn", true, "lang", language));
    }

    private String getDefaultLocale(Context ctx){
        String acceptLanguage = ctx.header("Accept-Language");
        Locale locale = Locale.GERMAN;
        if (acceptLanguage != null && acceptLanguage.toLowerCase().startsWith("en")) {
            locale = Locale.ENGLISH;
        }
        return locale.equals(Locale.GERMAN) ? "german" : "english";
    }
}

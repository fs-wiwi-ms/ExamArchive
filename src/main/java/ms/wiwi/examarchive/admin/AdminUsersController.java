package ms.wiwi.examarchive.admin;

import io.javalin.http.Context;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.Role;
import ms.wiwi.examarchive.model.User;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class AdminUsersController {

    private final Repository repository;

    public AdminUsersController(Repository repository) {
        this.repository = repository;
    }

    public void handleGet(Context ctx){
        int users = repository.countUsersByRole(Role.USER);
        int admins = repository.countUsersByRole(Role.ADMIN);
        int blocked = repository.countUsersByRole(Role.BLOCKED);
        ctx.render("adminUsers.jte", Map.of("users", users, "admins", admins, "blocked", blocked));
    }

    public void handleSearch(Context ctx){
        String search = ctx.formParam("search");
        List<User> users = repository.searchUsers(search);
        ctx.render("adminUsersSearchResult.jte", Map.of("users", users));
    }

    public void handleUnblock(@NotNull Context context) {
        String id = context.formParam("id");
        if(id == null || id.isBlank()){
            context.status(200).result("Fehler beim entsperren");
            return;
        }
        User user = repository.getUser(id);
        if(user == null){
            context.status(200).result("Nutzer nicht gefunden");
            return;
        }
        repository.updateUserRole(user.id(), Role.USER);
        context.status(200).result("Account entsperrt");
    }

    public void handleBlock(@NotNull Context context) {
        String id = context.formParam("id");
        if(id == null || id.isBlank()){
            context.status(200).result("Fehler beim entsperren");
            return;
        }
        User user = repository.getUser(id);
        if(user == null){
            context.status(200).result("Nutzer nicht gefunden");
            return;
        }
        repository.updateUserRole(user.id(), Role.BLOCKED);
        context.status(200).result("Account gesperrt");
    }
}

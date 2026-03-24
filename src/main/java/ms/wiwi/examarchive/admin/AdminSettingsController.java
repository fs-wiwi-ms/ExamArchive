package ms.wiwi.examarchive.admin;

import io.javalin.http.Context;
import ms.wiwi.examarchive.services.MotdService;
import ms.wiwi.examarchive.ProfessorExamDTO;
import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.Motd;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class AdminSettingsController {

    private final MotdService motdService;
    private final Repository repository;

    public AdminSettingsController(MotdService motdService, Repository repository) {
        this.motdService = motdService;
        this.repository = repository;
    }

    public void handleGet(Context ctx){
        int weeklyDownloads = repository.getWeeklyDownloads();
        int monthlyDownloads = repository.getMonthlyDownloads();
        int yearlyDownloads = repository.getYearlyDownloads();
        int totalDownloads = repository.getTotalDownloads();
        List<ProfessorExamDTO> topDownloads = repository.getTopDownloadedExams(15);
        ctx.render("adminSettings.jte", Map.of("weeklyDownloads", weeklyDownloads, "monthlyDownloads", monthlyDownloads, "yearlyDownloads", yearlyDownloads, "totalDownloads", totalDownloads, "topExams", topDownloads));
    }

    public void handleUpdateMotdPost(Context context){
        String motdText = context.formParam("motd");
        String daysStr = context.formParam("days");
        String hoursStr = context.formParam("hours");
        String minutesStr = context.formParam("minutes");
        int days;
        int hours;
        int minutes;
        try {
            days = Integer.parseInt(daysStr);
            hours = Integer.parseInt(hoursStr);
            minutes = Integer.parseInt(minutesStr);
        } catch (NumberFormatException | NullPointerException e) {
            context.status(400);
            context.result(e.getMessage());
            return;
        }
        if(motdText == null || motdText.isBlank() || hours == -1 || minutes == -1 || days == -1) {
            context.status(400);
            return;
        }
        Instant expires = Instant.now().plus(days, ChronoUnit.DAYS).plus(hours, ChronoUnit.HOURS).plus(minutes, ChronoUnit.MINUTES);
        if(Instant.now().isAfter(expires)) {
            context.status(400);
            return;
        }
        Motd motd = new Motd(motdText, expires);
        motdService.updateMotd(motd);
        context.status(200).result("Erfolgreich!");
    }
}

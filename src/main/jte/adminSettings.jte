@import ms.wiwi.examarchive.ProfessorExamDTO
@import ms.wiwi.examarchive.model.Motd
@import java.util.List
@param Motd motd = ms.wiwi.examarchive.MotdService.getMotd()
@param List<ProfessorExamDTO> topExams
@param int weeklyDownloads
@param int monthlyDownloads
@param int yearlyDownloads
@param int totalDownloads
<div class="container">
    <article>
        <h3>MOTD Bearbeiten</h3>
        <small>Stelle die Benachrichtigung auf der Start- und Suchseite (Message-Of-The-Day) unter dem Header ein. Du kannst HTML-Elemente
            wie ${"<strong>"} oder ${"<small>"} nutzen.</small>
        <form id="motd-form">
            @if(motd != null)
                <input type="text" name="motd" value="${motd.text()}" required>
            @else
                <input type="text" name="motd" placeholder="Nachricht festlegen" required>
            @endif
            <div class="grid">
                <div>
                    <label for="days">Tage</label>
                    <input type="number" id="days" name="days" min="0" value="0" required>
                </div>
                <div>
                    <label for="hours">Stunden</label>
                    <input type="number" id="hours" name="hours" min="0" max="23" value="1" required>
                </div>
                <div>
                    <label for="minutes">Minuten</label>
                    <input type="number" id="minutes" name="minutes" min="0" max="59" value="0" required>
                </div>
            </div>
            <button
                    type="button"
                    hx-post="/admin/updatemotd"
                    hx-include="#motd-form"
                    hx-swap="outerHTML">Speichern
            </button>
        </form>
    </article>

    <article>
        <header>
            <h3>Statistiken</h3>
            <small>Diese Statistiken können helfen zu entscheiden in welchen Modulen Altkausuren sehr gefragt sind und
                ob es sich lohnt weitere anzufragen</small>
        </header>
        <main>
            <div class="grid">
                <article style="text-align: center; padding: 1.5rem;">
                    <hgroup style="margin-bottom: 0;">
                        <h2 style="margin-bottom: 0;">${weeklyDownloads}</h2>
                        <p>Woche</p>
                    </hgroup>
                </article>
                <article style="text-align: center; padding: 1.5rem;">
                    <hgroup style="margin-bottom: 0;">
                        <h2 style="margin-bottom: 0;">${monthlyDownloads}</h2>
                        <p>Monat</p>
                    </hgroup>
                </article>
                <article style="text-align: center; padding: 1.5rem;">
                    <hgroup style="margin-bottom: 0;">
                        <h2 style="margin-bottom: 0;">${yearlyDownloads}</h2>
                        <p>Jahr</p>
                    </hgroup>
                </article>
                <article style="text-align: center; padding: 1.5rem;">
                    <hgroup style="margin-bottom: 0;">
                        <h2 style="margin-bottom: 0;">${totalDownloads}</h2>
                        <p>Gesamt</p>
                    </hgroup>
                </article>
            </div>
        </main>
        <footer>
            <h4>Top Downloads:</h4>
            @template.examCards(exams = topExams)
        </footer>
    </article>
</div>
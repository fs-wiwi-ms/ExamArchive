@import ms.wiwi.examarchive.model.Motd
@param Motd motd = ms.wiwi.examarchive.MotdService.getMotd()
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
</div>
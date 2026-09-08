@import ms.wiwi.examarchive.ai.ExamAIJob
@import ms.wiwi.examarchive.ai.ExamAIStatus
@import ms.wiwi.examarchive.services.JteLocalizer

@param ExamAIJob job
@param boolean isSseWrapper = false

!{
    boolean isFailed = job.status() == ExamAIStatus.FAILED;
    boolean isDone = job.status() == ExamAIStatus.DONE;
    int currentOrdinal = isFailed ? -1 : job.status().ordinal();
    int progress = 0;
    if (isFailed || isDone) {
        progress = 100;
    } else if (job.status() == ExamAIStatus.FETCH_EXAMS) {
        progress = 10;
    } else if (job.status() == ExamAIStatus.SCAN) {
        progress = 30;
    } else if (job.status() == ExamAIStatus.GENERATING) {
        progress = 60;
    } else if (job.status() == ExamAIStatus.COMPILING) {
        progress = 85;
    } else if (job.status() == ExamAIStatus.UPLOADING) {
        progress = 95;
    }
}
@if(isSseWrapper)
<div id="exam-ai-generator-state"
     class="container narrow-form-container"
     hx-ext="sse"
     sse-connect="/exams/ai/job/${job.id()}"
     sse-swap="message"
     hx-swap="innerHTML">
    @endif

    <article>
        <header>
            <h2 class="shiny-title">✨ ExamAI</h2>
            @if(isFailed)
                <p style="color: var(--pico-del-color); margin-bottom: 0;">Generierung abgebrochen.</p>
            @elseif(isDone)
                <p style="color: var(--pico-ins-color); margin-bottom: 0;">Deine Klausur ist fertig!</p>
            @else
                <p style="color: var(--pico-muted-color); margin-bottom: 0;">Bitte schließe dieses Fenster nicht...</p>
            @endif
        </header>
        <progress value="${progress}" max="100"></progress>
        @if(isFailed)
            <div style="margin-top: 1rem; padding: 1rem; border: 1px solid var(--pico-form-element-invalid-border-color); border-radius: var(--pico-border-radius);">
                <strong style="color: var(--pico-form-element-invalid-border-color);">Fehler:</strong>
                ${job.errorMessage()}
            </div>

            <div style="margin-top: 1.5rem; text-align: center;">
                <button class="secondary" onclick="location.reload()">Neu starten</button>
            </div>
        @else
            <ul style="list-style: none; padding: 0; margin-top: 2rem; display: flex; flex-direction: column; gap: 1rem;">

                <li style="${currentOrdinal > 0 ? "color: var(--pico-muted-color);" : (currentOrdinal == 0 ? "font-weight: bold; color: var(--pico-primary);" : "")}">
                    @if(currentOrdinal > 0) ✔ @elseif(currentOrdinal == 0)
                        <span aria-busy="true" style="margin-right: 0.5rem;"></span> @endif
                    Klausuren werden aus der Datenbank abgerufen
                </li>

                <li style="${currentOrdinal > 1 ? "color: var(--pico-muted-color);" : (currentOrdinal == 1 ? "font-weight: bold; color: var(--pico-primary);" : "color: var(--pico-muted-color); opacity: 0.5;")}">
                    @if(currentOrdinal > 1) ✔ @elseif(currentOrdinal == 1)
                        <span aria-busy="true" style="margin-right: 0.5rem;"></span> @endif
                    Texterkennung wird durchgeführt
                </li>

                <li style="${currentOrdinal > 2 ? "color: var(--pico-muted-color);" : (currentOrdinal == 2 ? "font-weight: bold; color: var(--pico-primary);" : "color: var(--pico-muted-color); opacity: 0.5;")}">
                    @if(currentOrdinal > 2) ✔ @elseif(currentOrdinal == 2)
                        <span aria-busy="true" style="margin-right: 0.5rem;"></span> @endif
                    KI generiert die neue Probeklausur
                </li>

                <li style="${currentOrdinal > 3 ? "color: var(--pico-muted-color);" : (currentOrdinal == 3 ? "font-weight: bold; color: var(--pico-primary);" : "color: var(--pico-muted-color); opacity: 0.5;")}">
                    @if(currentOrdinal > 3) ✔ @elseif(currentOrdinal == 3)
                        <span aria-busy="true" style="margin-right: 0.5rem;"></span> @endif
                    Klausur wird gerendert
                </li>

                <li style="${currentOrdinal > 4 ? "color: var(--pico-muted-color);" : (currentOrdinal == 4 ? "font-weight: bold; color: var(--pico-primary);" : "color: var(--pico-muted-color); opacity: 0.5;")}">
                    @if(currentOrdinal > 4) ✔ @elseif(currentOrdinal == 4)
                        <span aria-busy="true" style="margin-right: 0.5rem;"></span> @endif
                    Dokument wird sicher gespeichert
                </li>

            </ul>
        @endif

        @if(isDone)
            <div style="margin-top: 2.5rem; display: flex; justify-content: center;">
                <a href="/exams/download/${job.id()}" role="button">Zur Klausur</a>
                <!--TODO: automatic download -->
            </div>
        @endif

    </article>
@if(isSseWrapper)
    </div>
@endif
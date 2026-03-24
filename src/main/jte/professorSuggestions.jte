@import ms.wiwi.examarchive.services.JteLocalizer
@import ms.wiwi.examarchive.model.Professor
@import java.util.List
@param List<Professor> suggestions

@if(suggestions != null && !suggestions.isEmpty())
    <div style="margin-top: 0.5rem; margin-bottom: 1.5rem;">
        <small style="color: var(--pico-muted-color); display: block; margin-bottom: 0.5rem;">
            ${JteLocalizer.get("addExam.suggestions")}:
        </small>
        <div style="display: flex; flex-wrap: wrap; gap: 0.5rem;">
            @for(Professor p : suggestions)
                <button type="button" class="secondary outline"
                        style="width: auto; margin: 0; padding: 0.25rem 0.75rem; font-size: 0.85rem; border-radius: 2rem;"
                        hx-get="/exams/upload/professors/select?firstName=${p.firstName()}&lastName=${p.lastName()}"
                        hx-target="#professor-section"
                        hx-swap="outerHTML">
                    + ${p.firstName()} ${p.lastName()}
                </button>
            @endfor
        </div>
    </div>
@endif
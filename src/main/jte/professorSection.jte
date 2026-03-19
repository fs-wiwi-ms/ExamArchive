@import ms.wiwi.examarchive.JteLocalizer
@import ms.wiwi.examarchive.model.Professor
@import java.util.List

@param String firstName = ""
@param String lastName = ""
@param List<Professor> suggestions = null
@param boolean locked = false

<div id="professor-section">
    <label>${JteLocalizer.get("addExam.professor")}:</label>
    <div role="group">
        @if(locked)
            <input type="text" name="firstName" placeholder="${JteLocalizer.get("addExam.firstname")}"
                   pattern="^[\p{L}\s\-]+$" required value="${firstName}"
                   hx-post="/exams/upload/professors/search"
                   hx-trigger="keyup changed delay:250ms"
                   hx-target="#professor-suggestions"
                   hx-include="[name='module'], [name='firstName'], [name='lastName']"
                   autocomplete="off" readonly>
        @else
            <input type="text" name="firstName" placeholder="${JteLocalizer.get("addExam.firstname")}"
                   pattern="^[\p{L}\s\-]+$" required value="${firstName}"
                   hx-post="/exams/upload/professors/search"
                   hx-trigger="keyup changed delay:250ms"
                   hx-target="#professor-suggestions"
                   hx-include="[name='module'], [name='firstName'], [name='lastName']"
                   autocomplete="off">
        @endif
        @if(locked)
            <input type="text" name="lastName" placeholder="${JteLocalizer.get("addExam.lastname")}"
                   pattern="^[\p{L}\s\-]+$" required value="${lastName}"
                   hx-post="/exams/upload/professors/search"
                   hx-trigger="keyup changed delay:250ms"
                   hx-target="#professor-suggestions"
                   hx-include="[name='module'], [name='firstName'], [name='lastName']"
                   autocomplete="off"
                   readonly>
        @else
            <input type="text" name="lastName" placeholder="${JteLocalizer.get("addExam.lastname")}"
                   pattern="^[\p{L}\s\-]+$" required value="${lastName}"
                   hx-post="/exams/upload/professors/search"
                   hx-trigger="keyup changed delay:250ms"
                   hx-target="#professor-suggestions"
                   hx-include="[name='module'], [name='firstName'], [name='lastName']"
                   autocomplete="off">
        @endif
        @if(locked)
            <button type="button" class="secondary outline"
                    hx-get="/exams/upload/professors/clear"
                    hx-target="#professor-section"
                    hx-swap="outerHTML"
                    hx-include="[name='module']">X
            </button>
        @endif
    </div>

    <div id="professor-suggestions">
        @if(suggestions != null && !suggestions.isEmpty() && !locked)
            @template.professorSuggestions(suggestions = suggestions)
        @endif
    </div>
</div>
@import ms.wiwi.examarchive.services.JteLocalizer
@import ms.wiwi.examarchive.model.Module
@import java.util.List
@param List<Module> modules
@param List<String> errors

<form hx-post="/exams/upload"
      hx-target="#form-container"
      hx-swap="innerHTML"
      enctype="multipart/form-data">

    <label>${JteLocalizer.get("addExam.module")}:</label>
    <select name="module" aria-label="${JteLocalizer.get("addExam.module")}" required
            hx-get="/exams/upload/form-content"
            hx-target="#dynamic-form-content"
            hx-swap="innerHTML">
        <option hidden disabled selected value>${JteLocalizer.get("addExam.module")}</option>
        @for(Module module : modules)
            <option value="${module.moduleID()}">${module.name()}</option>
        @endfor
    </select>

    <div id="dynamic-form-content"></div>

    @if(errors != null && !errors.isEmpty())
        <article class="error-container" style="border-color: var(--pico-del-color); margin-top: 1rem;">
            <header style="background-color: var(--pico-del-color); color: white;">Error</header>
            <ul style="margin-bottom: 0;">
                @for(String error : errors)
                    <li>${error}</li>
                @endfor
            </ul>
        </article>
    @endif
</form>
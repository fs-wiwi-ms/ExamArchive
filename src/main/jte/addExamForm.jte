@import ms.wiwi.examarchive.JteLocalizer
@import ms.wiwi.examarchive.model.Module
@import ms.wiwi.examarchive.model.Semester
@import java.time.Year
@import java.util.List
@param List<Module> modules
@param List<String> errors

<form hx-post="/exams/upload"
      hx-target="#form-container"
      hx-swap="innerHTML"
      enctype="multipart/form-data">

    <label>${JteLocalizer.get("addExam.module")}:</label>
    <select name="module" aria-label="${JteLocalizer.get("addExam.module")}" required>
        <option hidden disabled selected value>${JteLocalizer.get("addExam.module")}</option>
        @for(Module module : modules)
            <option value="${module.moduleID()}">${module.name()}</option>
        @endfor
    </select>

    <label>${JteLocalizer.get("addExam.professor")}:</label>
    <div role="group">
        <input type="text" name="firstName" placeholder="${JteLocalizer.get("addExam.firstname")}"
               pattern="^[\p{L}\s\-]+$" required>
        <input type="text" name="lastName" placeholder="${JteLocalizer.get("addExam.lastname")}"
               pattern="^[\p{L}\s\-]+$" required>
    </div>

    <label>${JteLocalizer.get("addExam.year")}:</label>
    <input type="number" name="year" placeholder="2026"
           min="${Year.now().getValue() - 30}"
           max="${Year.now().getValue() + 30}" required>

    <label>${JteLocalizer.get("addExam.semester")}:</label>
    <select name="semester" aria-label="${JteLocalizer.get("addExam.semester")}" required>
        <option hidden disabled selected value>${JteLocalizer.get("addExam.semester")}</option>
        <option value="${Semester.WINTER.name()}">${JteLocalizer.get("addExam.wintersemester")}</option>
        <option value="${Semester.SUMMER.name()}">${JteLocalizer.get("addExam.summersemester")}</option>
    </select>

    <label>${JteLocalizer.get("addExam.file")}:</label>
    <input type="file" name="file" accept="application/pdf" required>

    <button type="submit">${JteLocalizer.get("addExam.submit")}</button>

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
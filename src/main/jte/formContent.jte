@import ms.wiwi.examarchive.services.JteLocalizer
@import ms.wiwi.examarchive.model.Semester
@import ms.wiwi.examarchive.model.Professor
@import java.time.Year
@import java.util.List

@param String firstName = ""
@param String lastName = ""
@param List<Professor> suggestions = null
@param boolean locked = false

@template.professorSection(firstName = firstName, lastName = lastName, suggestions = suggestions, locked = locked)

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
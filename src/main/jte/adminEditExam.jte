@import ms.wiwi.examarchive.model.Exam
@import ms.wiwi.examarchive.model.Module
@import ms.wiwi.examarchive.model.Professor
@import ms.wiwi.examarchive.model.Semester
@import java.util.List
@param Exam exam
@param Professor professor
@param List<Module> modules
<style>
    .form-action-bar {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        flex-wrap: wrap;
        margin-top: 1rem;
    }

    .form-action-group {
        display: flex;
        gap: 1rem;
        flex-wrap: wrap;
    }

    .form-action-bar button {
        width: auto;
    }

    .btn-danger {
        color: var(--pico-del-color);
        border-color: var(--pico-del-color);
    }

    .btn-danger:hover {
        background-color: var(--pico-del-color);
        color: white;
    }
</style>
<div class="container-fluid">
    <form id="exam-form">
        <select name="module">
            @for(Module module : modules)
                @if(module.moduleID().equals(exam.moduleID()))
                    <option value="${module.moduleID()}" selected>${module.name()}</option>
                @else
                    <option value="${module.moduleID()}">${module.name()}</option>
                @endif
            @endfor
        </select>
        <input type="number" name="year" value="${exam.year()}">
        <select name="semester">
            @if(exam.semester() == Semester.SUMMER)
                <option value="${Semester.SUMMER.name()}" selected>Sommer</option>
                <option value="${Semester.WINTER.name()}">Winter</option>
            @else
                <option value="${Semester.SUMMER.name()}">Sommer</option>
                <option value="${Semester.WINTER.name()}" selected>Winter</option>
            @endif
        </select>
        <div role="group">
            <input type="text" name="professor-firstname" value="${professor.firstName()}">
            <input type="text" name="professor-lastname" value="${professor.lastName()}">
        </div>
        <div class="form-action-bar">
            <div class="form-action-group">
                <button type="button"
                        hx-include="#exam-form"
                        hx-post="/admin/exams/editexam/${exam.examID()}"
                        hx-target="#tab-content">Speichern
                </button>
                <button type="button"
                        hx-get="/admin/exams"
                        hx-target="#tab-content"
                        class="outline contrast">Abbrechen
                </button>
            </div>

            <button type="button"
                    class="outline btn-danger"
                    hx-post="/admin/exams/deleteexam/${exam.examID()}"
                    hx-target="#tab-content">Löschen
            </button>
        </div>
    </form>
</div>

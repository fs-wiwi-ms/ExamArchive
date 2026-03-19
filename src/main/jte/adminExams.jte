@import ms.wiwi.examarchive.admin.AdminExamListDTO
@import ms.wiwi.examarchive.model.Module
@import java.text.SimpleDateFormat
@import java.util.Date
@import java.util.List
@param List<AdminExamListDTO> pendingExams
@param List<AdminExamListDTO> acceptedExams
@param List<Module> modules
@if(!pendingExams.isEmpty())
<div style="background-color: #dddddd; padding: 10px;">
    <h3>Ausstehende Klausuren</h3>
    <table>
        <thead>
        <tr>
            <th scope="col">Modul</th>
            <th scope="col">Jahr</th>
            <th scope="col">Semester</th>
            <th scope="col">Hochgeladen</th>
            <th scope="col">Prof</th>
            <th scope="col">User</th>
            <th scope="col">Status</th>
            <th scope="col">Ansehen</th>
        </tr>
        </thead>
        <tbody>
        @for(AdminExamListDTO exam : pendingExams)
            <tr>
                <th scope="row">${exam.module().name()}</th>
                <td>${exam.exam().year()}</td>
                <td>${exam.exam().semester().getReadableSemester(exam.exam().year())}</td>
                <td>${new SimpleDateFormat("dd.MM.yyyy").format(Date.from(exam.exam().uploadDate()))}</td>
                <td>${exam.professor().firstName() + " " + exam.professor().lastName()}</td>
                @if(exam.user() != null)
                    <td>${exam.user().firstname() + " " + exam.user().lastname()}</td>
                @else
                    <td>Nutzer gelöscht</td>
                @endif
                <td>
                    <div role="group">
                        <button hx-post="/admin/exams/acceptexam" value="${exam.exam().examID()}" name="id" hx-target="#tab-content"><small>Annehmen</small></button>
                        <button class="outline" hx-post="/admin/exams/declineexam" value="${exam.exam().examID()}" name="id" hx-target="#tab-content"><small>Ablehnen</small></button>
                    </div>
                </td>
                <td><a href="/exams/view/${exam.exam().examID()}">
                        <button>Ansehen</button>
                    </a>
                </td>
            </tr>
        @endfor
        </tbody>
    </table>
</div>
@endif
@for(Module module : modules)
    <details>
        <summary role="button">${module.name()}</summary>
        <button style="color: red" hx-post="/admin/exams/deletemodule/" value="${module.moduleID()}" name="id" hx-target="#tab-content">Modul löschen</button>
        <table>
            <thead>
            <tr>
                <th scope="col">Modul</th>
                <th scope="col">Jahr</th>
                <th scope="col">Semester</th>
                <th scope="col">Hochgeladen</th>
                <th scope="col">Prof</th>
                <th scope="col">User</th>
                <th scope="col">Status</th>
                <th scope="col">Ansehen</th>
            </tr>
            </thead>
            <tbody>
            @for(AdminExamListDTO exam : acceptedExams.stream().filter(ex -> ex.module().moduleID().equals(module.moduleID())).toList())
                <tr>
                    <th scope="row">${exam.module().name()}</th>
                    <td>${exam.exam().year()}</td>
                    <td>${exam.exam().semester().getReadableSemester(exam.exam().year())}</td>
                    <td>${new SimpleDateFormat("dd.MM.yyyy").format(Date.from(exam.exam().uploadDate()))}</td>
                    <td>${exam.professor().firstName() + " " + exam.professor().lastName()}</td>
                    @if(exam.user() != null)
                        <td>${exam.user().firstname() + " " + exam.user().lastname()}</td>
                    @else
                        <td>Nutzer gelöscht</td>
                    @endif
                    <td>Online</td>
                    <td><a href="/exams/view/${exam.exam().examID()}">
                            <button>Ansehen</button>
                        </a></td>
                </tr>
            @endfor
            </tbody>
        </table>
    </details>
@endfor
<div class="container">
    <form>
        <fieldset role="group">
            <input name="name" type="text" placeholder="Modul hinzufügen" pattern="^[a-zA-Z0-9ÄÖÜäöüß]+( [a-zA-Z0-9ÄÖÜäöüß]+)*$"/>
            <input type="submit" value="Hinzufügen" hx-post="/admin/exams/addmodule" hx-target="#tab-content"/>
        </fieldset>
    </form>
</div>
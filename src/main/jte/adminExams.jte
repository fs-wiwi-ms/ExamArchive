@import ms.wiwi.examarchive.admin.AdminExamListDTO
@import ms.wiwi.examarchive.admin.ModuleDegreeDTO
@import ms.wiwi.examarchive.model.Degree
@import ms.wiwi.examarchive.model.KeyWord
@import ms.wiwi.examarchive.model.Module
@import java.text.SimpleDateFormat
@import java.util.Date
@import java.util.List
@param List<AdminExamListDTO> pendingExams
@param List<AdminExamListDTO> acceptedExams
@param List<Module> modules
@param List<KeyWord> keyWords
@param List<Degree> degrees
@param List<ModuleDegreeDTO> degreesAndModules
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
    <details id="module-${module.moduleID()}">
        <summary role="button">${module.name()}</summary>
        <button style="color: red" hx-post="/admin/exams/deletemodule/" value="${module.moduleID()}" name="id" hx-target="#tab-content">Modul löschen</button>
        <p>Studiengänge:</p>
        @for(Degree degree : degrees)
            !{
                boolean isAssigned = degreesAndModules.stream()
                        .filter(dto -> dto.moduleID().equals(module.moduleID()))
                        .flatMap(dto -> dto.degrees().stream())
                        .anyMatch(d -> d.degreeID().equals(degree.degreeID()));
            }
            <label>
                <input type="checkbox"
                       name="assigned"
                       value="true"
                       checked="${isAssigned}"
                       hx-post="/admin/exams/updatemoduledegree?moduleID=${module.moduleID()}&degreeID=${degree.degreeID()}"
                       hx-trigger="change">
                ${degree.name()}
            </label>
        @endfor
        <p>Suchbegriffe:</p>
        <ul>
            @for(KeyWord keyword : keyWords.stream().filter(kw -> kw.moduleID().equals(module.moduleID())).toList())
                <li id="keyword-${keyword.keyword()}" style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem;">
                    <span>${keyword.keyword()}</span>
                    <button type="button"
                            class="outline btn-danger"
                            style="width: auto; padding: 0.15rem 0.5rem; font-size: 0.8rem; margin-bottom: 0;"
                            hx-post="/admin/exams/deletekeyword/${keyword.moduleID()}/${keyword.keyword()}"
                            hx-swap="delete"
                            hx-target="#keyword-${keyword.keyword()}">Löschen
                    </button>
                </li>
            @endfor
        </ul>
        <form id="keyword-form-${module.moduleID()}">
            <div role="group">
                <input type="text" name="keyword" placeholder="Suchbegriff" required>
                <button type="button"
                hx-include="#keyword-form-${module.moduleID()}"
                hx-target="#tab-content"
                hx-post="/admin/exams/addkeyword/${module.moduleID()}/">Hinzufügen</button>
            </div>
        </form>
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
                    <td>Online
                        <button
                                class="outline"
                                hx-get="/admin/exams/edit/${exam.exam().examID()}"
                                hx-target="#module-${module.moduleID()}">Edit
                        </button>
                    </td>
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
    <ul>
        @for(Degree degree : degrees)
            <li>
                <span>${degree.name()}</span>
                <button type="button"
                        class="secondary"
                        hx-post="/admin/exams/deletedegree/${degree.degreeID()}"
                        hx-target="#tab-content"
                >Löschen
                </button>
            </li>
        @endfor
    </ul>
    <form>
        <fieldset role="group">
            <input name="name" type="text" placeholder="Studiengang hinzufügen"
                   pattern="^[a-zA-Z0-9ÄÖÜäöüß]+( [a-zA-Z0-9ÄÖÜäöüß]+)*$"/>
            <input type="submit" value="Hinzufügen" hx-post="/admin/exams/adddegree" hx-target="#tab-content"/>
        </fieldset>
    </form>
</div>
@import ms.wiwi.examarchive.JteLocalizer
@import ms.wiwi.examarchive.ProfessorExamDTO
@import java.text.SimpleDateFormat
@import java.util.Date
@import java.util.List
@param List<ProfessorExamDTO> exams
@if(!exams.isEmpty())
    @for(ProfessorExamDTO exam : exams)
        <article class="exam-card">
            <header class="flex-responsive">
                <h3>${exam.exam().semester().getReadableSemester(exam.exam().year())}</h3>
                <a href="#" role="button" class="outline download-btn full-width-mobile">Download</a>
            </header>

            <div class="flex-responsive">
                <small>${exam.professor().getFullName()} &bull; ${JteLocalizer.get("module.year")}: ${exam.exam().year()}</small>
                <small>
                    ${JteLocalizer.get("module.upload")}
                    <time datetime="${new SimpleDateFormat("yyyy-MM-dd").format(Date.from(exam.exam().uploadDate()))}">
                        ${new SimpleDateFormat("dd.MM.yyyy").format(Date.from(exam.exam().uploadDate()))}
                    </time>
                </small>
            </div>
        </article>
    @endfor
@else
    <h4 style="text-align: center; margin-top: 2rem;">${JteLocalizer.get("module.no-exam-found")}</h4>
@endif

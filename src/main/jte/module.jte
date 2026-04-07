@import ms.wiwi.examarchive.services.JteLocalizer
@import ms.wiwi.examarchive.ProfessorExamDTO
@import ms.wiwi.examarchive.model.Module
@import ms.wiwi.examarchive.model.Professor
@import java.util.List
@param List<ProfessorExamDTO> exams
@param Module module
@param List<Professor> professors
<!Doctype html>
<html lang="de" data-theme="light">
<head>
    <script src="/assets/htmx.js"></script>
    <link rel="stylesheet" href="/assets/pico.min.css">
    <link rel="apple-touch-icon" sizes="180x180" href="/assets/apple-touch-icon.png">
    <link rel="icon" type="image/png" sizes="32x32" href="/assets/favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="/assets/favicon-16x16.png">
    <link rel="manifest" href="/assets/site.webmanifest">
    <title>${JteLocalizer.get("head.title")}</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body {
            display: flex;
            flex-direction: column;
            min-height: 100vh;
        }
        #main-content {
            flex: 1;
            padding: 2rem var(--pico-spacing)
        }
        .flex-responsive {
            display: flex;
            flex-direction: column;
            align-items: flex-start;
            gap: 0.75rem;
        }
        @media (min-width: 768px) {
            .flex-responsive {
                flex-direction: row;
                justify-content: space-between;
                align-items: center;
                gap: 1rem;
            }
        }
        .module-header hgroup,
        .exam-card header,
        .exam-card h3 {
            margin-bottom: 0;
        }
        .full-width-mobile {
            width: 100%;
        }
        @media (min-width: 768px) {
            .full-width-mobile {
                width: auto;
            }
        }
        .filter-dropdown {
            margin-bottom: 0;
        }
        .filter-dropdown ul {
            padding: 0.5rem 1rem;
            width: max-content;
            max-width: 100%;
        }
        .download-btn {
            text-align: center;
        }
    </style>
</head>
<body>
@template.header()

<main id="main-content" class="container">
    <header class="flex-responsive module-header" style="margin-bottom: 1.5rem;">
        <hgroup>
            <h2>${module.name()}</h2>
            <p>${JteLocalizer.get("module.description")}</p>
        </hgroup>

        <div class="full-width-mobile">
            <form hx-post="/exams/module/${module.moduleID()}/filter"
                  hx-target="#results"
                  hx-trigger="change">
                <details class="dropdown filter-dropdown">
                    <summary role="button" class="secondary outline">${JteLocalizer.get("module.choose-prof")}</summary>
                    <ul>
                        @for(Professor prof : professors)
                            @if(!prof.lastName().equals("Unbekannt"))
                                <li>
                                    <label>
                                        <input type="checkbox" name="professorFilter" value="${prof.professorID()}"/>
                                        ${prof.getFullName()}
                                    </label>
                                </li>
                            @endif
                        @endfor
                    </ul>
                </details>
            </form>
        </div>
    </header>
    <hr />
    <div id="results">
        @template.examCards(exams)
    </div>
</main>

@template.footer()
</body>
</html>
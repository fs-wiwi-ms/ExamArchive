@import java.util.List
@import ms.wiwi.examarchive.model.Professor
@import ms.wiwi.examarchive.services.JteLocalizer

@param List<Professor> professors
<!Doctype html>
<html lang="de" data-theme="light">
<head>
    <link rel="stylesheet" href="/assets/pico.min.css">
    <script src="/assets/htmx.js"></script>
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
            display: flex;
            align-items: center;
            padding: 2rem 0;
        }

        .shiny-title {
            background: linear-gradient(135deg, var(--pico-primary), #5a1ce1);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            display: inline-block;
            margin-bottom: 0.25rem;
        }

        .input-year-wrapper {
            max-width: 250px;
        }

        .form-actions {
            display: flex;
            justify-content: flex-end;
            margin-top: 1.5rem;
        }

        .narrow-form-container {
            max-width: 750px;
            margin-left: auto;
            margin-right: auto;
        }
    </style>
</head>
<body>
@template.header()

<main id="main-content">
    <div class="container narrow-form-container">
        <article>
            <header>
                <h2 class="shiny-title">✨ Exam AI</h2>
                <p style="color: var(--pico-muted-color); margin-bottom: 0;">
                    ${JteLocalizer.get("examai.filterDescription")}
                </p>
            </header>
            <main id="filter-form">
                <form>
                    <fieldset>
                        <legend><strong>${JteLocalizer.get("module.choose-prof")}</strong></legend>
                        @for(Professor prof : professors)
                            <label>
                                <input type="checkbox" name="profid" value="${prof.professorID()}"/>
                                ${prof.getFullName()}
                            </label>
                        @endfor
                    </fieldset>
                    <hr>
                    <fieldset>
                        <legend><strong>${JteLocalizer.get("examai.advancedFilter")}</strong></legend>
                        <div class="input-year-wrapper">
                            <label for="year-input">
                                ${JteLocalizer.get("examai.maxyear")}
                            </label>
                            <input type="number"
                                   id="year-input"
                                   name="year"
                                   placeholder="z.B. 2023"
                                   min="1990"
                                   max="2099"
                                   aria-label="Jahr">
                        </div>
                    </fieldset>
                    <div class="form-actions">
                        <button type="submit">Klausur generieren</button>
                    </div>

                </form>
            </main>

            <footer>
                <small style="color: var(--pico-muted-color); font-size: 0.85em; text-align: center; display: block;">
                    ${JteLocalizer.get("examai.note")}
                </small>
            </footer>

        </article>
    </div>
</main>

@template.footer()
</body>
</html>
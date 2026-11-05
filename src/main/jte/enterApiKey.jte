@import ms.wiwi.examarchive.services.JteLocalizer

@param String moduleid = null
@param String error = null
<!Doctype html>
<html lang="de" data-theme="light">
<head>
    <link rel="stylesheet" href="/assets/pico.min.css">
    <script src="/webjars/htmx.org/2.0.7/dist/htmx.min.js"></script>
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
                <h2>${JteLocalizer.get("apikey.title")}</h2>
                <p style="color: var(--pico-muted-color); margin-bottom: 0;">
                    ${JteLocalizer.get("apikey.description")}
                </p>
            </header>
            <main id="filter-form">
                @if(error != null)
                    <article class="error-container" style="border-color: var(--pico-del-color); margin-top: 0; margin-bottom: 1rem;">
                        <header style="background-color: var(--pico-del-color); color: white;">Error</header>
                        <p style="margin-bottom: 0;">${error}</p>
                    </article>
                @endif
                <form action="/exams/apikey" method="post">
                    <label for="apikey-input">${JteLocalizer.get("apikey.label")}</label>
                    <input type="text"
                           id="apikey-input"
                           name="apikey"
                           autocomplete="off"
                           spellcheck="false"
                           style="font-family: monospace;"
                           aria-label="${JteLocalizer.get("apikey.label")}">
                    @if(moduleid != null && !moduleid.isBlank())
                        <input type="hidden" name="moduleid" value="${moduleid}">
                    @endif
                    <div class="form-actions">
                        <button type="submit">${JteLocalizer.get("apikey.submit")}</button>
                    </div>
                </form>
            </main>

            <footer>
                <small style="color: var(--pico-muted-color); font-size: 0.85em; text-align: center; display: block;">
                    ${JteLocalizer.get("apikey.encryptedNote")}
                </small>
            </footer>

        </article>
    </div>
</main>

@template.footer()
</body>
</html>

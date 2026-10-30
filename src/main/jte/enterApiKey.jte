@import ms.wiwi.examarchive.services.JteLocalizer

@param String moduleid = null
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
    </style>
</head>
<body>
@template.header()

<main id="main-content">
    <div class="container narrow-form-container">
        <article>
            <header>
                <h2>UniGPT API-Key</h2>
                <p style="color: var(--pico-muted-color); margin-bottom: 0;">
                    ${JteLocalizer.get("test")}
                </p>
            </header>
            <main id="filter-form">
                <form>

                </form>
            </main>

            <footer>
                <small style="color: var(--pico-muted-color); font-size: 0.85em; text-align: center; display: block;">
                    ${JteLocalizer.get("api keys are safed encrypted")}
                </small>
            </footer>

        </article>
    </div>
</main>

@template.footer()
</body>
</html>
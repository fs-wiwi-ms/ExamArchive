@import ms.wiwi.examarchive.services.JteLocalizer

@param String message
@param int status = 404
<!Doctype html>
<html lang="de" data-theme="light">
<head>
    <link rel="stylesheet" href="/assets/pico.min.css">
    <script src="/assets/htmx.js"></script>
    <link rel="apple-touch-icon" sizes="180x180" href="/assets/apple-touch-icon.png">
    <link rel="icon" type="image/png" sizes="32x32" href="/assets/favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="/assets/favicon-16x16.png">
    <link rel="manifest" href="/assets/site.webmanifest">
    <title>${status}</title>
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
    <div class="container">
        <article style="max-width: 500px; margin: 0 auto; text-align: center;">
            <header>
                <h1 style="color: var(--pico-del-color);">${status}</h1>
            </header>
            <main>
                <p>${message}</p>
            </main>
            <a href="/"><button class="secondary">${JteLocalizer.get("error.back")}</button></a>
        </article>
    </div>
</main>

@template.footer()
</body>
</html>
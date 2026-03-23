@import ms.wiwi.examarchive.JteLocalizer
<!Doctype html>
<html lang="de" data-theme="light">
<head>
    <link rel="stylesheet" href="assets/pico.min.css">
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
    </style>
</head>
<body>
@template.header()
@template.motd()
<main id="main-content">
    <div class="container">
        <article style="max-width: 500px; margin: 0 auto;">
            <header>
                <h3>${JteLocalizer.get("index.welcome")}</h3>
            </header>
            <main>
                <p>${JteLocalizer.get("index.login.info")}</p>
            </main>
            <a href="/login/user"><button>${JteLocalizer.get("index.login.button")}</button></a>
            <footer style="text-align: center;">
                <small style="color: #525f7a">${JteLocalizer.get("index.login.hint")}<a style="color: #525f7a; text-decoration: none" href="mailto:klausurarchiv@fachschaft-wiwi.ms"> Klausurarchiv@fachschaft-wiwi.ms</a></small>
            </footer>
        </article>
    </div>
</main>
@template.footer()
</body>
</html>
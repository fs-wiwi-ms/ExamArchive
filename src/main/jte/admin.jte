@import ms.wiwi.examarchive.JteLocalizer
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
            padding: 2rem 0;
        }
    </style>
</head>
<body>
@template.header()

<main id="main-content" class="container">
    <h1>Admin Interface</h1>
    <div role="group">
        <button hx-get="/admin/exams" hx-target="#tab-content">Klausuren</button>
        <button hx-get="/admin/users" hx-target="#tab-content">Accounts</button>
        <button hx-get="/admin/settings" hx-target="#tab-content">Sonstiges</button>
    </div>
    <hr>
    <div id="tab-content" >

    </div>
</main>

@template.footer()
</body>
</html>
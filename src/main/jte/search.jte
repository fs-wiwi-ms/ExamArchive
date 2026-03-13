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

        .upload-cta {
            display: flex;
            justify-content: flex-end;
            align-items: center;
            gap: 1rem;
            margin-bottom: 1rem;
        }

        .upload-cta-text {
            font-size: 0.875rem;
            color: var(--pico-muted-color);
        }

        .upload-cta-button {
            padding: 0.25rem 0.75rem;
            font-size: 0.875rem;
            border-radius: 2rem;
        }

        .search-form {
            margin-bottom: 1.5rem;
        }

        .filters-container {
            margin-bottom: 2rem;
        }
    </style>
</head>
<body>
@template.header()

<main id="main-content">
    <div class="container">

        <div class="upload-cta">
            <span class="upload-cta-text">${JteLocalizer.get("search.exam-missing")}</span>
            <a href="/exams/upload" role="button" class="outline secondary upload-cta-button">
                ${JteLocalizer.get("search.upload-exam")}
            </a>
        </div>

        <form class="search-form">
            <input type="search"
                   placeholder="${JteLocalizer.get("search.placeholder")}"
                   aria-placeholder="${JteLocalizer.get("search.placeholder")}"
                   name="search"
                   autocomplete="off"
                   hx-post="/exams/search"
                   hx-trigger="input changed delay:300ms, keyup[key=='Enter'], load"
                   hx-target="#search-results"
                   hx-include="[name='search']">
        </form>

        <div id="filters" class="filters-container">
        </div>

        <div id="search-results">

        </div>

    </div>
</main>

@template.footer()
</body>
</html>
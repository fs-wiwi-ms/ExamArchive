@import ms.wiwi.examarchive.services.JteLocalizer
@import ms.wiwi.examarchive.model.Degree
@import java.util.List

@param List<Degree> degrees

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

        .search-container {
            display: flex;
            gap: 1rem;
            align-items: flex-start;
        }

        .search-container > input[type="search"] {
            flex: 1;
            margin-bottom: 0;
        }

        details.dropdown.filter-dropdown ul {
            right: 0;
            left: auto;
            min-width: max-content;
            max-width: calc(100vw - 2rem);
            z-index: 10;
        }
    </style>
</head>
<body>
@template.header()
@template.motd()

<main id="main-content">
    <div class="container">

        <div class="upload-cta">
            <span class="upload-cta-text">${JteLocalizer.get("search.exam-missing")}</span>
            <a href="/exams/upload" role="button" class="outline secondary upload-cta-button">
                ${JteLocalizer.get("search.upload-exam")}
            </a>
        </div>

        <form class="search-form"
              hx-post="/exams/search"
              hx-target="#search-results"
              hx-trigger="input delay:300ms from:[name='search'], change from:[name='degreeFilter']">

            <div class="search-container">
                <input type="search"
                       placeholder="${JteLocalizer.get("search.placeholder")}"
                       aria-placeholder="${JteLocalizer.get("search.placeholder")}"
                       name="search"
                       autocomplete="off">

                <details class="dropdown filter-dropdown">
                    <summary role="button" class="secondary outline">${JteLocalizer.get("search.filter")}</summary>
                    <ul>
                        @for(Degree degree : degrees)
                            <li>
                                <label>
                                    <input type="checkbox" name="degreeFilter" value="${degree.degreeID()}"/>
                                    ${degree.name()}
                                </label>
                            </li>
                        @endfor
                    </ul>
                </details>
            </div>
        </form>

        <div id="filters" class="filters-container">
        </div>

        <div id="search-results">
            <progress
            hx-trigger="intersect once"
            hx-target="#search-results"
            hx-post="/exams/search"></progress>
        </div>

    </div>
</main>

@template.footer()
</body>
</html>
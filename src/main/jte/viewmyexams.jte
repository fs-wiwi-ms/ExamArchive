@import ms.wiwi.examarchive.model.User
@import ms.wiwi.examarchive.model.UserExam
@import ms.wiwi.examarchive.services.JteLocalizer
@import java.util.List
@import java.util.Map
@import java.util.TreeMap
@import java.util.ArrayList
@import java.util.Collections
@import java.time.LocalDate
@import java.time.ZoneId
@import java.time.format.DateTimeFormatter
@param User user
@param List<UserExam> userexams
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
            align-items: flex-start;
            padding: 2rem 0;
        }
        .container {
            width: 100%;
        }

        summary.semester-summary {
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: 0.35rem 0.5rem;
            cursor: pointer;
        }

        .table-responsive {
            width: 100%;
            overflow-x: auto;
            -webkit-overflow-scrolling: touch;
            margin-top: 1rem;
        }

        .exam-title-text {
            font-weight: 600;
            word-break: break-word;
            hyphens: auto;
            line-height: 1.3;
        }

        .exam-id-sub {
            display: block;
            font-size: 0.75rem;
            color: var(--pico-muted-color);
            font-weight: normal;
            font-family: monospace;
            margin-top: 0.15rem;
        }

        .btn-download {
            display: inline-flex;
            align-items: center;
            gap: 0.35rem;
            padding: 0.35rem 0.65rem;
            font-size: 0.85rem;
            margin-bottom: 0;
            white-space: nowrap;
        }

        @media (max-width: 576px) {
            #main-content {
                padding: 1rem 0;
            }
            article {
                padding: 1rem !important;
            }
            table th, table td {
                padding: 0.5rem 0.25rem !important;
                font-size: 0.875rem;
            }
            .date-col {
                font-size: 0.8rem;
            }
            .btn-text {
                display: none;
            }
        }
        @media (min-width: 400px) {
            .btn-text {
                display: inline;
            }
        }
    </style>
</head>
<body>
@template.header()
@template.motd()
<main id="main-content">
    <div class="container">
        !{
            var zone = ZoneId.systemDefault();
            var today = LocalDate.now(zone);
            int currentMonth = today.getMonthValue();
            int currentYear = today.getYear();
            int currentSemKey = (currentMonth >= 4 && currentMonth <= 9)
                    ? (currentYear * 10 + 1)
                    : ((currentMonth >= 10 ? currentYear : currentYear - 1) * 10 + 2);

            var dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(zone);
            Map<Integer, List<UserExam>> groupedExams = new TreeMap<>(Collections.reverseOrder());
            if (userexams != null) {
                for (var exam : userexams) {
                    if (exam.creationDate() != null) {
                        var dateTime = exam.creationDate().toInstant().atZone(zone);
                        int m = dateTime.getMonthValue();
                        int y = dateTime.getYear();
                        int semKey = (m >= 4 && m <= 9)
                                ? (y * 10 + 1)
                                : ((m >= 10 ? y : y - 1) * 10 + 2);
                        groupedExams.computeIfAbsent(semKey, k -> new ArrayList<>()).add(exam);
                    }
                }
                for (var list : groupedExams.values()) {
                    list.sort((a, b) -> b.creationDate().compareTo(a.creationDate()));
                }
            }
        }
        <header style="margin-bottom: 2rem;">
            <hgroup>
                <h1>${JteLocalizer.get("userexams.greeting")} ${user.firstname()}!</h1>
            </hgroup>
            <article style="border-left: 4px solid var(--pico-primary); padding: 1rem 1.25rem; margin-top: 1rem; margin-bottom: 0;">
                <p style="margin-bottom: 0.4rem;">${JteLocalizer.get("userexams.description")}</p>
                <small style="display: block; opacity: 0.85; line-height: 1.5;">
                    <strong>${JteLocalizer.get("userexams.disclaimer.title")}:</strong> ${JteLocalizer.get("userexams.disclaimer.text")}
                </small>
            </article>
        </header>
        @if(groupedExams.isEmpty())
            <article style="text-align: center; padding: 3rem 1.5rem;">
                <h3>${JteLocalizer.get("userexams.empty.title")}</h3>
                <p style="color: var(--pico-muted-color);">${JteLocalizer.get("userexams.empty.text")}</p>
            </article>
        @else
            @for(var entry : groupedExams.entrySet())
                !{
                    int key = entry.getKey();
                    var examsInSem = entry.getValue();
                    boolean isSummer = (key % 10 == 1);
                    int semYear = key / 10;
                    String semesterName = isSummer
                            ? JteLocalizer.lookup("addExam.summersemester") + " " + semYear
                            : JteLocalizer.lookup("addExam.wintersemester") + " " + semYear + "/" + (semYear + 1);
                    boolean isCurrent = (key == currentSemKey);
                }

                <article style="margin-bottom: 1.5rem;">
                    <details open="${isCurrent}">
                        <summary class="semester-summary">
                            <span><strong>${semesterName}</strong></span>
                            @if(isCurrent)
                                <mark style="font-size: 0.75rem; padding: 0.15rem 0.55rem; border-radius: 9999px;">
                                    ${JteLocalizer.get("userexams.badge.current")}
                                </mark>
                            @endif
                            <small style="color: var(--pico-muted-color);">
                                (${examsInSem.size()} ${examsInSem.size() == 1 ? JteLocalizer.get("userexams.count.singular") : JteLocalizer.get("userexams.count.plural")})
                            </small>
                        </summary>
                        <div class="table-responsive">
                            <table class="striped" style="margin-bottom: 0;">
                                <thead>
                                <tr>
                                    <th scope="col" style="width: auto;">${JteLocalizer.get("userexams.count.singular")}</th>
                                    <th scope="col" style="width: 100px;">${JteLocalizer.get("userexams.table.date")}</th>
                                    <th scope="col" style="width: 1%; text-align: right; white-space: nowrap;">${JteLocalizer.get("userexams.table.action")}</th>
                                </tr>
                                </thead>
                                <tbody>
                                @for(var exam : examsInSem)
                                    !{
                                        String rawId = exam.fileID() != null ? exam.fileID() : "";
                                        String cleanTitle = rawId.replaceAll("-[0-9a-fA-F]{3,8}-[0-9a-fA-F]{4}.*$", "");
                                        boolean hasUuid = !cleanTitle.equals(rawId);
                                        String uuidShort = hasUuid && rawId.length() >= 8
                                                ? rawId.substring(rawId.length() - 8)
                                                : "";
                                    }
                                    <tr>
                                        <th scope="row" style="vertical-align: middle;">
                                            <div class="exam-title-text">${cleanTitle}</div>
                                            @if(!uuidShort.isEmpty())
                                                <small class="exam-id-sub">#${uuidShort}</small>
                                            @endif
                                        </th>
                                        <td class="date-col" style="vertical-align: middle; white-space: nowrap;">
                                            ${dateFormatter.format(exam.creationDate().toInstant())}
                                        </td>
                                        <td style="text-align: right; vertical-align: middle; white-space: nowrap;">
                                            <a href="/exams/usercontent/download/${exam.id()}"
                                               role="button"
                                               class="outline btn-download"
                                               title="${JteLocalizer.get("userexams.download")}">
                                                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                                                    <polyline points="7 10 12 15 17 10"></polyline>
                                                    <line x1="12" y1="15" x2="12" y2="3"></line>
                                                </svg>
                                                <span class="btn-text">${JteLocalizer.get("userexams.download")}</span>
                                            </a>
                                        </td>
                                    </tr>
                                @endfor
                                </tbody>
                            </table>
                        </div>
                    </details>
                </article>
            @endfor
        @endif
    </div>
</main>
@template.footer()
</body>
</html>
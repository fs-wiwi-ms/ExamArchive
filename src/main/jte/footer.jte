@import ms.wiwi.examarchive.services.JteLocalizer
<style>
    footer {
        background-color: #10375B;
    }

    footer li,
    footer a {
        color: #ffffff;
        text-decoration-color: #ffffff;
    }

    @media (max-width: 768px) {
        footer nav,
        footer nav ul {
            flex-wrap: wrap;
            justify-content: center;
        }
        footer nav ul {
            margin: 0;
        }
    }
</style>

<footer>
    <nav class="container">
        <ul>
            <li><strong>&copy;Fachschaft WiWi</strong></li>
        </ul>
        <ul>
            <li><a href="https://fachschaft-wiwi.ms/imprint/" class="contrast">${JteLocalizer.get("footer.legal")}</a></li>
            <li><a href="https://fachschaft-wiwi.ms/privacy/" class="contrast">${JteLocalizer.get("footer.privacy")}</a></li>
            <li><a href="https://fachschaft-wiwi.ms/" class="contrast">${JteLocalizer.get("footer.website")}</a></li>
            <li><a href="https://github.com/fs-wiwi-ms/ExamArchive/" class="contrast">${JteLocalizer.get("footer.code")}</a></li>
        </ul>
    </nav>
</footer>
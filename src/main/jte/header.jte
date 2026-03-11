@import ms.wiwi.examarchive.JteLocalizer
<style>
    #header {
        background-color: #10375B;
    }
</style>

<header id="header">
    <nav class="container">
        <ul>
            <li><img style="height: 2rem; width: auto;" src="/assets/fs_logo_white.svg" alt="Logo"></li>
            <li><strong style="color: #ffffff">${JteLocalizer.get("header.title")}</strong></li>
        </ul>
        <ul>
            <li>
                <details class="dropdown">
                    <summary>☰</summary> <ul dir="rtl">
                        <li><a href="mailto:klausurarchiv@fachschaft-wiwi.ms">${JteLocalizer.get("header.contact")}</a></li>
                        <li><a href="https://fachschaft-wiwi.ms/">${JteLocalizer.get("header.website")}</a></li>
                        <li><a href="/login/admin">${JteLocalizer.get("header.admin")}</a></li>
                    </ul>
                </details>
            </li>
        </ul>
    </nav>
</header>
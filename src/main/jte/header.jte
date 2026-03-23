@import ms.wiwi.examarchive.JteLocalizer
<style>
    #header {
        background-color: #10375B;
    }
    #headerlink {
        text-decoration: none;
    }
</style>

<header id="header">
    <nav class="container">
        <ul>
            <li><img style="height: 2rem; width: auto;" src="/assets/fs_logo_white.svg" alt="Logo"></li>
            <li><a href="/" id="headerlink"><strong style="color: #ffffff">${JteLocalizer.get("header.title")}</strong></a></li>
        </ul>
        <ul>
            <li>
                <details class="dropdown">
                    <summary>☰</summary> <ul dir="rtl">
                        <li hx-trigger="intersect once"
                            hx-get="/dropdown"
                            hx-target=".dropdown"
                            hx-swap="innerHTML"><progress></progress></li>
                    </ul>
                </details>
            </li>
        </ul>
    </nav>
</header>
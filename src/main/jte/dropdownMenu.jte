@import ms.wiwi.examarchive.services.JteLocalizer
@param boolean loggedIn
@param String lang
<summary>☰</summary>
<ul dir="rtl">
    <li><a href="mailto:klausurarchiv@fachschaft-wiwi.ms">${JteLocalizer.get("header.contact")}</a></li>
    <li><a href="https://fachschaft-wiwi.ms/">${JteLocalizer.get("header.website")}</a></li>
    @if(loggedIn)
    <li><a href="/logout">Logout</a></li>
    @else
    <li><a href="/login/admin">${JteLocalizer.get("header.admin")}</a></li>
    @endif
    @if(lang.equals("german"))
        <li><a href="/dropdown?language=english">Switch to English</a></li>
    @else
        <li><a href="/dropdown?language=german">Switch to German</a></li>
    @endif
</ul>

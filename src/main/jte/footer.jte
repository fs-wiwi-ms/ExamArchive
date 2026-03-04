@import ms.wiwi.examarchive.JteLocalizer
<footer class="container">
    <nav>
        <ul>
            <li><strong>&copy;Fachschaft WiWi</strong></li>
        </ul>
        <ul>
            <li><a href="https://fachschaft-wiwi.ms/imprint/" class="contrast">${JteLocalizer.get("footer.legal")}</a></li>
            <li><a href="https://fachschaft-wiwi.ms/privacy/" class="contrast">${JteLocalizer.get("footer.privacy")}</a></li>
            <li><a href="https://fachschaft-wiwi.ms/" class="contrast">${JteLocalizer.get("footer.website")}</a></li>
        </ul>
    </nav>
</footer>
<style>
   @media (max-width: 768px) {
       footer nav {
           flex-wrap: wrap;
           justify-content: center;
       }

       footer nav ul {
           flex-wrap: wrap;
           justify-content: center;
           margin: 0;
       }
   }
</style>
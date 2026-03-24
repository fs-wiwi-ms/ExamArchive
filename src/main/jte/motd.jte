@import ms.wiwi.examarchive.services.MotdService
@if(MotdService.getMotd() != null)
    <div class="container motd-container">
        <article class="motd-card">
            $unsafe{MotdService.getMotd().text()}
        </article>
    </div>
@endif
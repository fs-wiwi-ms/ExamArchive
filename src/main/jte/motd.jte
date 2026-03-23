@import ms.wiwi.examarchive.MotdService
@if(MotdService.getMotd() != null)
    <div class="container motd-container">
        <article class="motd-card">
            $unsafe{MotdService.getMotd().text()}
        </article>
    </div>
@endif
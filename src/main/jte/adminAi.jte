@param int semesterToken
@param int semesterExamCount
@param int weeklyToken
@param int weeklyExamCount
@param double currentAzureCredit
@param double currentAzureAmount
@param int weeklyTokenLimit
<div class="container">
    <article>
        <header>
            <h3>Statistiken</h3>
            <small>Hier ist eine Übersicht über die aktuelle Nutzung des KI-Assistenten ExamAI</small>
        </header>
        <main>
            <div class="grid">
                <article style="text-align: center; padding: 1.5rem;">
                    <hgroup style="margin-bottom: 0;">
                        <h2 style="margin-bottom: 0;">${weeklyToken}</h2>
                        <p>Token/Woche</p>
                        <meter value="${weeklyToken}" min="0" max="${weeklyTokenLimit}">${weeklyToken}</meter>
                    </hgroup>
                </article>
                <article style="text-align: center; padding: 1.5rem;">
                    <hgroup style="margin-bottom: 0;">
                        <h2 style="margin-bottom: 0;">${weeklyExamCount}</h2>
                        <p>Klausuren/Woche</p>
                    </hgroup>
                </article>
                <article style="text-align: center; padding: 1.5rem;">
                    <hgroup style="margin-bottom: 0;">
                        <h2 style="margin-bottom: 0;">${String.format(java.util.Locale.GERMANY, "%,d", semesterToken)}</h2>
                        <p>Token/Semester</p>
                    </hgroup>
                </article>
                <article style="text-align: center; padding: 1.5rem;">
                    <hgroup style="margin-bottom: 0;">
                        <h2 style="margin-bottom: 0;">${semesterExamCount}</h2>
                        <p>Klausuren/Semester</p>
                    </hgroup>
                </article>
                <article style="text-align: center; padding: 1.5rem;">
                    <hgroup style="margin-bottom: 0;">
                        <h2 style="margin-bottom: 0;">${String.format(java.util.Locale.GERMANY, "%.2f €", currentAzureAmount)}</h2>
                        <p>von ${String.format(java.util.Locale.GERMANY, "%.2f €", currentAzureCredit)} Azure Credit</p>
                    </hgroup>
                </article>
            </div>
        </main>
    </article>
</div>
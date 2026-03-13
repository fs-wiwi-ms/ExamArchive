@import ms.wiwi.examarchive.JteLocalizer
@import ms.wiwi.examarchive.ModuleSearchResult
@import java.util.List

@param List<ModuleSearchResult> results

<style>
    .search-result-list {
        list-style: none;
        padding: 0;
        margin: 0;
        border: 1px solid var(--pico-muted-border-color);
        border-radius: var(--pico-border-radius);
        overflow: hidden;
        background: var(--pico-background-color);
    }

    .search-result-item {
        margin: 0;
        border-bottom: 1px solid var(--pico-muted-border-color);
    }

    .search-result-item:last-child {
        border-bottom: none;
    }

    .search-result-link {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: 1rem 1.25rem;
        text-decoration: none;
        color: inherit;
        transition: background-color 0.1s ease-in-out;
        gap: 1rem;
    }

    .search-result-link:hover,
    .search-result-link:focus {
        background-color: var(--pico-form-element-background-color);
        outline: none;
    }

    .result-info {
        flex: 1;
        min-width: 0;
    }

    .result-title {
        margin-bottom: 0.25rem;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .professor-scroll-container {
        display: flex;
        gap: 1.25rem;
        overflow-x: auto;
        white-space: nowrap;
        padding-bottom: 0.1rem;
        scrollbar-width: none;
        -ms-overflow-style: none; /* IE and Edge */
    }

    .professor-scroll-container::-webkit-scrollbar {
        display: none;
    }

    .professor-name {
        font-size: 0.75rem;
        color: var(--pico-muted-color);
        flex-shrink: 0;
    }

    .result-stats {
        text-align: right;
        flex-shrink: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
    }

    .exam-badge {
        display: inline-block;
        padding: 0.25rem 0.75rem;
        background-color: var(--pico-primary-background);
        color: var(--pico-primary-inverse);
        border-radius: 2rem;
        font-size: 0.85rem;
        font-weight: 600;
        text-align: center;
        white-space: nowrap;
    }

    .exam-label {
        font-size: 0.7rem;
        color: var(--pico-muted-color);
        text-transform: uppercase;
        letter-spacing: 0.05em;
        margin-top: 0.25rem;
    }

    .empty-search {
        text-align: center;
        padding: 3rem 1rem;
        color: var(--pico-muted-color);
    }

    .empty-icon {
        margin-bottom: 1rem;
        opacity: 0.5;
    }

    .empty-title {
        margin-bottom: 0.25rem;
        color: var(--pico-color);
    }
</style>

@if(results.isEmpty())
    <div class="empty-search">
        <svg class="empty-icon" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
        <h4 class="empty-title">${JteLocalizer.get("search.no-result.header")}</h4>
        <p><small>${JteLocalizer.get("search.no-result.text")}</small></p>
    </div>
@else
    <div id="search-results">
        <ul class="search-result-list">
            @for(ModuleSearchResult result : results)
                <li class="search-result-item">
                    <a href="/exams/${result.moduleID()}" class="search-result-link">

                        <div class="result-info">
                            <h5 class="result-title">${result.name()}</h5>
                            <div class="professor-scroll-container">
                                @for(String professor : result.professors())
                                    <span class="professor-name">${professor}</span>
                                @endfor
                            </div>
                        </div>

                        <div class="result-stats">
                            <span class="exam-badge">${result.exams()}</span>
                            <div class="exam-label">
                                ${JteLocalizer.get("search.exams")}
                            </div>
                        </div>

                    </a>
                </li>
            @endfor
        </ul>
    </div>
@endif
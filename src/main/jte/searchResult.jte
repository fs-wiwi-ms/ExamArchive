@import ms.wiwi.examarchive.services.JteLocalizer
@import ms.wiwi.examarchive.ModuleSearchResultDTO
@import java.util.List

@param List<ModuleSearchResultDTO> results
@param boolean isTopDownloads

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

    .top-downloads-header {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-bottom: 1rem;
        color: var(--pico-muted-color);
        padding-left: 0.25rem;
    }

    .top-downloads-header h5 {
        margin: 0;
        font-size: 0.9rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: inherit;
    }

    .more-results-hint {
        text-align: center;
        padding: 1.5rem 1rem 0.5rem 1rem;
        color: var(--pico-muted-color);
        font-size: 0.85rem;
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
        @if(isTopDownloads)
            <div class="top-downloads-header">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"></polyline>
                    <polyline points="17 6 23 6 23 12"></polyline>
                </svg>
                <h5>${JteLocalizer.get("search.top-downloads")}</h5>
            </div>
        @endif
        <ul class="search-result-list">
            @for(ModuleSearchResultDTO result : results)
                <li class="search-result-item">
                    <a href="/exams/module/${result.moduleID()}" class="search-result-link">

                        <div class="result-info">
                            <h5 class="result-title">${result.name()}</h5>
                            <div class="professor-scroll-container">
                                @for(String professor : result.professors())
                                    @if(!professor.contains("Unbekannt"))
                                        <span class="professor-name">${professor}</span>
                                    @endif
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
        @if(isTopDownloads)
            <div class="more-results-hint">
                ${JteLocalizer.get("search.more-exams-hint")}
            </div>
        @endif
    </div>
@endif
@param int users
@param int admins
@param int blocked

<div class="container">

    <div class="grid">
        <article style="text-align: center; padding: 1.5rem;">
            <hgroup style="margin-bottom: 0;">
                <h2 style="margin-bottom: 0;">${users}</h2>
                <p>Studenten</p>
            </hgroup>
        </article>
        <article style="text-align: center; padding: 1.5rem;">
            <hgroup style="margin-bottom: 0;">
                <h2 style="margin-bottom: 0;">${admins}</h2>
                <p>Admins</p>
            </hgroup>
        </article>
        <article style="text-align: center; padding: 1.5rem; border-color: var(--pico-del-color);">
            <hgroup style="margin-bottom: 0;">
                <h2 style="margin-bottom: 0; color: var(--pico-del-color);">${blocked}</h2>
                <p>Blockiert</p>
            </hgroup>
        </article>
    </div>

    <form hx-post="/admin/users/search"
          hx-target="#search-results"
          hx-trigger="submit">

        <input type="search"
               name="search"
               placeholder="Account finden"
               autocomplete="off"
               hx-post="/admin/users/search"
               hx-trigger="input changed delay:200ms, load"
               hx-target="#search-results">
    </form>

    <div id="search-results">

    </div>

</div>
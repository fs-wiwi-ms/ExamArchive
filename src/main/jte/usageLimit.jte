@import ms.wiwi.examarchive.services.JteLocalizer

@param int used
@param int max

!{
    int safeMax = Math.max(1, max);
    int percentage = Math.min(100, Math.max(0, (int) Math.round(((double) used / safeMax) * 100)));
    int hue = Math.max(0, (int) Math.round((1.0 - ((double) used / safeMax)) * 120));
}

<div class="usage-limit-box">
    <div class="usage-limit-header">
        <span><strong>${JteLocalizer.get("examai.limit.label")}</strong></span>
        <span class="usage-limit-text">
            <strong>${used}</strong> / ${max} ${JteLocalizer.get("examai.limit.unit")}
        </span>
    </div>

    <div class="usage-bar-track"
         role="progressbar"
         aria-valuenow="${used}"
         aria-valuemin="0"
         aria-valuemax="${max}">
        <div class="usage-bar-fill"
             style="width: ${percentage}%; background-color: hsl(${hue}, 80%, 45%);">
        </div>
    </div>
</div>

<style>
    .usage-limit-box {
        margin-top: 1.5rem;
        margin-bottom: 0.5rem;
    }

    .usage-limit-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 0.875rem;
        margin-bottom: 0.4rem;
    }

    .usage-limit-text {
        color: var(--pico-muted-color);
        font-variant-numeric: tabular-nums;
    }

    .usage-bar-track {
        width: 100%;
        height: 10px;
        background-color: var(--pico-form-element-border-color);
        border-radius: var(--pico-border-radius);
        overflow: hidden;
    }

    .usage-bar-fill {
        height: 100%;
        border-radius: inherit;
        transition: width 0.3s ease, background-color 0.3s ease;
    }
</style>
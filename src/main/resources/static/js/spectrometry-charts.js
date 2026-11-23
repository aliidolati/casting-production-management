const SpectrometryCharts = (() => {
    const elements = [
        { key: "mg", symbol: "Mg", name: "منیزیم", stableSlope: 0.0002 },
        { key: "fe", symbol: "Fe", name: "آهن", stableSlope: 0.0001 },
        { key: "ti", symbol: "Ti", name: "تیتانیوم", stableSlope: 0.00005 },
    ];
    const colors = { mean: "#df850d", fit: "#3b82f6", selected: "#16a34a", other: "#858c99" };
    const directions = { increasing: "افزایشی", decreasing: "کاهشی", stable: "تقریباً ثابت" };
    const hitSize = 12;
    const charts = new Map();
    let points = [];
    let selection = new Set();
    let basis = [];
    let domain;
    let onToggle;
    let queued = false;
    let overlap = null;

    // Same centered least-squares model as TrendAnalyzer, evaluated locally so selection is immediate.
    function statistics(rows, key, stableSlope) {
        const count = rows.length;
        if (!count) return { mean: null, slope: null, r2: null, reason: "empty" };

        const first = rows[0].day;
        const last = rows[count - 1].day;
        const base = Number(rows[0][key]);
        let sumX = 0;
        let sumY = 0;
        for (const row of rows) {
            sumX += row.day - first;
            sumY += Number(row[key]) - base;
        }
        const meanX = sumX / count;
        const meanDelta = sumY / count;
        const mean = base + meanDelta;
        if (first === last) return { mean, slope: null, r2: null, reason: "dates" };

        let xx = 0;
        let xy = 0;
        let yy = 0;
        for (const row of rows) {
            const dx = row.day - first - meanX;
            const dy = Number(row[key]) - base - meanDelta;
            xx += dx * dx;
            xy += dx * dy;
            yy += dy * dy;
        }
        const slope = xy / xx;
        return {
            mean,
            slope,
            r2: yy === 0 ? null : Math.max(0, Math.min(1, xy * xy / (xx * yy))),
            direction: Math.abs(slope) <= stableSlope ? "stable" : slope > 0 ? "increasing" : "decreasing",
            start: { day: first, value: mean - slope * meanX },
            end: { day: last, value: mean + slope * (last - first - meanX) },
        };
    }

    function init(toggle) {
        onToggle = toggle;
        const container = document.getElementById("spectrometry-charts");
        for (const element of elements) {
            const card = document.createElement("section");
            card.className = "trend-panel spectrometry-chart";
            card.innerHTML = `
                <h4 class="trend-panel-title">${element.symbol} · ${element.name}</h4>
                <div class="trend-chart-toolbar">
                    <label class="trend-toggle"><input type="checkbox" data-line="mean"> نمایش میانگین</label>
                    <label class="trend-toggle"><input type="checkbox" data-line="fit"> نمایش رگرسیون خطی</label>
                    <label class="trend-toggle"><input type="checkbox" data-line="trend" checked> نمایش روند و R²</label>
                </div>
                <ul class="spectrometry-legend">
                    <li><span class="spectrometry-dot"></span> انتخاب‌نشده</li>
                    <li><span class="spectrometry-triangle"></span> انتخاب‌شده</li>
                    <li data-legend="mean" hidden><span class="spectrometry-line spectrometry-mean"></span> میانگین</li>
                    <li data-legend="fit" hidden><span class="spectrometry-line spectrometry-fit"></span> رگرسیون</li>
                </ul>
                <canvas role="img" aria-label="نمودار ${element.symbol} بر حسب تاریخ آزمون" dir="ltr"></canvas>
                <dl class="spectrometry-metrics">
                    <div><dt>میانگین</dt><dd data-metric="mean"></dd></div>
                    <div><dt>R²</dt><dd data-metric="r2"></dd></div>
                    <div><dt>جهت روند</dt><dd data-metric="trend"></dd></div>
                </dl>
                <p class="form-hint spectrometry-stat-note"></p>`;
            const canvas = card.querySelector("canvas");
            const chart = { ...element, card, canvas, hits: new Map() };
            charts.set(element.key, chart);
            container.append(card);
            card.addEventListener("change", schedule);
            canvas.addEventListener("pointermove", (event) => onPointer(event, chart, false));
            canvas.addEventListener("pointerleave", () => { if (!overlap) hideTooltip(); });
            canvas.addEventListener("click", (event) => onPointer(event, chart, true));
        }
        window.addEventListener("resize", schedule);
        document.addEventListener("themechange", schedule);
        document.addEventListener("scroll", (event) => {
            if (event.target !== document.getElementById("spectrometry-tooltip")) hideTooltip();
        }, true);
        document.addEventListener("pointerdown", (event) => {
            if (overlap && !event.target.closest("#spectrometry-tooltip, .spectrometry-chart canvas")) hideTooltip();
        });
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && overlap) hideTooltip();
        });
    }

    function setPoints(rows) {
        hideTooltip();
        points = rows.map(row => ({ ...row, day: Date.parse(row.testDate) / TREND_DAY_MS }));
        domain = trendTimeDomain({ samples: rows.map(row => ({ testedAt: row.testDate })) });
    }

    function setSelection(selected, selectedOnly) {
        selection = selected;
        const nextBasis = selectedOnly ? points.filter(row => selected.has(row.id)) : points;
        if (nextBasis !== basis) {
            basis = nextBasis;
            for (const chart of charts.values()) {
                chart.stats = statistics(basis, chart.key, chart.stableSlope);
            }
        }
        schedule();
    }

    function reset() {
        setPoints([]);
        selection = new Set();
        basis = [];
        charts.forEach(chart => {
            chart.hits.clear();
            chart.stats = null;
            chart.canvas.getContext("2d").clearRect(0, 0, chart.canvas.width, chart.canvas.height);
            chart.card.querySelectorAll("[data-line]").forEach(input => { input.checked = input.defaultChecked; });
        });
    }

    function schedule() {
        if (queued) return;
        queued = true;
        requestAnimationFrame(() => {
            queued = false;
            if (!document.getElementById("spectrometry-section").classList.contains("active")) return;
            charts.forEach(draw);
        });
    }

    function metric(value, digits) {
        return value == null ? "—" : value.toLocaleString("en-US", { maximumFractionDigits: digits, useGrouping: false });
    }

    function renderMetrics(chart, meanLine, regression, trend) {
        const { card, stats } = chart;
        card.querySelector('[data-legend="mean"]').hidden = !meanLine;
        card.querySelector('[data-legend="fit"]').hidden = !regression;
        card.querySelector('[data-metric="mean"]').textContent = stats.mean == null ? "—" : `${metric(stats.mean, 10)} wt%`;
        card.querySelector('[data-metric="r2"]').textContent = trend ? metric(stats.r2, 4) : "—";
        card.querySelector('[data-metric="trend"]').textContent = trend ? (directions[stats.direction] ?? "نامشخص (—)") : "—";
        const notes = [];
        if (stats.reason === "empty") notes.push("آزمونی در مبنای تحلیل نیست.");
        if (stats.reason === "dates") notes.push("برای رگرسیون و تعیین روند، دست‌کم دو تاریخ آزمون متفاوت لازم است.");
        if (!stats.reason && (trend || regression)) {
            notes.push(`شیب ${metric(stats.slope, 12)} wt% در روز. آستانهٔ روند تقریباً ثابت ${chart.stableSlope} wt% در روز.`);
            if (trend && stats.r2 == null) notes.push("با مقادیر ثابت، R² تعریف نمی‌شود.");
            if (trend && basis.length === 2) notes.push("برازش بر پایهٔ دو آزمون است.");
        }
        if (!trend) notes.push("نمایش روند و R² خاموش است.");
        card.querySelector(".spectrometry-stat-note").textContent = notes.join(" ");
    }

    function draw(chart) {
        if (!chart.stats) return;
        const { canvas, card, stats } = chart;
        const meanLine = card.querySelector('[data-line="mean"]').checked && stats.mean != null;
        const regression = card.querySelector('[data-line="fit"]').checked && stats.slope != null;
        renderMetrics(chart, meanLine, regression, card.querySelector('[data-line="trend"]').checked);
        chart.hits.clear();

        const width = canvas.clientWidth;
        const height = canvas.clientHeight;
        if (!width || !height) return;
        const dpr = window.devicePixelRatio || 1;
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        const ctx = canvas.getContext("2d");
        ctx.scale(dpr, dpr);
        const palette = trendChartPalette();
        ctx.fillStyle = palette.plotBg;
        ctx.fillRect(0, 0, width, height);
        ctx.font = "12px Vazirmatn, sans-serif";
        ctx.fillStyle = palette.label;
        if (!domain) {
            ctx.textAlign = "center";
            ctx.fillText("آزمونی برای نمایش وجود ندارد", width / 2, height / 2);
            return;
        }

        let low = Infinity;
        let high = -Infinity;
        for (const row of points) {
            const value = Number(row[chart.key]);
            low = Math.min(low, value);
            high = Math.max(high, value);
        }
        if (regression) {
            low = Math.min(low, stats.start.value, stats.end.value);
            high = Math.max(high, stats.start.value, stats.end.value);
        }
        const pad = Math.max((high - low) * 0.1, Math.abs(high) * 0.003, 1e-10);
        low -= pad;
        high += pad;
        const left = width < 400 ? 84 : 98;
        const right = width - 16;
        const top = 20;
        const bottom = height - 47;
        const px = (day) => left + (day * TREND_DAY_MS - domain.min) * (right - left) / (domain.max - domain.min);
        const py = (value) => bottom - (value - low) * (bottom - top) / (high - low);
        const line = (x1, y1, x2, y2, color, width = 1) => {
            ctx.strokeStyle = color;
            ctx.lineWidth = width;
            ctx.beginPath();
            ctx.moveTo(x1, y1);
            ctx.lineTo(x2, y2);
            ctx.stroke();
        };

        ctx.textAlign = "right";
        ctx.textBaseline = "middle";
        for (let tick = 0; tick <= 4; tick++) {
            const value = low + (high - low) * tick / 4;
            const y = py(value);
            line(left, y, right, y, palette.grid);
            ctx.fillText(metric(value, 10), left - 6, y);
        }
        ctx.textAlign = "center";
        const tickCount = Math.max(2, Math.floor((right - left) / 99));
        // Use UTC calendar days so a local offset or daylight saving cannot move a date tick.
        for (const day of dateTicks(domain, tickCount)) {
            const x = px(day);
            line(x, top, x, bottom, palette.grid);
            const label = isoToJalaliString(new Date(day * TREND_DAY_MS).toISOString());
            ctx.fillText(label, Math.max(left, Math.min(right - 22, x)), bottom + 14);
        }
        line(left, top, left, bottom, palette.axis);
        line(left, bottom, right, bottom, palette.axis);
        ctx.fillText("تاریخ آزمون", (left + right) / 2, height - 8);
        ctx.textAlign = "left";
        ctx.fillText("wt%", 8, 10);

        if (meanLine) line(left, py(stats.mean), right, py(stats.mean), colors.mean, 2);
        if (regression) line(px(stats.start.day), py(stats.start.value), px(stats.end.day), py(stats.end.value), colors.fit, 2);

        const selectedHits = [];
        for (const row of points) {
            const hit = { row, x: px(row.day), y: py(Number(row[chart.key])) };
            const key = hitCell(hit.x, hit.y);
            if (!chart.hits.has(key)) chart.hits.set(key, []);
            chart.hits.get(key).push(hit);
            if (selection.has(row.id)) {
                selectedHits.push(hit);
            } else {
                drawPoint(ctx, hit, false, palette.pointStroke);
            }
        }
        selectedHits.forEach(hit => drawPoint(ctx, hit, true, palette.pointStroke));
    }

    function dateTicks({ min, max }, count) {
        const span = (max - min) / TREND_DAY_MS;
        const step = Math.max(1, Math.ceil(span / count));
        const ticks = [];
        for (let day = Math.ceil(min / TREND_DAY_MS); day <= max / TREND_DAY_MS; day += step) ticks.push(day);
        return ticks;
    }

    function drawPoint(ctx, { x, y }, selected, border) {
        ctx.beginPath();
        if (selected) {
            ctx.moveTo(x, y - 7);
            ctx.lineTo(x + 6, y + 5);
            ctx.lineTo(x - 6, y + 5);
            ctx.closePath();
        } else {
            ctx.arc(x, y, 4.5, 0, Math.PI * 2);
        }
        ctx.fillStyle = selected ? colors.selected : colors.other;
        ctx.strokeStyle = border;
        ctx.lineWidth = 1;
        ctx.fill();
        ctx.stroke();
    }

    function hitCell(x, y) {
        return `${Math.floor(x / hitSize)}:${Math.floor(y / hitSize)}`;
    }

    function nearby(chart, x, y) {
        let distance = hitSize;
        let closest = null;
        const candidates = [];
        for (let dx = -1; dx <= 1; dx++) {
            for (let dy = -1; dy <= 1; dy++) {
                for (const hit of chart.hits.get(hitCell(x + dx * hitSize, y + dy * hitSize)) || []) {
                    candidates.push(hit);
                    const separation = Math.hypot(hit.x - x, hit.y - y);
                    if (separation <= distance) {
                        distance = separation;
                        closest = hit;
                    }
                }
            }
        }
        if (!closest) return [];
        return [closest, ...candidates.filter(hit => hit !== closest && Math.hypot(hit.x - closest.x, hit.y - closest.y) < 2)];
    }

    function onPointer(event, chart, clicked) {
        if (overlap && !clicked) return;
        const box = chart.canvas.getBoundingClientRect();
        const hits = nearby(chart, event.clientX - box.left, event.clientY - box.top);
        if (!hits.length) return hideTooltip();
        if (clicked && hits.length === 1) onToggle(hits[0].row.id);
        overlap = clicked && hits.length > 1 ? { event, chart, hits } : null;
        showTooltip(event, chart, hits);
    }

    function showTooltip(event, chart, hits, focusId) {
        const tip = document.getElementById("spectrometry-tooltip");
        const detail = ({ row }) => `
            <span class="trend-tooltip-meta">${escapeHtml(row.projectCode)} · ${escapeHtml(row.pieceName)} · ${formatFaNumber(row.pieceNumber)}</span>
            <span class="trend-tooltip-row"><span>${chart.name} · <bdi>${isoToJalaliString(row.testDate)}</bdi></span>
                <span dir="ltr">${chart.symbol} · ${escapeHtml(row[chart.key])} wt%</span></span>
            <span class="trend-tooltip-meta">${selection.has(row.id) ? "انتخاب‌شده برای تحلیل" : "انتخاب‌نشده برای تحلیل"}</span>`;
        if (overlap) {
            tip.innerHTML = `<strong>انتخاب آزمون از نقاط هم‌پوشان</strong>
                ${hits.map(hit => `<button type="button" class="spectrometry-overlap-option"
                    data-point-id="${escapeHtml(hit.row.id)}" aria-pressed="${selection.has(hit.row.id)}">${detail(hit)}
                    <span>کد آزمون <bdi>${escapeHtml(hit.row.id.slice(0, 8))}</bdi></span></button>`).join("")}`;
            tip.querySelectorAll("[data-point-id]").forEach(button => button.addEventListener("click", () => {
                onToggle(button.dataset.pointId);
                showTooltip(event, chart, hits, button.dataset.pointId);
            }));
        } else {
            tip.innerHTML = detail(hits[0]) + (hits.length > 1 ? "<span>برای دیدن آزمون‌های هم‌پوشان کلیک کنید</span>" : "");
        }
        tip.classList.toggle("spectrometry-tooltip-open", Boolean(overlap));
        tip.hidden = false;
        if (overlap) {
            const buttons = [...tip.querySelectorAll("button")];
            (buttons.find(button => button.dataset.pointId === focusId) || buttons[0])?.focus({ preventScroll: true });
        }
        tip.style.left = `${Math.max(8, Math.min(event.clientX + 12, innerWidth - tip.offsetWidth - 8))}px`;
        tip.style.top = `${Math.max(8, Math.min(event.clientY + 12, innerHeight - tip.offsetHeight - 8))}px`;
    }

    function hideTooltip() {
        overlap = null;
        const tooltip = document.getElementById("spectrometry-tooltip");
        if (tooltip) tooltip.hidden = true;
    }

    return { init, reset, setPoints, setSelection, hideTooltip, statistics };
})();

if (typeof module !== "undefined") module.exports = SpectrometryCharts;

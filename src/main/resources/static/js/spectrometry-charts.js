const SpectrometryCharts = (() => {
    const elements = [
        { key: "mg", symbol: "Mg", name: "منیزیم", stableSlope: 0.0002 },
        { key: "fe", symbol: "Fe", name: "آهن", stableSlope: 0.0001 },
        { key: "ti", symbol: "Ti", name: "تیتانیوم", stableSlope: 0.00005 },
    ];
    const colors = { mean: "#ab6c21", fit: "#7259c4", selected: "#624bce", other: "#858c99" };
    const directions = { increasing: "افزایشی", decreasing: "کاهشی", stable: "تقریباً ثابت" };
    const hitSize = 12;
    const charts = new Map();
    let activeElement = elements[0].key;
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
        const options = document.getElementById("spectrometry-elements");
        for (const element of elements) {
            const isActive = element.key === activeElement;
            const option = document.createElement("label");
            option.innerHTML = `<input type="radio" name="spectrometry-element" value="${element.key}"
                aria-label="${element.symbol} · ${element.name}" ${isActive ? "checked" : ""}><bdi>${element.symbol}</bdi>`;
            options.append(option);

            const card = document.createElement("section");
            card.className = "spectrometry-chart";
            card.hidden = !isActive;
            card.dataset.element = element.key;
            card.innerHTML = `
                <div class="spectrometry-chart-summary">
                    <h4 class="trend-panel-title">${element.name}</h4>
                    <dl class="spectrometry-metrics">
                        <div><dt>میانگین</dt><dd data-metric="mean"></dd></div>
                        <div><dt><bdi>R²</bdi></dt><dd data-metric="r2"></dd></div>
                        <div><dt>جهت روند</dt><dd data-metric="trend"></dd></div>
                    </dl>
                </div>
                <canvas role="img" aria-label="نمودار ${element.symbol} بر حسب تاریخ آزمون" dir="ltr"></canvas>
                <ul class="spectrometry-legend">
                    <li><span class="spectrometry-triangle"></span> آزمون انتخاب‌شده</li>
                    <li><span class="spectrometry-dot"></span> سایر آزمون‌ها</li>
                    <li data-legend="mean" hidden><span class="spectrometry-line spectrometry-mean"></span> میانگین</li>
                    <li data-legend="fit" hidden><span class="spectrometry-line spectrometry-fit"></span> رگرسیون خطی</li>
                </ul>
                <div class="trend-chart-toolbar">
                    <label class="trend-toggle"><input type="checkbox" data-line="mean" checked> نمایش میانگین</label>
                    <label class="trend-toggle"><input type="checkbox" data-line="fit" checked> نمایش رگرسیون خطی</label>
                    <label class="trend-toggle"><input type="checkbox" data-line="trend" checked> نمایش روند و R²</label>
                </div>
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
        options.addEventListener("change", (event) => selectElement(event.target.value));
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

    function selectElement(key) {
        if (!charts.has(key)) return;
        charts.get(activeElement).card.hidden = true;
        activeElement = key;
        charts.get(key).card.hidden = false;
        hideTooltip();
        schedule();
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
        selectElement(elements[0].key);
        document.querySelector(`[name="spectrometry-element"][value="${activeElement}"]`).checked = true;
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
            if (document.getElementById("spectrometry-panel-overview").hidden) return;
            draw(charts.get(activeElement));
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
        if (!canvas.clientWidth || !canvas.clientHeight) return;
        const meanLine = card.querySelector('[data-line="mean"]').checked && stats.mean != null;
        const regression = card.querySelector('[data-line="fit"]').checked && stats.slope != null;
        renderMetrics(chart, meanLine, regression, card.querySelector('[data-line="trend"]').checked);
        chart.hits.clear();

        const width = canvas.clientWidth;
        const height = canvas.clientHeight;
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
        const ticks = Array.from({ length: 5 }, (_, index) => low + (high - low) * index / 4);
        const precision = Math.max(0, Math.min(10, Math.ceil(-Math.log10((high - low) / 4))));
        const left = Math.max(52, ...ticks.map(value => Math.ceil(ctx.measureText(metric(value, precision)).width) + 15));
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
        for (const value of ticks) {
            const y = py(value);
            line(left, y, right, y, palette.grid);
            ctx.fillText(metric(value, precision), left - 6, y);
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

        if (meanLine) {
            ctx.setLineDash([6, 4]);
            line(left, py(stats.mean), right, py(stats.mean), colors.mean, 2);
            ctx.setLineDash([]);
        }
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
        if (width >= 600 && selectedHits.length <= 5) {
            ctx.font = "600 12px Vazirmatn, sans-serif";
            ctx.textAlign = "center";
            ctx.fillStyle = palette.label;
            ctx.strokeStyle = palette.plotBg;
            ctx.lineWidth = 3;
            let previousX = -Infinity;
            for (const { row, x, y } of selectedHits) {
                if (x - previousX < 48) continue;
                const label = metric(Number(row[chart.key]), 3);
                const labelY = y < top + 12 ? y + 15 : y - 13;
                ctx.strokeText(label, x, labelY);
                ctx.fillText(label, x, labelY);
                previousX = x;
            }
        }
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

    return { init, reset, setPoints, setSelection, hideTooltip, redraw: schedule, statistics };
})();

if (typeof module !== "undefined") module.exports = SpectrometryCharts;

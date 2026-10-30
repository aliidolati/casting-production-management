/**
 * گزارش Quality Index تست کشش.
 * هر نمونه یک نقطه است: محور افقی YS و محور عمودی EL.
 * خط مرجع ثابت است و با داده‌ها جابه‌جا نمی‌شود
 */

const QI_X_MIN = 0;
const QI_X_MAX = 600;
const QI_Y_MIN = 0;
const QI_Y_MAX = 10;

function qiReferenceEl(ys) {
    return 0.25 * (36 - 0.064 * ys);
}

/**
 * رنگ‌های نمودار از توکن‌های تم (--chart-*) خوانده می‌شوند تا با تغییر تم عوض شوند.
 * مقدار دوم هر توکن فقط پشتیبان است اگر سیستم تم بارگذاری نشده باشد.
 */
function qiChartPalette() {
    const token = (name, fallback) => (
        window.ThemeManager ? ThemeManager.cssVar(name, fallback) : fallback
    );
    return {
        plotBg: token("--chart-plot-bg", "#FFFFFF"),
        grid: token("--chart-grid", "#E6E2F0"),
        axis: token("--chart-axis", "#1B1A2E"),
        label: token("--chart-label", "#6B6880"),
        title: token("--chart-title", "#1B1A2E"),
        reference: token("--chart-reference", "#5346E0"),
        pointStroke: token("--chart-point-stroke", "#FFFFFF"),
        bands: {
            GREEN: token("--chart-success", "#16A34A"),
            YELLOW: token("--chart-warning", "#EAB308"),
            RED: token("--chart-danger", "#DC2626"),
            UNDEFINED: token("--chart-neutral", "#9B98AB"),
        },
    };
}

const TensileQualityState = {
    dateFrom: "",
    dateTo: "",
    dateFromPicker: null,
    dateToPicker: null,
    points: [],
    stats: null,
    hits: [],
    loadedOnce: false,
    platesLoaded: false,
};

function initTensileQualityReportUi() {
    const dateFromInput = document.getElementById("tensile-quality-date-from");
    const dateToInput = document.getElementById("tensile-quality-date-to");
    if (dateFromInput && typeof attachPersianDatePicker === "function") {
        TensileQualityState.dateFromPicker = attachPersianDatePicker(dateFromInput, {
            onSelect: (d) => {
                TensileQualityState.dateFrom = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                TensileQualityState.dateFrom = "";
            },
        });
    }
    if (dateToInput && typeof attachPersianDatePicker === "function") {
        TensileQualityState.dateToPicker = attachPersianDatePicker(dateToInput, {
            onSelect: (d) => {
                TensileQualityState.dateTo = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                TensileQualityState.dateTo = "";
            },
        });
    }

    document.getElementById("tensile-quality-project")?.addEventListener("change", (e) => {
        loadTensileQualityParts(e.target.value);
    });
    document.getElementById("btn-tensile-quality-run")?.addEventListener("click", () => runTensileQualityReport());
    document.getElementById("btn-tensile-quality-export")?.addEventListener("click", exportTensileQualityCsv);
    document.getElementById("btn-tensile-quality-reset")?.addEventListener("click", () => resetTensileQualityFilters());
    ["tensile-quality-ys-min", "tensile-quality-ys-max", "tensile-quality-el-min", "tensile-quality-el-max"].forEach((id) => {
        document.getElementById(id)?.addEventListener("input", () => applyTensileQualityView());
    });

    const plateDetails = document.getElementById("tensile-quality-plate-details");
    if (plateDetails) {
        document.addEventListener("click", (event) => {
            if (!(event.target instanceof Element) || !event.target.closest("#tensile-quality-plate-details")) {
                plateDetails.open = false;
            }
        });
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") plateDetails.open = false;
        });
    }

    const canvas = document.getElementById("tensile-quality-canvas");
    canvas?.addEventListener("mousemove", onTensileQualityHover);
    canvas?.addEventListener("mouseleave", hideTensileQualityTooltip);
    const redrawIfVisible = () => {
        const panel = document.getElementById("dashboard-tab-tensile-quality-report");
        if (panel && !panel.hidden) applyTensileQualityView();
    };
    window.addEventListener("resize", redrawIfVisible);
    /* canvas رنگ خودش را نگه می‌دارد؛ با تغییر تم باید دوباره رسم شود */
    document.addEventListener("themechange", redrawIfVisible);
}

async function openTensileQualityReport() {
    if (typeof ensureDashboardProjects === "function") {
        await ensureDashboardProjects();
    }
    await ensureTensileQualityPlates();
    if (!TensileQualityState.loadedOnce) {
        TensileQualityState.loadedOnce = true;
        await runTensileQualityReport();
        return;
    }
    applyTensileQualityView();
}

async function ensureTensileQualityPlates() {
    if (TensileQualityState.platesLoaded) return;
    const container = document.getElementById("tensile-quality-plates");
    if (!container || typeof renderMultiFilterOptions !== "function") return;
    try {
        const labels = await TensileTestAPI.getSampleLabels();
        const selected = new Set(
            typeof getMultiFilterValues === "function" ? getMultiFilterValues("tensile-quality-plates") : []
        );
        renderMultiFilterOptions(
            "tensile-quality-plates",
            (labels || []).map((label) => ({ value: label, label }))
        );
        if (selected.size) {
            container.querySelectorAll('input[type="checkbox"]').forEach((input) => {
                input.checked = selected.has(input.value);
            });
            if (typeof updateMultiFilterBadge === "function") updateMultiFilterBadge("tensile-quality-plates");
        }
        TensileQualityState.platesLoaded = true;
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function loadTensileQualityParts(projectId) {
    const select = document.getElementById("tensile-quality-part");
    if (!select) return;
    if (!projectId) {
        select.disabled = true;
        select.innerHTML = `<option value="">ابتدا پروژه را انتخاب کنید</option>`;
        return;
    }
    select.disabled = true;
    select.innerHTML = `<option value="">در حال بارگذاری…</option>`;
    try {
        const definitions = await PieceDefinitionAPI.getByProject(projectId);
        select.innerHTML = `<option value="">همه قطعات</option>` + definitions
            .map((d) => `<option value="${d.id}">${escapeHtml(d.pieceName)} — ردیف ${escapeHtml(d.rowNum)}</option>`)
            .join("");
        select.disabled = false;
    } catch (error) {
        select.innerHTML = `<option value="">خطا در بارگذاری قطعات</option>`;
        showToast(error.message, "error");
    }
}

function resetTensileQualityFilters() {
    const project = document.getElementById("tensile-quality-project");
    if (project) project.value = "";
    if (typeof clearMultiFilter === "function") clearMultiFilter("tensile-quality-plates");
    TensileQualityState.dateFrom = "";
    TensileQualityState.dateTo = "";
    TensileQualityState.dateFromPicker?.clear();
    TensileQualityState.dateToPicker?.clear();
    ["tensile-quality-ys-min", "tensile-quality-ys-max", "tensile-quality-el-min", "tensile-quality-el-max"].forEach((id) => {
        const input = document.getElementById(id);
        if (input) input.value = "";
    });
    loadTensileQualityParts("");
    runTensileQualityReport();
}

function setTensileQualityError(message) {
    const box = document.getElementById("tensile-quality-error");
    if (!box) return;
    box.textContent = message || "";
    box.style.display = message ? "block" : "none";
}

async function runTensileQualityReport() {
    setTensileQualityError("");
    TensileQualityState.points = [];
    const exportBtn = document.getElementById("btn-tensile-quality-export");
    if (exportBtn) exportBtn.disabled = true;
    const dateFrom = TensileQualityState.dateFrom;
    const dateTo = TensileQualityState.dateTo;
    if (dateFrom && dateTo && dateTo < dateFrom) {
        setTensileQualityError("تاریخ پایان نمی‌تواند قبل از تاریخ شروع باشد");
        return;
    }

    const runBtn = document.getElementById("btn-tensile-quality-run");
    if (runBtn) runBtn.disabled = true;
    try {
        const report = await DashboardAPI.getTensileQualityReport({
            projectId: document.getElementById("tensile-quality-project")?.value || "",
            pieceDefinitionId: document.getElementById("tensile-quality-part")?.value || "",
            sampleLabels: typeof getMultiFilterValues === "function"
                ? getMultiFilterValues("tensile-quality-plates")
                : [],
            dateFrom,
            dateTo,
        });
        TensileQualityState.points = report.points || [];
        TensileQualityState.stats = report.stats || null;
        if (exportBtn) exportBtn.disabled = !TensileQualityState.points.length;
        requestAnimationFrame(() => applyTensileQualityView());
    } catch (error) {
        setTensileQualityError(error.message || "خطا در اجرای گزارش");
        showToast(error.message, "error");
    } finally {
        if (runBtn) runBtn.disabled = false;
    }
}

function exportTensileQualityCsv() {
    const domain = tensileQualityDomain();
    const error = tensileQualityRangeError(domain);
    if (error) {
        setTensileQualityError(error);
        return;
    }
    const range = tensileQualityRange();
    const narrowed = Object.values(range).some(value => value != null);
    const points = narrowed ? TensileQualityState.points.filter(point => pointInTensileRange(point, domain))
        : TensileQualityState.points;
    if (!points.length) {
        showToast("داده‌ای برای خروجی وجود ندارد", "error");
        return;
    }
    const lines = [["کد پروژه", "نام قطعه", "شماره قطعه", "شماره تست پلیت", "YS", "EL", "Q", "گروه Q"].join(",")];
    points.forEach(point => lines.push([point.projectCode, point.pieceName, point.pieceNumber,
        point.sampleLabel, point.ys, point.el, point.q, point.band].map(csvEscape).join(",")));
    downloadCsv(`tensile-quality-${new Date().toISOString().slice(0, 10)}.csv`, lines);
}

function formatQiNumber(value, digits) {
    if (value === null || value === undefined || value === "") return "—";
    const num = Number(value);
    if (Number.isNaN(num)) return "—";
    return num.toLocaleString("fa-IR", {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
    });
}

function formatQiPercent(count, total) {
    if (!total) return "۰٪";
    return `${(count * 100 / total).toLocaleString("fa-IR", { maximumFractionDigits: 1 })}٪`;
}

function renderTensileQualityStats(stats) {
    const container = document.getElementById("tensile-quality-stats");
    if (!container) return;
    if (!stats) {
        container.innerHTML = "";
        return;
    }
    const plotted = stats.plottedCount || 0;
    const cards = [
        ["تعداد نقاط", formatQiNumber(plotted, 0)],
        ["سبز", `${formatQiNumber(stats.greenCount, 0)} (${formatQiPercent(stats.greenCount, plotted)})`],
        ["زرد", `${formatQiNumber(stats.yellowCount, 0)} (${formatQiPercent(stats.yellowCount, plotted)})`],
        ["قرمز", `${formatQiNumber(stats.redCount, 0)} (${formatQiPercent(stats.redCount, plotted)})`],
        ["Q نامعتبر", formatQiNumber(stats.undefinedCount, 0)],
        ["غیرقابل رسم", formatQiNumber(stats.unplottableCount, 0)],
        ["میانگین Q", formatQiNumber(stats.averageQ, 3)],
        ["میانگین YS", formatQiNumber(stats.averageYs, 2)],
        ["میانگین EL", formatQiNumber(stats.averageEl, 2)],
    ];
    container.innerHTML = cards.map(([label, value]) => `
        <div class="tensile-quality-stat">
            <div class="tensile-quality-stat-label">${label}</div>
            <div class="tensile-quality-stat-value">${value}</div>
        </div>
    `).join("");
}

function readQiBound(id) {
    const raw = document.getElementById(id)?.value?.trim() ?? "";
    if (raw === "") return null;
    const num = Number(raw);
    return Number.isFinite(num) ? num : NaN;
}

function tensileQualityRange() {
    return {
        ysMin: readQiBound("tensile-quality-ys-min"),
        ysMax: readQiBound("tensile-quality-ys-max"),
        elMin: readQiBound("tensile-quality-el-min"),
        elMax: readQiBound("tensile-quality-el-max"),
    };
}

function tensileQualityDomain() {
    const range = tensileQualityRange();
    return {
        xMin: range.ysMin == null ? QI_X_MIN : range.ysMin,
        xMax: range.ysMax == null ? QI_X_MAX : range.ysMax,
        yMin: range.elMin == null ? QI_Y_MIN : range.elMin,
        yMax: range.elMax == null ? QI_Y_MAX : range.elMax,
    };
}

function tensileQualityRangeError(domain) {
    if ([domain.xMin, domain.xMax, domain.yMin, domain.yMax].some((value) => Number.isNaN(value))) {
        return "رنج YS و EL باید عدد باشد";
    }
    if (!(domain.xMax > domain.xMin)) return "حد بالای YS باید بزرگ‌تر از حد پایین باشد";
    if (!(domain.yMax > domain.yMin)) return "حد بالای EL باید بزرگ‌تر از حد پایین باشد";
    return "";
}

function pointInTensileRange(point, domain) {
    const ys = Number(point.ys);
    const el = Number(point.el);
    if (!Number.isFinite(ys) || !Number.isFinite(el)) return false;
    return ys >= domain.xMin && ys <= domain.xMax && el >= domain.yMin && el <= domain.yMax;
}

function statsFromVisiblePoints(points) {
    let green = 0;
    let yellow = 0;
    let red = 0;
    let undefined = 0;
    let sumQ = 0;
    let qCount = 0;
    let sumYs = 0;
    let sumEl = 0;
    points.forEach((point) => {
        if (point.band === "GREEN") green += 1;
        else if (point.band === "YELLOW") yellow += 1;
        else if (point.band === "RED") red += 1;
        else undefined += 1;
        const q = Number(point.q);
        if ((point.band === "GREEN" || point.band === "YELLOW" || point.band === "RED") && Number.isFinite(q)) {
            sumQ += q;
            qCount += 1;
        }
        sumYs += Number(point.ys);
        sumEl += Number(point.el);
    });
    const count = points.length;
    return {
        plottedCount: count,
        greenCount: green,
        yellowCount: yellow,
        redCount: red,
        undefinedCount: undefined,
        unplottableCount: 0,
        averageQ: qCount ? sumQ / qCount : null,
        averageYs: count ? sumYs / count : null,
        averageEl: count ? sumEl / count : null,
    };
}

function applyTensileQualityView() {
    const domain = tensileQualityDomain();
    const error = tensileQualityRangeError(domain);
    if (error) {
        setTensileQualityError(error);
        return;
    }
    setTensileQualityError("");
    const range = tensileQualityRange();
    const narrowed = range.ysMin != null || range.ysMax != null || range.elMin != null || range.elMax != null;
    const visible = narrowed
        ? TensileQualityState.points.filter((point) => pointInTensileRange(point, domain))
        : TensileQualityState.points;
    renderTensileQualityStats(narrowed ? statsFromVisiblePoints(visible) : TensileQualityState.stats);
    drawTensileQualityChart(visible);
}

function niceTicks(min, max, count) {
    const span = max - min;
    if (!(span > 0)) return [min];
    const rough = span / count;
    const mag = 10 ** Math.floor(Math.log10(rough));
    const err = rough / mag;
    const step = err >= 7.5 ? 10 * mag : err >= 3.5 ? 5 * mag : err >= 1.5 ? 2 * mag : mag;
    const start = Math.ceil((min - step * 1e-9) / step) * step;
    const ticks = [];
    for (let value = start; value <= max + step * 1e-6; value += step) {
        const rounded = Number(value.toFixed(6));
        if (rounded >= min - step * 1e-6 && rounded <= max + step * 1e-6) ticks.push(rounded);
    }
    return ticks;
}

function formatTick(value) {
    const rounded = Math.round(value * 1000) / 1000;
    if (Math.abs(rounded - Math.round(rounded)) < 1e-6) return String(Math.round(rounded));
    return String(rounded);
}

const QI_INSIDE = 0;
const QI_LEFT = 1;
const QI_RIGHT = 2;
const QI_BOTTOM = 4;
const QI_TOP = 8;

function qualityClipCode(x, y, domain) {
    let code = QI_INSIDE;
    if (x < domain.xMin) code |= QI_LEFT;
    else if (x > domain.xMax) code |= QI_RIGHT;
    if (y < domain.yMin) code |= QI_BOTTOM;
    else if (y > domain.yMax) code |= QI_TOP;
    return code;
}

function clipQualityLine(x1, y1, x2, y2, domain) {
    let c1 = qualityClipCode(x1, y1, domain);
    let c2 = qualityClipCode(x2, y2, domain);
    for (let i = 0; i < 12; i++) {
        if (!(c1 | c2)) return { x1, y1, x2, y2 };
        if (c1 & c2) return null;
        const code = c1 || c2;
        let x;
        let y;
        if (code & QI_TOP) {
            x = x1 + (x2 - x1) * (domain.yMax - y1) / (y2 - y1);
            y = domain.yMax;
        } else if (code & QI_BOTTOM) {
            x = x1 + (x2 - x1) * (domain.yMin - y1) / (y2 - y1);
            y = domain.yMin;
        } else if (code & QI_RIGHT) {
            y = y1 + (y2 - y1) * (domain.xMax - x1) / (x2 - x1);
            x = domain.xMax;
        } else {
            y = y1 + (y2 - y1) * (domain.xMin - x1) / (x2 - x1);
            x = domain.xMin;
        }
        if (code === c1) {
            x1 = x;
            y1 = y;
            c1 = qualityClipCode(x1, y1, domain);
        } else {
            x2 = x;
            y2 = y;
            c2 = qualityClipCode(x2, y2, domain);
        }
    }
    return null;
}

function drawTensileQualityChart(visiblePoints) {
    const canvas = document.getElementById("tensile-quality-canvas");
    const wrap = canvas?.parentElement;
    if (!canvas || !wrap) return;
    const cssWidth = wrap.clientWidth;
    const cssHeight = 460;
    if (cssWidth < 40) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.style.width = `${cssWidth}px`;
    canvas.style.height = `${cssHeight}px`;
    canvas.width = Math.round(cssWidth * dpr);
    canvas.height = Math.round(cssHeight * dpr);
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.setLineDash([]);
    ctx.clearRect(0, 0, cssWidth, cssHeight);
    ctx.font = "12px Vazirmatn, Tahoma, sans-serif";

    const pad = { left: 78, right: 20, top: 16, bottom: 58 };
    const plotW = cssWidth - pad.left - pad.right;
    const plotH = cssHeight - pad.top - pad.bottom;
    const domain = tensileQualityDomain();
    const xSpan = domain.xMax - domain.xMin || 1;
    const ySpan = domain.yMax - domain.yMin || 1;
    const xToPx = (ys) => pad.left + ((ys - domain.xMin) / xSpan) * plotW;
    const yToPx = (el) => pad.top + ((domain.yMax - el) / ySpan) * plotH;

    const palette = qiChartPalette();
    ctx.fillStyle = palette.plotBg;
    ctx.fillRect(pad.left, pad.top, plotW, plotH);
    ctx.strokeStyle = palette.grid;
    ctx.lineWidth = 1;
    niceTicks(domain.xMin, domain.xMax, 8).forEach((tick) => {
        const x = xToPx(tick);
        ctx.beginPath();
        ctx.moveTo(x, pad.top);
        ctx.lineTo(x, pad.top + plotH);
        ctx.stroke();
        ctx.fillStyle = palette.label;
        ctx.textAlign = "center";
        ctx.textBaseline = "top";
        ctx.fillText(formatTick(tick), x, pad.top + plotH + 6);
    });
    niceTicks(domain.yMin, domain.yMax, 6).forEach((tick) => {
        const y = yToPx(tick);
        ctx.beginPath();
        ctx.moveTo(pad.left, y);
        ctx.lineTo(pad.left + plotW, y);
        ctx.stroke();
        ctx.fillStyle = palette.label;
        ctx.textAlign = "right";
        ctx.textBaseline = "middle";
        ctx.fillText(formatTick(tick), pad.left - 8, y);
    });

    ctx.strokeStyle = palette.axis;
    ctx.strokeRect(pad.left, pad.top, plotW, plotH);

    ctx.fillStyle = palette.title;
    ctx.textAlign = "center";
    ctx.textBaseline = "bottom";
    ctx.fillText("Yield Strength (YS) MPa", pad.left + plotW / 2, cssHeight - 8);
    ctx.save();
    ctx.translate(18, pad.top + plotH / 2);
    ctx.rotate(-Math.PI / 2);
    ctx.textBaseline = "middle";
    ctx.fillText("Elongation (EL) %", 0, 0);
    ctx.restore();

    const y1 = qiReferenceEl(domain.xMin);
    const y2 = qiReferenceEl(domain.xMax);
    const clipped = clipQualityLine(domain.xMin, y1, domain.xMax, y2, domain);
    if (clipped) {
        ctx.save();
        ctx.beginPath();
        ctx.rect(pad.left, pad.top, plotW, plotH);
        ctx.clip();
        ctx.strokeStyle = palette.reference;
        ctx.lineWidth = 2;
        ctx.setLineDash([7, 5]);
        ctx.beginPath();
        ctx.moveTo(xToPx(clipped.x1), yToPx(clipped.y1));
        ctx.lineTo(xToPx(clipped.x2), yToPx(clipped.y2));
        ctx.stroke();
        ctx.restore();
    }

    const order = { UNDEFINED: 0, RED: 1, YELLOW: 2, GREEN: 3 };
    const plotted = visiblePoints || TensileQualityState.points;
    const sorted = [...plotted].sort(
        (a, b) => (order[a.band] ?? 0) - (order[b.band] ?? 0)
    );
    TensileQualityState.hits = [];
    sorted.forEach((point) => {
        const x = xToPx(Number(point.ys));
        const y = yToPx(Number(point.el));
        ctx.beginPath();
        ctx.fillStyle = palette.bands[point.band] || palette.bands.UNDEFINED;
        ctx.strokeStyle = palette.pointStroke;
        ctx.lineWidth = 1.5;
        ctx.setLineDash([]);
        ctx.arc(x, y, 5, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
        TensileQualityState.hits.push({ x, y, point });
    });

    if (!plotted.length) {
        ctx.fillStyle = palette.label;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        const emptyText = TensileQualityState.points.length
            ? "نمونه‌ای در این رنج YS و EL نیست"
            : "نمونه‌ای برای این فیلتر روی نمودار نیست";
        ctx.fillText(emptyText, pad.left + plotW / 2, pad.top + plotH / 2);
    }
}

function onTensileQualityHover(event) {
    const canvas = event.currentTarget;
    const rect = canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    let best = null;
    let bestDist = 12;
    TensileQualityState.hits.forEach((hit) => {
        const dist = Math.hypot(hit.x - x, hit.y - y);
        if (dist <= bestDist) {
            best = hit;
            bestDist = dist;
        }
    });
    const tip = document.getElementById("tensile-quality-tooltip");
    if (!tip) return;
    if (!best) {
        tip.hidden = true;
        return;
    }
    const point = best.point;
    const label = point.sampleLabel ? escapeHtml(point.sampleLabel) : "بدون شماره";
    const qText = point.q == null ? "تعریف نشده" : formatQiNumber(point.q, 3);
    tip.innerHTML = `
        <div>${label}</div>
        <div>EL: ${formatQiNumber(point.el, 2)}%</div>
        <div>YS: ${formatQiNumber(point.ys, 2)} MPa</div>
        <div>Q: ${qText}</div>
    `;
    tip.hidden = false;
    const wrap = canvas.parentElement.getBoundingClientRect();
    let left = event.clientX - wrap.left + 12;
    let top = event.clientY - wrap.top + 12;
    if (left + 160 > wrap.width) left = event.clientX - wrap.left - 168;
    if (top + 90 > wrap.height) top = event.clientY - wrap.top - 96;
    tip.style.left = `${Math.max(8, left)}px`;
    tip.style.top = `${Math.max(8, top)}px`;
}

function hideTensileQualityTooltip() {
    const tip = document.getElementById("tensile-quality-tooltip");
    if (tip) tip.hidden = true;
}

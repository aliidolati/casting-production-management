/**
 * گزارش «روند تست کشش» (Dashboard → گزارش‌ها).
 *
 * همه‌ی محاسبات روند (رگرسیون خطی، شیب، R²، درصد تغییر، جهت، داده‌ی پرت، میانگین متحرک) در Backend انجام
 * می‌شود (GET /api/dashboard/tensile-trend-report). این فایل فقط نتیجه را نمایش می‌دهد: کارت‌های خلاصه،
 * متن تحلیل، جدول‌ها و سه نمودار هم‌محور (UTS / YS / EL) که محور زمانی مشترک دارند.
 *
 * نمودارها روی canvas و با همان توکن‌های تم (--chart-*) رسم می‌شوند تا با Dark/Light هماهنگ بمانند.
 * توابع niceTicks / formatTick از tensile-quality-report.js استفاده می‌شوند (آن فایل زودتر بارگذاری می‌شود).
 */

/* هر پارامتر یک ردیف؛ افزودن پارامتر جدید فقط یک ردیف دیگر می‌خواهد (سری‌ها از API می‌آیند) */
const TREND_PARAMS = [
    { key: "UTS", title: "UTS", unit: "MPa", colorToken: "--chart-1", fallback: "#6B5EF7" },
    { key: "YS", title: "YS", unit: "MPa", colorToken: "--chart-2", fallback: "#0F766E" },
    { key: "EL", title: "EL", unit: "%", colorToken: "--chart-5", fallback: "#2563EB" },
];

const TREND_DIRECTIONS = {
    INCREASING: { label: "افزایشی", en: "Increasing", arrow: "↑" },
    DECREASING: { label: "کاهشی", en: "Decreasing", arrow: "↓" },
    STABLE: { label: "ثابت", en: "Stable", arrow: "→" },
    INSUFFICIENT_DATA: { label: "داده ناکافی", en: "Insufficient Data", arrow: "…" },
};

const TREND_CONFIDENCE_LABELS = { HIGH: "بالا", MEDIUM: "متوسط", LOW: "پایین", NONE: "—" };

const TREND_CHART_HEIGHT = 250;
const TREND_DAY_MS = 86400000;

const TensileTrendState = {
    dateFrom: "",
    dateTo: "",
    dateFromPicker: null,
    dateToPicker: null,
    report: null,
    samplesById: new Map(),
    hoverId: null,
    hits: {},
    loadedOnce: false,
    platesLoaded: false,
    redrawQueued: false,
};

/* ---------- قالب‌بندی عدد ---------- */

function trendFmt(value, maxDigits = 2) {
    if (value === null || value === undefined || value === "") return "—";
    const num = Number(value);
    if (!Number.isFinite(num)) return "—";
    return num.toLocaleString("fa-IR", { minimumFractionDigits: 0, maximumFractionDigits: maxDigits });
}

function trendFmtSigned(value, maxDigits = 2) {
    if (value === null || value === undefined || value === "") return "—";
    const num = Number(value);
    if (!Number.isFinite(num)) return "—";
    return num.toLocaleString("fa-IR", {
        minimumFractionDigits: 0,
        maximumFractionDigits: maxDigits,
        signDisplay: "exceptZero",
    });
}

/** شیب پارامتری مثل EL خیلی کوچک است؛ رقم اعشار بر اساس بزرگی مقدار انتخاب می‌شود */
function trendFmtSlope(value) {
    if (value === null || value === undefined) return "—";
    return trendFmtSigned(value, Math.abs(Number(value)) >= 1 ? 2 : 4);
}

function trendParamMeta(key) {
    return TREND_PARAMS.find((p) => p.key === key) || { key, title: key, unit: "", colorToken: "--chart-1", fallback: "#6B5EF7" };
}

function trendDirection(series) {
    return TREND_DIRECTIONS[series.trend] || TREND_DIRECTIONS.INSUFFICIENT_DATA;
}

function trendBadgeHtml(series) {
    const dir = trendDirection(series);
    return `<span class="trend-badge" data-trend="${escapeHtml(series.trend)}">
        <span class="trend-badge-arrow" aria-hidden="true">${dir.arrow}</span>
        <span>${dir.label}</span>
        <small>${dir.en}</small>
    </span>`;
}

/** برچسب قدرت روند بر اساس R² و آستانه‌های دریافتی از سرور */
function trendStrengthLabel(r2, report) {
    if (r2 === null || r2 === undefined) return { level: "NONE", text: "—" };
    if (r2 >= report.strongR2) return { level: "HIGH", text: "قوی" };
    if (r2 >= report.moderateR2) return { level: "MEDIUM", text: "متوسط" };
    return { level: "LOW", text: "ضعیف" };
}

/** توضیح کوتاه برای حالت‌هایی که کاربر نباید روند را قطعی بپندارد */
function trendNote(series, report) {
    if (series.trend === "INSUFFICIENT_DATA") {
        switch (series.insufficientReason) {
            case "SINGLE_POINT":
                return "فقط یک داده ثبت شده؛ روند قابل محاسبه نیست.";
            case "NO_TIME_SPREAD":
                return "همه داده‌ها در یک زمان ثبت شده‌اند؛ روند زمانی قابل محاسبه نیست.";
            default:
                return "برای این پارامتر داده‌ای در بازه نیست.";
        }
    }
    if (series.confidence === "LOW") {
        if (series.pointCount === 2) return "فقط ۲ داده وجود دارد؛ اطمینان به روند پایین است.";
        if (series.pointCount < report.minReliablePoints) {
            return `تعداد داده کم است (${trendFmt(series.pointCount, 0)} نمونه)؛ اطمینان به روند پایین است.`;
        }
        return "R² پایین است؛ روند با داده‌ها همخوانی کمی دارد و قطعی نیست.";
    }
    if (series.trend === "STABLE" && series.normalizedChangePercent != null) {
        return `تغییر خط روند در بازه ${trendFmt(Math.abs(series.normalizedChangePercent), 2)}٪ است (کمتر از آستانه ${trendFmt(series.stableThresholdPercent, 2)}٪).`;
    }
    return "";
}

/* ---------- راه‌اندازی UI ---------- */

function initTensileTrendReportUi() {
    const dateFromInput = document.getElementById("tensile-trend-date-from");
    const dateToInput = document.getElementById("tensile-trend-date-to");
    if (dateFromInput && typeof attachPersianDatePicker === "function") {
        TensileTrendState.dateFromPicker = attachPersianDatePicker(dateFromInput, {
            onSelect: (d) => {
                TensileTrendState.dateFrom = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                TensileTrendState.dateFrom = "";
            },
        });
    }
    if (dateToInput && typeof attachPersianDatePicker === "function") {
        TensileTrendState.dateToPicker = attachPersianDatePicker(dateToInput, {
            onSelect: (d) => {
                TensileTrendState.dateTo = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                TensileTrendState.dateTo = "";
            },
        });
    }

    document.getElementById("tensile-trend-project")?.addEventListener("change", (e) => {
        loadTensileTrendParts(e.target.value);
    });
    document.getElementById("btn-tensile-trend-run")?.addEventListener("click", () => runTensileTrendReport());
    document.getElementById("btn-tensile-trend-export")?.addEventListener("click", exportTensileTrendCsv);
    document.getElementById("btn-tensile-trend-reset")?.addEventListener("click", () => resetTensileTrendFilters());

    /* نمایش/عدم نمایش خط روند و میانگین متحرک فقط دوباره رسم می‌کند؛ داده از قبل در دست است */
    ["tensile-trend-show-line", "tensile-trend-show-ma"].forEach((id) => {
        document.getElementById(id)?.addEventListener("change", () => scheduleTensileTrendRedraw());
    });
    /* میانگین متحرک در Backend محاسبه می‌شود؛ تغییر پنجره، گزارش را دوباره می‌گیرد */
    document.getElementById("tensile-trend-ma-window")?.addEventListener("change", () => {
        if (TensileTrendState.report) runTensileTrendReport();
    });

    const plateDetails = document.getElementById("tensile-trend-plate-details");
    if (plateDetails) {
        document.addEventListener("click", (event) => {
            if (!(event.target instanceof Element) || !event.target.closest("#tensile-trend-plate-details")) {
                plateDetails.open = false;
            }
        });
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") plateDetails.open = false;
        });
    }

    buildTensileTrendChartSkeleton();

    const redrawIfVisible = () => {
        const panel = document.getElementById("dashboard-tab-tensile-trend-report");
        if (panel && !panel.hidden) scheduleTensileTrendRedraw();
    };
    window.addEventListener("resize", redrawIfVisible);
    /* canvas رنگ خودش را نگه می‌دارد؛ با تغییر تم باید دوباره رسم شود */
    document.addEventListener("themechange", redrawIfVisible);
}

/** سه نمودار یک‌بار ساخته می‌شوند؛ بعد از هر اجرا فقط رسم دوباره انجام می‌شود */
function buildTensileTrendChartSkeleton() {
    const container = document.getElementById("tensile-trend-charts");
    if (!container || container.dataset.ready) return;
    container.dataset.ready = "1";
    container.innerHTML =
        TREND_PARAMS.map(
            (param) => `
        <figure class="trend-chart" data-param="${param.key}">
            <figcaption class="trend-chart-head">
                <span class="trend-chart-title">${param.title} Trend</span>
                <span class="trend-chart-unit">${escapeHtml(param.unit)}</span>
                <span class="trend-chart-badge" data-role="badge"></span>
            </figcaption>
            <canvas role="img" aria-label="نمودار روند ${param.title} در برابر تاریخ تست"></canvas>
        </figure>`
        ).join("") + `<div class="trend-tooltip" id="tensile-trend-tooltip" hidden></div>`;

    container.querySelectorAll(".trend-chart").forEach((figure) => {
        const canvas = figure.querySelector("canvas");
        const key = figure.dataset.param;
        canvas.addEventListener("mousemove", (event) => onTensileTrendHover(event, key));
        canvas.addEventListener("mouseleave", () => setTensileTrendHover(null));
    });

    const legend = document.getElementById("tensile-trend-legend");
    if (legend) {
        legend.innerHTML = `
            <span class="trend-legend-item"><i class="trend-legend-dot"></i>Data Point</span>
            <span class="trend-legend-item"><i class="trend-legend-line is-trend"></i>Trend Line</span>
            <span class="trend-legend-item"><i class="trend-legend-line is-ma"></i>Moving Average</span>
            <span class="trend-legend-item"><i class="trend-legend-ring"></i>Outlier</span>
            <span class="trend-legend-item"><i class="trend-legend-line is-limit"></i>حداقل مجاز</span>`;
    }
}

async function openTensileTrendReport() {
    if (typeof ensureDashboardProjects === "function") {
        await ensureDashboardProjects();
    }
    await ensureTensileTrendPlates();
    if (!TensileTrendState.loadedOnce) {
        TensileTrendState.loadedOnce = true;
        await runTensileTrendReport();
        return;
    }
    scheduleTensileTrendRedraw();
}

async function ensureTensileTrendPlates() {
    if (TensileTrendState.platesLoaded) return;
    const container = document.getElementById("tensile-trend-plates");
    if (!container || typeof renderMultiFilterOptions !== "function") return;
    try {
        const labels = await TensileTestAPI.getSampleLabels();
        renderMultiFilterOptions(
            "tensile-trend-plates",
            (labels || []).map((label) => ({ value: label, label }))
        );
        TensileTrendState.platesLoaded = true;
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function loadTensileTrendParts(projectId) {
    const select = document.getElementById("tensile-trend-part");
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

function resetTensileTrendFilters() {
    const project = document.getElementById("tensile-trend-project");
    if (project) project.value = "";
    if (typeof clearMultiFilter === "function") clearMultiFilter("tensile-trend-plates");
    TensileTrendState.dateFrom = "";
    TensileTrendState.dateTo = "";
    TensileTrendState.dateFromPicker?.clear();
    TensileTrendState.dateToPicker?.clear();
    loadTensileTrendParts("");
    runTensileTrendReport();
}

function setTensileTrendError(message) {
    const box = document.getElementById("tensile-trend-error");
    if (!box) return;
    box.textContent = message || "";
    box.style.display = message ? "block" : "none";
}

async function runTensileTrendReport() {
    setTensileTrendError("");
    TensileTrendState.report = null;
    const exportBtn = document.getElementById("btn-tensile-trend-export");
    if (exportBtn) exportBtn.disabled = true;
    const { dateFrom, dateTo } = TensileTrendState;
    if (dateFrom && dateTo && dateTo < dateFrom) {
        setTensileTrendError("تاریخ پایان نمی‌تواند قبل از تاریخ شروع باشد");
        return;
    }

    const runBtn = document.getElementById("btn-tensile-trend-run");
    const content = document.getElementById("tensile-trend-content");
    if (runBtn) runBtn.disabled = true;
    setElementLoading(content, true);
    try {
        const report = await DashboardAPI.getTensileTrendReport({
            projectId: document.getElementById("tensile-trend-project")?.value || "",
            pieceDefinitionId: document.getElementById("tensile-trend-part")?.value || "",
            sampleLabels: typeof getMultiFilterValues === "function" ? getMultiFilterValues("tensile-trend-plates") : [],
            dateFrom,
            dateTo,
            movingAverageWindow: Number(document.getElementById("tensile-trend-ma-window")?.value) || null,
        });
        TensileTrendState.report = report;
        if (exportBtn) exportBtn.disabled = !report.samples?.length;
        TensileTrendState.samplesById = new Map((report.samples || []).map((s) => [s.sampleId, s]));
        TensileTrendState.hoverId = null;
        renderTensileTrendReport();
    } catch (error) {
        setTensileTrendError(error.message || "خطا در اجرای گزارش");
        showToast(error.message, "error");
    } finally {
        setElementLoading(content, false);
        if (runBtn) runBtn.disabled = false;
    }
}

function exportTensileTrendCsv() {
    const samples = TensileTrendState.report?.samples || [];
    if (!samples.length) {
        showToast("ابتدا گزارش را اجرا کنید", "error");
        return;
    }
    const lines = [["کد پروژه", "نام قطعه", "شماره قطعه", "شماره تست پلیت", "تاریخ تست", "UTS", "YS", "EL"].join(",")];
    samples.forEach(sample => lines.push([sample.projectCode, sample.pieceName, sample.pieceNumber,
        sample.sampleLabel, formatDateTime(sample.testedAt), sample.uts, sample.ys, sample.el]
        .map(csvEscape).join(",")));
    downloadCsv(`tensile-trend-${new Date().toISOString().slice(0, 10)}.csv`, lines);
}

/* ---------- نمایش نتیجه ---------- */

function renderTensileTrendReport() {
    const report = TensileTrendState.report;
    const content = document.getElementById("tensile-trend-content");
    if (!report || !content) return;

    content.hidden = false;
    content.classList.toggle("trend-empty", report.sampleCount === 0);

    renderTensileTrendMeta(report);
    renderTensileTrendCards(report);
    renderTensileTrendSummary(report);
    renderTensileTrendTables(report);
    requestAnimationFrame(() => drawAllTensileTrendCharts());
}

function renderTensileTrendMeta(report) {
    const meta = document.getElementById("tensile-trend-meta");
    if (!meta) return;
    if (!report.sampleCount) {
        meta.innerHTML = `<div class="trend-empty-state">در این فیلتر هیچ نمونه تست کششی ثبت نشده است. فیلترها یا بازه تاریخ را تغییر دهید.</div>`;
        return;
    }
    const times = report.samples.map((s) => Date.parse(s.testedAt)).filter(Number.isFinite);
    const first = Math.min(...times);
    const last = Math.max(...times);
    const chips = [
        `<span class="dashboard-filter-chip">تعداد نمونه: ${trendFmt(report.sampleCount, 0)}</span>`,
        `<span class="dashboard-filter-chip">اولین تست: ${escapeHtml(formatDate(new Date(first).toISOString()))}</span>`,
        `<span class="dashboard-filter-chip">آخرین تست: ${escapeHtml(formatDate(new Date(last).toISOString()))}</span>`,
    ];
    meta.innerHTML = chips.join("");
}

function renderTensileTrendCards(report) {
    const container = document.getElementById("tensile-trend-cards");
    if (!container) return;
    container.innerHTML = report.series.map((series) => {
        const meta = trendParamMeta(series.parameter);
        const strength = trendStrengthLabel(series.r2, report);
        const note = trendNote(series, report);
        const hasTrend = series.trend !== "INSUFFICIENT_DATA";
        return `
        <article class="trend-card" data-trend="${escapeHtml(series.trend)}" aria-label="روند ${escapeHtml(series.parameter)}">
            <header class="trend-card-head">
                <span class="trend-card-param">${escapeHtml(series.parameter)}</span>
                ${trendBadgeHtml(series)}
            </header>
            <div class="trend-card-change" dir="ltr" title="درصد تغییر بین ابتدا و انتهای بازه">
                ${series.percentageChange == null ? "—" : `${trendFmtSigned(series.percentageChange, 2)}%`}
            </div>
            <dl class="trend-card-stats">
                <div><dt>میانگین</dt><dd dir="ltr">${trendFmt(series.average, 2)} ${escapeHtml(meta.unit)}</dd></div>
                <div><dt>اولین مقدار</dt><dd dir="ltr">${trendFmt(series.firstValue, 2)}</dd></div>
                <div><dt>آخرین مقدار</dt><dd dir="ltr">${trendFmt(series.lastValue, 2)}</dd></div>
                <div><dt>شیب روند</dt><dd dir="ltr">${hasTrend ? `${trendFmtSlope(series.slope)} ${escapeHtml(series.slopeUnit)}` : "—"}</dd></div>
                <div><dt>R²</dt><dd dir="ltr">${hasTrend ? trendFmt(series.r2, 2) : "—"}
                    ${hasTrend && series.r2 != null ? `<span class="trend-strength" data-level="${strength.level}">${strength.text}</span>` : ""}</dd></div>
                <div><dt>تعداد نقاط</dt><dd>${trendFmt(series.pointCount, 0)}</dd></div>
            </dl>
            ${note ? `<p class="trend-card-note">${escapeHtml(note)}</p>` : ""}
        </article>`;
    }).join("");
}

function trendJoinNames(names) {
    if (names.length <= 1) return names.join("");
    return `${names.slice(0, -1).join("، ")} و ${names[names.length - 1]}`;
}

/** خلاصه‌ی متنی کاملاً از نتیجه‌ی واقعی سری‌ها ساخته می‌شود */
function buildTensileTrendSummaryText(report) {
    if (!report.sampleCount) return "";
    const groups = { INCREASING: [], DECREASING: [], STABLE: [], INSUFFICIENT_DATA: [] };
    report.series.forEach((s) => (groups[s.trend] || groups.INSUFFICIENT_DATA).push(s.parameter));

    const clause = (direction, names) => {
        const subject = trendJoinNames(names);
        const plural = names.length > 1;
        switch (direction) {
            case "INCREASING":
                return `${subject} روند افزایشی ${plural ? "داشته‌اند" : "داشته است"}`;
            case "DECREASING":
                return `${subject} روند کاهشی ${plural ? "داشته‌اند" : "داشته است"}`;
            case "STABLE":
                return `${subject} تقریباً ثابت ${plural ? "بوده‌اند" : "بوده است"}`;
            default:
                return `برای ${subject} داده کافی برای تحلیل روند وجود ندارد`;
        }
    };

    const clauses = ["INCREASING", "DECREASING", "STABLE", "INSUFFICIENT_DATA"]
        .filter((direction) => groups[direction].length)
        .map((direction) => clause(direction, groups[direction]));

    let text = `در بازه انتخاب‌شده، ${clauses[0]}`;
    clauses.slice(1).forEach((c, i) => {
        text += i === 0 ? `، در حالی که ${c}` : ` و ${c}`;
    });
    text += ".";

    const uncertain = report.series
        .filter((s) => (s.trend === "INCREASING" || s.trend === "DECREASING") && s.confidence === "LOW")
        .map((s) => s.parameter);
    if (uncertain.length) {
        text += ` توجه: اطمینان روند ${trendJoinNames(uncertain)} پایین است (R² کم یا تعداد داده اندک).`;
    }
    return text;
}

function renderTensileTrendSummary(report) {
    const list = document.getElementById("tensile-trend-summary-list");
    const text = document.getElementById("tensile-trend-summary-text");
    if (list) {
        list.innerHTML = report.series.map((series) => `
            <li class="trend-summary-item" data-trend="${escapeHtml(series.trend)}">
                <span class="trend-summary-param">${escapeHtml(series.parameter)}</span>
                ${trendBadgeHtml(series)}
            </li>`).join("");
    }
    if (text) text.textContent = buildTensileTrendSummaryText(report);
}

function renderTensileTrendTables(report) {
    const compare = document.getElementById("tensile-trend-compare-body");
    if (compare) {
        compare.innerHTML = report.series.map((s) => `
            <tr>
                <td><strong>${escapeHtml(s.parameter)}</strong></td>
                <td dir="ltr">${trendFmt(s.firstValue, 2)}</td>
                <td dir="ltr">${trendFmt(s.lastValue, 2)}</td>
                <td dir="ltr">${s.percentageChange == null ? "—" : `${trendFmtSigned(s.percentageChange, 2)}%`}</td>
            </tr>`).join("");
    }

    const details = document.getElementById("tensile-trend-details-body");
    if (details) {
        details.innerHTML = report.series.map((s) => {
            const hasTrend = s.trend !== "INSUFFICIENT_DATA";
            const strength = trendStrengthLabel(s.r2, report);
            return `
            <tr>
                <td><strong>${escapeHtml(s.parameter)}</strong></td>
                <td>${trendBadgeHtml(s)}</td>
                <td>${trendFmt(s.pointCount, 0)}</td>
                <td dir="ltr">${trendFmt(s.average, 2)} ${escapeHtml(s.unit)}</td>
                <td dir="ltr">${hasTrend ? `${trendFmtSlope(s.slope)} ${escapeHtml(s.slopeUnit)}` : "—"}</td>
                <td dir="ltr">${hasTrend ? trendFmt(s.r2, 2) : "—"}${hasTrend && s.r2 != null ? ` (${strength.text})` : ""}</td>
                <td>${hasTrend ? TREND_CONFIDENCE_LABELS[s.confidence] || "—" : "—"}</td>
                <td dir="ltr">${trendFmt(s.stableThresholdPercent, 2)}%</td>
                <td>${trendFmt(s.outlierCount, 0)}</td>
            </tr>`;
        }).join("");
    }
}

/* ---------- نمودارها ---------- */

function scheduleTensileTrendRedraw() {
    if (TensileTrendState.redrawQueued) return;
    TensileTrendState.redrawQueued = true;
    requestAnimationFrame(() => {
        TensileTrendState.redrawQueued = false;
        drawAllTensileTrendCharts();
    });
}

function trendChartPalette() {
    const token = (name, fallback) => (window.ThemeManager ? ThemeManager.cssVar(name, fallback) : fallback);
    return {
        plotBg: token("--chart-plot-bg", "#FFFFFF"),
        grid: token("--chart-grid", "#E6E2F0"),
        axis: token("--chart-axis", "#1B1A2E"),
        label: token("--chart-label", "#6B6880"),
        title: token("--chart-title", "#1B1A2E"),
        trend: token("--chart-reference", "#5346E0"),
        movingAverage: token("--chart-3", "#B45309"),
        limit: token("--chart-danger", "#DC2626"),
        outlier: token("--chart-danger", "#DC2626"),
        pointStroke: token("--chart-point-stroke", "#FFFFFF"),
        series: (param) => token(param.colorToken, param.fallback),
    };
}

/** دامنه‌ی زمانی مشترک سه نمودار؛ از همه‌ی نمونه‌ها گرفته می‌شود تا نمودارها دقیقاً هم‌تراز باشند */
function trendTimeDomain(report) {
    let min = Infinity;
    let max = -Infinity;
    (report.samples || []).forEach((s) => {
        const t = Date.parse(s.testedAt);
        if (Number.isFinite(t)) {
            min = Math.min(min, t);
            max = Math.max(max, t);
        }
    });
    if (!Number.isFinite(min)) return null;
    const pad = Math.max((max - min) * 0.04, TREND_DAY_MS / 2);
    return { min: min - pad, max: max + pad };
}

function trendLocalIsoDate(date) {
    const m = String(date.getMonth() + 1).padStart(2, "0");
    const d = String(date.getDate()).padStart(2, "0");
    return `${date.getFullYear()}-${m}-${d}`;
}

/** برچسب‌های محور زمان روی نیمه‌شب‌های محلی با گام «روز» مناسب */
function trendTimeTicks(domain, maxTicks) {
    const spanDays = (domain.max - domain.min) / TREND_DAY_MS;
    const steps = [1, 2, 3, 5, 7, 10, 14, 21, 30, 45, 60, 90, 120, 180, 365, 730, 1825];
    const step = steps.find((s) => spanDays / s <= maxTicks) || steps[steps.length - 1];
    const start = new Date(domain.min);
    start.setHours(24, 0, 0, 0);
    const ticks = [];
    for (let k = 0; ; k += 1) {
        const tick = new Date(start.getFullYear(), start.getMonth(), start.getDate() + k * step);
        if (tick.getTime() > domain.max) break;
        ticks.push(tick.getTime());
        if (k > 200) break;
    }
    return ticks;
}

function trendYDomain(series, options) {
    const values = series.points.map((p) => p.value);
    if (options.showLine) series.points.forEach((p) => p.trendValue != null && values.push(p.trendValue));
    if (options.showMa) series.points.forEach((p) => p.movingAverage != null && values.push(p.movingAverage));
    if (!values.length) return null;

    let min = Math.min(...values);
    let max = Math.max(...values);
    const mean = (min + max) / 2;

    /* حد مجاز فقط وقتی وارد محدوده می‌شود که به داده نزدیک باشد؛ وگرنه داده را فشرده می‌کند */
    let limit = series.limits && series.limits.min != null ? Number(series.limits.min) : null;
    let limitVisible = false;
    if (limit != null && Number.isFinite(limit)) {
        const distance = limit < min ? min - limit : limit > max ? limit - max : 0;
        const tolerance = Math.max((max - min) * 1.5, Math.abs(mean) * 0.2);
        if (distance <= tolerance) {
            limitVisible = true;
            min = Math.min(min, limit);
            max = Math.max(max, limit);
        }
    } else {
        limit = null;
    }

    const pad = (max - min) * 0.1 || Math.max(Math.abs(max) * 0.05, 1);
    return { min: min - pad, max: max + pad, limit, limitVisible };
}

function drawAllTensileTrendCharts() {
    const report = TensileTrendState.report;
    if (!report || !report.sampleCount) return;
    const domain = trendTimeDomain(report);
    if (!domain) return;
    const options = {
        showLine: document.getElementById("tensile-trend-show-line")?.checked ?? true,
        showMa: document.getElementById("tensile-trend-show-ma")?.checked ?? false,
    };
    const palette = trendChartPalette();
    report.series.forEach((series) => drawTensileTrendChart(series, domain, options, palette));
}

function drawTensileTrendChart(series, timeDomain, options, palette) {
    const figure = document.querySelector(`#tensile-trend-charts .trend-chart[data-param="${series.parameter}"]`);
    const canvas = figure?.querySelector("canvas");
    if (!figure || !canvas) return;

    const badge = figure.querySelector('[data-role="badge"]');
    if (badge) badge.innerHTML = trendBadgeHtml(series);

    const cssWidth = figure.clientWidth;
    const cssHeight = TREND_CHART_HEIGHT;
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

    const meta = trendParamMeta(series.parameter);
    const color = palette.series(meta);
    const pad = { left: 70, right: 20, top: 12, bottom: 34 };
    const plotW = cssWidth - pad.left - pad.right;
    const plotH = cssHeight - pad.top - pad.bottom;

    ctx.fillStyle = palette.plotBg;
    ctx.fillRect(pad.left, pad.top, plotW, plotH);

    const xSpan = timeDomain.max - timeDomain.min || 1;
    const xToPx = (t) => pad.left + ((t - timeDomain.min) / xSpan) * plotW;

    /* محور زمان (مشترک بین هر سه نمودار) */
    ctx.lineWidth = 1;
    ctx.strokeStyle = palette.grid;
    trendTimeTicks(timeDomain, Math.max(2, Math.floor(plotW / 95))).forEach((t) => {
        const x = xToPx(t);
        ctx.beginPath();
        ctx.moveTo(x, pad.top);
        ctx.lineTo(x, pad.top + plotH);
        ctx.stroke();
        ctx.fillStyle = palette.label;
        ctx.textAlign = "center";
        ctx.textBaseline = "top";
        const label = typeof isoToJalaliString === "function"
            ? isoToJalaliString(trendLocalIsoDate(new Date(t)))
            : trendLocalIsoDate(new Date(t));
        ctx.fillText(label, x, pad.top + plotH + 6);
    });

    TensileTrendState.hits[series.parameter] = [];
    const yDomain = series.points.length ? trendYDomain(series, options) : null;

    if (!yDomain) {
        ctx.strokeStyle = palette.axis;
        ctx.strokeRect(pad.left, pad.top, plotW, plotH);
        ctx.fillStyle = palette.label;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText("داده‌ای برای این پارامتر در بازه نیست", pad.left + plotW / 2, pad.top + plotH / 2);
        return;
    }

    const ySpan = yDomain.max - yDomain.min || 1;
    const yToPx = (v) => pad.top + ((yDomain.max - v) / ySpan) * plotH;

    niceTicks(yDomain.min, yDomain.max, 5).forEach((tick) => {
        const y = yToPx(tick);
        ctx.strokeStyle = palette.grid;
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
    ctx.textBaseline = "middle";
    ctx.save();
    ctx.translate(16, pad.top + plotH / 2);
    ctx.rotate(-Math.PI / 2);
    ctx.fillText(`${meta.title} (${meta.unit})`, 0, 0);
    ctx.restore();

    ctx.save();
    ctx.beginPath();
    ctx.rect(pad.left, pad.top, plotW, plotH);
    ctx.clip();

    /* حد مجاز (فعلاً فقط حداقل از تعریف قطعه؛ حداکثر/Target بعداً به همین شکل اضافه می‌شود) */
    if (yDomain.limitVisible) {
        const y = yToPx(yDomain.limit);
        ctx.strokeStyle = palette.limit;
        ctx.lineWidth = 1.5;
        ctx.setLineDash([2, 4]);
        ctx.beginPath();
        ctx.moveTo(pad.left, y);
        ctx.lineTo(pad.left + plotW, y);
        ctx.stroke();
        ctx.setLineDash([]);
        ctx.fillStyle = palette.limit;
        ctx.textAlign = "left";
        ctx.textBaseline = "bottom";
        ctx.fillText(`Min ${formatTick(yDomain.limit)}`, pad.left + 6, y - 3);
    }

    const pts = series.points.map((p) => ({
        id: p.sampleId,
        x: xToPx(Date.parse(p.testedAt)),
        y: yToPx(p.value),
        point: p,
    }));

    /* خط اتصال نقاط واقعی (کم‌رنگ) */
    if (pts.length > 1) {
        ctx.strokeStyle = color;
        ctx.globalAlpha = 0.35;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        pts.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
        ctx.stroke();
        ctx.globalAlpha = 1;
    }

    if (options.showMa) {
        const maPts = pts.filter((p) => p.point.movingAverage != null);
        if (maPts.length > 1) {
            ctx.strokeStyle = palette.movingAverage;
            ctx.lineWidth = 2;
            ctx.setLineDash([6, 4]);
            ctx.beginPath();
            maPts.forEach((p, i) => {
                const y = yToPx(p.point.movingAverage);
                i === 0 ? ctx.moveTo(p.x, y) : ctx.lineTo(p.x, y);
            });
            ctx.stroke();
            ctx.setLineDash([]);
        }
    }

    if (options.showLine) {
        const withTrend = pts.filter((p) => p.point.trendValue != null);
        if (withTrend.length > 1) {
            const a = withTrend[0];
            const b = withTrend[withTrend.length - 1];
            ctx.strokeStyle = palette.trend;
            ctx.lineWidth = 2.5;
            ctx.beginPath();
            ctx.moveTo(a.x, yToPx(a.point.trendValue));
            ctx.lineTo(b.x, yToPx(b.point.trendValue));
            ctx.stroke();
        }
    }
    ctx.restore();

    /* نقاط واقعی؛ داده‌ی پرت فقط با حلقه علامت می‌خورد و حذف نمی‌شود */
    pts.forEach((p) => {
        if (p.point.outlier) {
            ctx.strokeStyle = palette.outlier;
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.arc(p.x, p.y, 9, 0, Math.PI * 2);
            ctx.stroke();
        }
        ctx.beginPath();
        ctx.fillStyle = color;
        ctx.strokeStyle = palette.pointStroke;
        ctx.lineWidth = 1.5;
        ctx.arc(p.x, p.y, 4.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
        TensileTrendState.hits[series.parameter].push({ x: p.x, y: p.y, id: p.id });
    });

    /* نمونه‌ی زیر ماوس در هر سه نمودار هم‌زمان مشخص می‌شود */
    const hoverId = TensileTrendState.hoverId;
    if (hoverId) {
        const sample = TensileTrendState.samplesById.get(hoverId);
        if (sample) {
            const x = xToPx(Date.parse(sample.testedAt));
            ctx.strokeStyle = palette.axis;
            ctx.globalAlpha = 0.45;
            ctx.lineWidth = 1;
            ctx.setLineDash([4, 3]);
            ctx.beginPath();
            ctx.moveTo(x, pad.top);
            ctx.lineTo(x, pad.top + plotH);
            ctx.stroke();
            ctx.setLineDash([]);
            ctx.globalAlpha = 1;
        }
        const hovered = pts.find((p) => p.id === hoverId);
        if (hovered) {
            ctx.strokeStyle = palette.title;
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.arc(hovered.x, hovered.y, 7.5, 0, Math.PI * 2);
            ctx.stroke();
        }
    }
}

/* ---------- Hover / Tooltip ---------- */

function setTensileTrendHover(sampleId) {
    if (TensileTrendState.hoverId === sampleId) return;
    TensileTrendState.hoverId = sampleId;
    if (!sampleId) hideTensileTrendTooltip();
    scheduleTensileTrendRedraw();
}

function hideTensileTrendTooltip() {
    const tip = document.getElementById("tensile-trend-tooltip");
    if (tip) tip.hidden = true;
}

function onTensileTrendHover(event, parameter) {
    const canvas = event.currentTarget;
    const rect = canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    let best = null;
    let bestDist = 16;
    (TensileTrendState.hits[parameter] || []).forEach((hit) => {
        const dist = Math.hypot(hit.x - x, hit.y - y);
        if (dist <= bestDist) {
            best = hit;
            bestDist = dist;
        }
    });
    if (!best) {
        setTensileTrendHover(null);
        return;
    }
    setTensileTrendHover(best.id);
    showTensileTrendTooltip(event, best.id, parameter);
}

function showTensileTrendTooltip(event, sampleId, parameter) {
    const tip = document.getElementById("tensile-trend-tooltip");
    const sample = TensileTrendState.samplesById.get(sampleId);
    const series = TensileTrendState.report?.series.find((s) => s.parameter === parameter);
    const point = series?.points.find((p) => p.sampleId === sampleId);
    if (!tip || !sample || !point) return;

    const valueRow = (param, value) => {
        const meta = trendParamMeta(param);
        const active = param === parameter ? " is-active" : "";
        return `<div class="trend-tooltip-row${active}"><span>${param}</span><span dir="ltr">${trendFmt(value, 2)} ${escapeHtml(meta.unit)}</span></div>`;
    };
    const where = [sample.projectCode, sample.pieceName, sample.pieceNumber != null ? `#${sample.pieceNumber}` : ""]
        .filter((v) => v !== null && v !== undefined && v !== "")
        .map((v) => escapeHtml(String(v)))
        .join(" · ");

    tip.innerHTML = `
        <div class="trend-tooltip-date" dir="ltr">${escapeHtml(formatDateTime(sample.testedAt))}</div>
        ${sample.sampleLabel ? `<div class="trend-tooltip-meta">پلیت: <bdi>${escapeHtml(sample.sampleLabel)}</bdi></div>` : ""}
        ${where ? `<div class="trend-tooltip-meta" dir="auto">${where}</div>` : ""}
        ${valueRow("UTS", sample.uts)}
        ${valueRow("YS", sample.ys)}
        ${valueRow("EL", sample.el)}
        ${point.trendValue != null ? `<div class="trend-tooltip-meta">خط روند ${escapeHtml(parameter)}: <span dir="ltr">${trendFmt(point.trendValue, 2)}</span></div>` : ""}
        ${point.movingAverage != null ? `<div class="trend-tooltip-meta">میانگین متحرک: <span dir="ltr">${trendFmt(point.movingAverage, 2)}</span></div>` : ""}
        ${point.outlier ? `<div class="trend-tooltip-outlier">داده پرت (حذف نشده است)</div>` : ""}
    `;
    tip.hidden = false;

    const wrap = tip.parentElement.getBoundingClientRect();
    let left = event.clientX - wrap.left + 14;
    let top = event.clientY - wrap.top + 14;
    if (left + tip.offsetWidth > wrap.width) left = event.clientX - wrap.left - tip.offsetWidth - 14;
    if (top + tip.offsetHeight > wrap.height) top = event.clientY - wrap.top - tip.offsetHeight - 14;
    tip.style.left = `${Math.max(4, left)}px`;
    tip.style.top = `${Math.max(4, top)}px`;
}

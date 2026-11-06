/**
 * tracking.js
 * منطق مربوط به بخش "ردیابی قطعات": نمایش همه قطعات سیستم،
 * فیلتر بر اساس مرحله (Stage)، و کارت‌های آماری بالای صفحه.
 */

// آیکن‌های سفارشی مراحل (گزینه B تأییدشده)
const STAGE_ICON_RADIOGRAPHY = `
<svg class="stage-icon-svg" viewBox="0 0 40 40" aria-hidden="true" focusable="false">
  <circle cx="20" cy="20" r="19" fill="#F1C40F"/>
  <circle cx="20" cy="20" r="17.5" fill="none" stroke="#1a1a1a" stroke-width="1.2"/>
  <g fill="#1a1a1a" transform="translate(20,20)">
    <circle r="2.4"/>
    <path d="M0-4.2 L-5.2-13.2 A13.5 13.5 0 0 1 5.2-13.2 Z"/>
    <path d="M0-4.2 L-5.2-13.2 A13.5 13.5 0 0 1 5.2-13.2 Z" transform="rotate(120)"/>
    <path d="M0-4.2 L-5.2-13.2 A13.5 13.5 0 0 1 5.2-13.2 Z" transform="rotate(240)"/>
  </g>
</svg>`;

const STAGE_ICON_TENSILE = `<img class="stage-icon-img" src="img/icon-tensile.png" alt="" draggable="false">`;
const STAGE_ICON_WELDING = `<img class="stage-icon-img" src="img/icon-welding.png" alt="" draggable="false">`;

function stageStrokeIcon(inner) {
    return `<svg class="stage-icon-svg stage-stroke-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${inner}</svg>`;
}

const STAGE_ICON_NOT_STARTED = stageStrokeIcon(`<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>`);
const STAGE_ICON_CASTING = stageStrokeIcon(`<path d="M12 3c1 3-1 5-2 6 2 0 4 2 4 4.5a4 4 0 1 1-8 0c0-1.5 1-3 2-4-.5 1.5-.5 3 .5 4 0-3 2-6 3.5-10.5z"/>`);
const STAGE_ICON_ROUGH = stageStrokeIcon(`<path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>`);
const STAGE_ICON_HEAT = stageStrokeIcon(`<path d="M14 14.76V3.5a2.5 2.5 0 0 0-5 0v11.26a4.5 4.5 0 1 0 5 0z"/>`);
const STAGE_ICON_BLAST = stageStrokeIcon(`<path d="M12 12c2.8 0 5-2.2 5-5 0-.6-.4-1-1-1-2.8 0-5 2.2-5 5"/><path d="M12 12c0 2.8 2.2 5 5 5 .6 0 1-.4 1-1 0-2.8-2.2-5-5-5"/><path d="M12 12c-2.8 0-5 2.2-5 5 0 .6.4 1 1 1 2.8 0 5-2.2 5-5"/><path d="M12 12c0-2.8-2.2-5-5-5-.6 0-1 .4-1 1 0 2.8 2.2 5 5 5"/>`);
const STAGE_ICON_READY = stageStrokeIcon(`<circle cx="12" cy="12" r="9"/><path d="m8.5 12 2.5 2.5 4.5-5"/>`);
const STAGE_ICON_DELIVERED = stageStrokeIcon(`<path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/>`);
const STAGE_ICON_SCRAPPED = stageStrokeIcon(`<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/>`);
const STAGE_ICON_ALL = stageStrokeIcon(`<rect x="8" y="2" width="8" height="4" rx="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>`);

const STAGE_DEFINITIONS = [
    { key: "NOT_STARTED", icon: STAGE_ICON_NOT_STARTED, label: "شروع نشده" },
    { key: "CASTING", icon: STAGE_ICON_CASTING, label: "ذوب‌ریزی" },
    { key: "ROUGH_MACHINING", icon: STAGE_ICON_ROUGH, label: "خشن‌تراشی" },
    { key: "RADIOGRAPHY", icon: STAGE_ICON_RADIOGRAPHY, label: "رادیوگرافی", custom: true },
    { key: "WELDING", icon: STAGE_ICON_WELDING, label: "جوشکاری", custom: true },
    { key: "HEAT_TREATMENT", icon: STAGE_ICON_HEAT, label: "عملیات حرارتی" },
    { key: "TENSILE", icon: STAGE_ICON_TENSILE, label: "تست کشش", custom: true },
    { key: "SHOT_BLASTING", icon: STAGE_ICON_BLAST, label: "شات‌بلاست" },
    { key: "READY", icon: STAGE_ICON_READY, label: "آماده تحویل" },
    { key: "DELIVERED", icon: STAGE_ICON_DELIVERED, label: "تحویل داده شد" },
    { key: "SCRAPPED", icon: STAGE_ICON_SCRAPPED, label: "اسقاط" },
];

const TrackingState = {
    pieces: [],
    summary: {
        totalCount: 0,
        inProgressCount: 0,
        notStartedCount: 0,
        deliveredCount: 0,
        stageCounts: {},
    },
    page: 0,
    pageSize: 50,
    total: 0,
    totalPages: 0,
    loadSeq: 0,
    activeFilter: "all", // "all" یا یکی از کلیدهای STAGE_DEFINITIONS
    includeArchived: false,
    searchQuery: "", // عبارت جستجوی متنی
    searchDebounceTimer: null, // تایمر برای debounce جستجو
    pieceNameQuery: "",
    projectCodeQuery: "",
    dateFrom: "", // فیلتر پیشرفته: از تاریخ ثبت (yyyy-mm-dd میلادی، خروجی input[type=date])
    dateTo: "", // فیلتر پیشرفته: تا تاریخ ثبت
    radiographySubStageFilter: "", // فیلتر پیشرفته: فقط وقتی activeFilter برابر RADIOGRAPHY باشد معنا دارد
    heatTreatmentSubStageFilter: "", // فیلتر پیشرفته: فقط وقتی activeFilter برابر HEAT_TREATMENT باشد معنا دارد
    roughMachiningSubStageFilter: "", // فیلتر پیشرفته: فقط وقتی activeFilter برابر ROUGH_MACHINING باشد معنا دارد
    selectedPieceIds: new Set(), // شناسه‌ی قطعاتِ انتخاب‌شده برای عملیات دسته‌جمعی
    sortBy: "", // "" | "pieceNumber" | "processDate" | "stageEnteredAt"
    sortDir: "desc", // "asc" | "desc" — اولین کلیک جدیدترین را بالا می‌آورد
};

function trackingListParams() {
    const params = {
        q: TrackingState.searchQuery,
        includeArchived: TrackingState.includeArchived,
        pieceName: TrackingState.pieceNameQuery,
        projectCode: TrackingState.projectCodeQuery,
        dateFrom: TrackingState.dateFrom,
        dateTo: TrackingState.dateTo,
        sortBy: TrackingState.sortBy,
        sortDir: TrackingState.sortBy ? TrackingState.sortDir : "",
        page: TrackingState.page,
        size: TrackingState.pageSize,
    };
    if (TrackingState.activeFilter !== "all") {
        params.stage = TrackingState.activeFilter;
    }
    if (TrackingState.activeFilter === "RADIOGRAPHY" && TrackingState.radiographySubStageFilter) {
        params.radiographySubStage = TrackingState.radiographySubStageFilter;
    }
    if (TrackingState.activeFilter === "HEAT_TREATMENT" && TrackingState.heatTreatmentSubStageFilter) {
        params.heatTreatmentSubStage = TrackingState.heatTreatmentSubStageFilter;
    }
    if (TrackingState.activeFilter === "ROUGH_MACHINING" && TrackingState.roughMachiningSubStageFilter) {
        params.roughMachiningSubStage = TrackingState.roughMachiningSubStageFilter;
    }
    return params;
}

function trackingSummaryParams() {
    return {
        q: TrackingState.searchQuery,
        includeArchived: TrackingState.includeArchived,
        pieceName: TrackingState.pieceNameQuery,
        projectCode: TrackingState.projectCodeQuery,
        dateFrom: TrackingState.dateFrom,
        dateTo: TrackingState.dateTo,
    };
}

function applyTrackingPageResult(data) {
    TrackingState.pieces = data.items || [];
    /* پاسخ سرور PageResponse است: totalElements — نه total */
    TrackingState.total = data.totalElements ?? data.total ?? 0;
    TrackingState.page = data.page ?? 0;
    TrackingState.pageSize = data.size || TrackingState.pageSize;
    TrackingState.totalPages = data.totalPages ?? 0;
}

/* ===========================
   بارگذاری داده و رندر اولیه
   =========================== */

async function loadTracking(options = {}) {
    const includeSummary = options.includeSummary !== false;
    if (options.resetPage) TrackingState.page = 0;
    if (options.clearSelection !== false) TrackingState.selectedPieceIds.clear();

    const seq = ++TrackingState.loadSeq;
    const tbody = document.getElementById("tracking-table-body");
    showTableLoader(tbody, canUseBulkSelection() ? 7 : 6);
    try {
        const requests = [PieceAPI.listTracking(trackingListParams())];
        if (includeSummary) {
            requests.unshift(PieceAPI.getTrackingSummary(trackingSummaryParams()));
        }
        const results = await Promise.all(requests);
        if (seq !== TrackingState.loadSeq) return;

        if (includeSummary) {
            TrackingState.summary = results[0] || TrackingState.summary;
            applyTrackingPageResult(results[1] || {});
        } else {
            applyTrackingPageResult(results[0] || {});
        }

        if (
            TrackingState.page > 0 &&
            TrackingState.pieces.length === 0 &&
            TrackingState.total > 0 &&
            TrackingState.totalPages > 0
        ) {
            TrackingState.page = TrackingState.totalPages - 1;
            const pageData = await PieceAPI.listTracking(trackingListParams());
            if (seq !== TrackingState.loadSeq) return;
            applyTrackingPageResult(pageData);
        }

        if (includeSummary) {
            renderTrackingMetrics();
            renderStageCards();
        }
        renderTrackingTable();
    } catch (error) {
        if (seq !== TrackingState.loadSeq) return;
        showToast(error.message, "error");
    }
}

/* ===========================
   متریک‌های کلی بالای صفحه
   =========================== */

function renderTrackingMetrics() {
    const summary = TrackingState.summary || {};
    const total = summary.totalCount || 0;
    const inProgress = summary.inProgressCount || 0;
    const notStarted = summary.notStartedCount || 0;
    const delivered = summary.deliveredCount || 0;

    const container = document.getElementById("tracking-metrics");
    container.innerHTML = `
        <div class="metric card-tilt">
            <div class="metric-label">کل قطعات</div>
            <div class="metric-value">${total.toLocaleString("fa-IR")}</div>
            <div class="metric-sub">${TrackingState.includeArchived ? "با احتساب بایگانی‌شده‌ها" : "بدون بایگانی‌شده‌ها"}</div>
        </div>
        <div class="metric card-tilt">
            <div class="metric-label">در جریان تولید</div>
            <div class="metric-value" style="color: var(--color-primary)">${inProgress.toLocaleString("fa-IR")}</div>
            <div class="metric-sub">شروع شده، به‌جز آماده تحویل و تحویل‌شده</div>
        </div>
        <div class="metric card-tilt">
            <div class="metric-label">شروع نشده</div>
            <div class="metric-value" style="color: var(--color-text-muted)">${notStarted.toLocaleString("fa-IR")}</div>
            <div class="metric-sub">در صف تولید</div>
        </div>
        <div class="metric card-tilt">
            <div class="metric-label">تحویل داده شد</div>
            <div class="metric-value" style="color: var(--color-success)">${delivered.toLocaleString("fa-IR")}</div>
            <div class="metric-sub">پایان فرآیند</div>
        </div>
    `;
}

/* ===========================
   کارت‌های مرحله
   =========================== */

function renderStageCards() {
    const summary = TrackingState.summary || {};
    const stageCounts = summary.stageCounts || {};
    const total = summary.totalCount || 0;
    const container = document.getElementById("tracking-stages-row");

    const allCard = `
        <div class="stage-card card-tilt ${TrackingState.activeFilter === "all" ? "active" : ""}" data-filter="all">
            <div class="stage-card-icon" style="background: var(--color-surface2)">${STAGE_ICON_ALL}</div>
            <div class="stage-card-name">همه قطعات</div>
            <span class="stage-card-count" style="background: var(--color-primary-bg); color: var(--color-primary-dark)">${total.toLocaleString("fa-IR")} قطعه</span>
        </div>
    `;

    const stageCards = STAGE_DEFINITIONS.map((stage) => {
        const count = Number(stageCounts[stage.key] || 0);
        const isActive = TrackingState.activeFilter === stage.key;
        const customIcon = stage.custom ? " has-custom-icon" : "";
        return `
            <div class="stage-card card-tilt ${isActive ? "active" : ""}" data-filter="${stage.key}">
                <div class="stage-card-icon stage-${stage.key}${customIcon}">${stage.icon}</div>
                <div class="stage-card-name">${stage.label}</div>
                <span class="stage-card-count stage-${stage.key}">${count.toLocaleString("fa-IR")} قطعه</span>
            </div>
        `;
    }).join("");

    container.innerHTML = allCard + stageCards;

    container.querySelectorAll(".stage-card").forEach((card) => {
        card.addEventListener("click", () => {
            TrackingState.activeFilter = card.dataset.filter;
            renderStageCards();
            loadTracking({ includeSummary: false, resetPage: true });
        });
    });
}

/* ===========================
   فیلترهای پیشرفته (تاریخ ثبت و وجود عیب رادیوگرافی)
   =========================== */

/**
 * تاریخ ورود قطعه به وضعیت فعلی‌اش (currentStageEnteredAt) را با بازه‌ی انتخاب‌شده
 * در فیلتر پیشرفته مقایسه می‌کند. TrackingState.dateFrom/dateTo رشته‌ی میلادی yyyy-mm-dd هستند
 * (تبدیل‌شده از تاریخ شمسیِ انتخابی کاربر در تقویم).
 */
function isWithinDateRange(stageEnteredAt) {
    if (!TrackingState.dateFrom && !TrackingState.dateTo) return true;

    const enteredDate = new Date(stageEnteredAt);
    enteredDate.setHours(0, 0, 0, 0);

    if (TrackingState.dateFrom) {
        const from = new Date(TrackingState.dateFrom);
        if (enteredDate < from) return false;
    }

    if (TrackingState.dateTo) {
        const to = new Date(TrackingState.dateTo);
        if (enteredDate > to) return false;
    }

    return true;
}

function applyAdvancedFilters(pieces) {
    return pieces.filter((p) => {
        if (!isWithinDateRange(p.currentStageEnteredAt)) return false;
        // فیلتر زیرمرحله فقط وقتی معنا دارد که مرحله‌ی «رادیوگرافی» انتخاب شده باشد؛
        // فقط قطعاتِ خودِ مرحله‌ی رادیوگرافی را محدود می‌کند، بدون تأثیر روی شمارش سایر مراحل
        if (
            TrackingState.activeFilter === "RADIOGRAPHY" &&
            TrackingState.radiographySubStageFilter &&
            p.currentStage === "RADIOGRAPHY" &&
            p.radiographySubStage !== TrackingState.radiographySubStageFilter
        ) {
            return false;
        }
        if (
            TrackingState.activeFilter === "HEAT_TREATMENT" &&
            TrackingState.heatTreatmentSubStageFilter &&
            p.currentStage === "HEAT_TREATMENT" &&
            p.heatTreatmentSubStage !== TrackingState.heatTreatmentSubStageFilter
        ) {
            return false;
        }
        if (
            TrackingState.activeFilter === "ROUGH_MACHINING" &&
            TrackingState.roughMachiningSubStageFilter &&
            p.currentStage === "ROUGH_MACHINING" &&
            p.roughMachiningSubStage !== TrackingState.roughMachiningSubStageFilter
        ) {
            return false;
        }
        return true;
    });
}

/**
 * فیلدهای فیلتر زیرمرحله فقط وقتی مرحله‌ی مربوطه به‌عنوان فیلتر فعال انتخاب شده باشد
 * نمایش داده می‌شوند؛ در غیر این صورت مقدارشان هم بازنشانی می‌شود.
 */
function updateSubStageFilterVisibility() {
    const radiographySelect = document.getElementById("filter-radiography-substage");
    const heatTreatmentSelect = document.getElementById("filter-heat-treatment-substage");
    const roughMachiningSelect = document.getElementById("filter-rough-machining-substage");

    const isRadiography = TrackingState.activeFilter === "RADIOGRAPHY";
    const isHeatTreatment = TrackingState.activeFilter === "HEAT_TREATMENT";
    const isRoughMachining = TrackingState.activeFilter === "ROUGH_MACHINING";

    if (radiographySelect) {
        radiographySelect.style.display = isRadiography ? "" : "none";
        if (!isRadiography && TrackingState.radiographySubStageFilter) {
            TrackingState.radiographySubStageFilter = "";
            radiographySelect.value = "";
        }
    }

    if (heatTreatmentSelect) {
        heatTreatmentSelect.style.display = isHeatTreatment ? "" : "none";
        if (!isHeatTreatment && TrackingState.heatTreatmentSubStageFilter) {
            TrackingState.heatTreatmentSubStageFilter = "";
            heatTreatmentSelect.value = "";
        }
    }

    if (roughMachiningSelect) {
        roughMachiningSelect.style.display = isRoughMachining ? "" : "none";
        if (!isRoughMachining && TrackingState.roughMachiningSubStageFilter) {
            TrackingState.roughMachiningSubStageFilter = "";
            roughMachiningSelect.value = "";
        }
    }
}

/* ===========================
   جدول قطعات (فیلترشده بر اساس مرحله انتخابی و فیلترهای پیشرفته)
   =========================== */

/**
 * لیست قطعاتِ فعلاً نمایش‌داده‌شده در جدول (پس از اعمال فیلتر مرحله و فیلترهای پیشرفته).
 * هم در رندر جدول و هم در منطق «انتخاب همه» استفاده می‌شود تا منطق فیلتر یک‌جا تعریف شده باشد.
 */
function getFilteredPieces() {
    return TrackingState.pieces;
}

function trackingSortValue(piece, key) {
    if (key === "processDate") {
        return piece.processDate || null;
    }
    if (key === "stageEnteredAt") {
        return piece.currentStageEnteredAt || null;
    }
    if (key === "pieceNumber") {
        return piece.pieceNumber ?? null;
    }
    return null;
}

function sortTrackingPieces(pieces) {
    const key = TrackingState.sortBy;
    if (!key) return pieces;

    const dir = TrackingState.sortDir === "asc" ? 1 : -1;
    return [...pieces].sort((a, b) => {
        const va = trackingSortValue(a, key);
        const vb = trackingSortValue(b, key);
        if (va === null && vb === null) return 0;
        if (va === null) return 1;
        if (vb === null) return -1;
        if (va < vb) return -1 * dir;
        if (va > vb) return 1 * dir;
        return 0;
    });
}

function updateTrackingSortHeaders() {
    document.querySelectorAll("#tracking-table thead .sortable-th").forEach((th) => {
        const indicator = th.querySelector(".sort-indicator");
        const active = th.dataset.sort === TrackingState.sortBy;
        th.classList.toggle("is-sorted", active);
        th.setAttribute("aria-sort", active ? (TrackingState.sortDir === "asc" ? "ascending" : "descending") : "none");
        if (indicator) {
            indicator.textContent = active ? (TrackingState.sortDir === "asc" ? "▲" : "▼") : "↕";
        }
    });
}

function setupTrackingSortListeners() {
    document.querySelectorAll("#tracking-table thead .sortable-th").forEach((th) => {
        th.addEventListener("click", () => {
            const key = th.dataset.sort;
            if (TrackingState.sortBy === key) {
                TrackingState.sortDir = TrackingState.sortDir === "desc" ? "asc" : "desc";
            } else {
                TrackingState.sortBy = key;
                TrackingState.sortDir = key === "pieceNumber" ? "asc" : "desc";
            }
            loadTracking({ includeSummary: false, resetPage: true });
        });
    });
    updateTrackingSortHeaders();
}

function renderTensileTrackingInfo(piece) {
    if (piece.currentStage !== "TENSILE" || !piece.tensileTrackingInfo) return "";
    const info = piece.tensileTrackingInfo;
    const labels = { NEXT_TENSILE: "کشش بعدی", RE_OPERATION: "عملیات مجدد", AGING: "پیرسازی" };
    const label = labels[info.nextStage];
    if (!label) return "";
    const durations = Array.isArray(info.agingDurationsMinutes) ? info.agingDurationsMinutes
        : info.nextStage === "AGING" && info.agingDurationMinutes != null ? [info.agingDurationMinutes] : [];
    const agingBoxes = durations.map((raw) => {
        const minutes = raw == null || raw === "" ? null : Number(raw);
        if (minutes === null || !Number.isFinite(minutes) || minutes < 0) return "";
        const number = minutes.toLocaleString("fa-IR", { maximumFractionDigits: 2 });
        return `<span class="tensile-aging-duration" title="مدت پیرسازی ثبت‌شده">${escapeHtml(number)}<sup>دقیقه</sup></span>`;
    }).join("");
    return `<span class="tensile-disposition-info" title="آخرین تصمیم ثبت‌شده">${escapeHtml(label)}</span>${agingBoxes}`;
}

function renderTrackingTable() {
    updateSubStageFilterVisibility();
    updateTrackingSortHeaders();

    const filtered = getFilteredPieces();
    const showBulk = canUseBulkSelection();

    // انتخاب‌های قبلی که دیگر در نتیجه‌ی فیلتر فعلی نیستند حذف می‌شوند تا انتخاب همیشه معتبر بماند
    const visibleIds = new Set(filtered.filter((p) => !p.archivedAt).map((p) => p.id));
    TrackingState.selectedPieceIds = new Set(
        [...TrackingState.selectedPieceIds].filter((id) => visibleIds.has(id))
    );

    const titleEl = document.getElementById("tracking-table-title");
    const countEl = document.getElementById("tracking-table-count");
    const tbody = document.getElementById("tracking-table-body");

    if (TrackingState.activeFilter === "all") {
        titleEl.textContent = TrackingState.includeArchived ? "همه قطعات با احتساب بایگانی‌شده‌ها" : "همه قطعات در جریان تولید";
    } else {
        const stage = STAGE_DEFINITIONS.find((s) => s.key === TrackingState.activeFilter);
        titleEl.textContent = `قطعات در مرحله: ${stage.label}`;
    }

    countEl.textContent = trackingTableCountLabel();

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="${showBulk ? 7 : 6}">هیچ قطعه‌ای با این فیلترها یافت نشد</td></tr>`;
        updateSelectAllCheckboxState(filtered);
        renderBulkActionsBar();
        renderTrackingPagination();
        return;
    }

    tbody.innerHTML = filtered
        .map(
            (p) => `
        <tr data-piece-id="${p.id}" class="${p.archivedAt ? "archived-piece-row" : ""}">
            ${showBulk ? `<td class="checkbox-cell"><input type="checkbox" class="row-select-checkbox" data-id="${p.id}" ${TrackingState.selectedPieceIds.has(p.id) ? "checked" : ""} ${p.archivedAt ? 'disabled aria-label="قطعه بایگانی‌شده"' : ""} /></td>` : ""}
            <td>${p.pieceNumber}</td>
            <td>${escapeHtml(p.pieceName)}</td>
            <td><span class="code-num">${escapeHtml(p.projectCode)}</span></td>
            <td>
                <span class="stage-badge stage-${p.currentStage}">
                    <span class="badge-dot"></span>
                    ${stageLabel(p.currentStage)}
                </span>
                ${
                    p.currentStage === "RADIOGRAPHY" && p.radiographySubStage
                        ? `<span class="substage-badge ${radiographySubStageBadgeClass(p.radiographySubStage)}">${RADIOGRAPHY_SUBSTAGE_LABELS[p.radiographySubStage]}</span>`
                        : ""
                }
                ${
                    p.currentStage === "HEAT_TREATMENT"
                        ? `<span class="substage-badge ${heatTreatmentSubStageBadgeClass(p.heatTreatmentSubStage || "AWAITING_OPERATIONS")}">${HEAT_TREATMENT_SUBSTAGE_LABELS[p.heatTreatmentSubStage || "AWAITING_OPERATIONS"]}</span>`
                        : ""
                }
                ${
                    p.currentStage === "ROUGH_MACHINING"
                        ? `<span class="substage-badge ${roughMachiningSubStageBadgeClass()}">${ROUGH_MACHINING_SUBSTAGE_LABELS[p.roughMachiningSubStage || "DEBURRING"]}</span>`
                        : ""
                }
                ${p.archivedAt ? '<span class="substage-badge">بایگانی‌شده</span>' : ""}
                ${renderTensileTrackingInfo(p)}
            </td>
            <td class="process-date-cell">${renderProcessDateCell(p)}</td>
            <td>${formatDate(p.currentStageEnteredAt)}</td>
        </tr>
    `
        )
        .join("");

    tbody.querySelectorAll("tr[data-piece-id]").forEach((row) => {
        row.addEventListener("click", () => openPieceProfile(row.dataset.pieceId));
    });

    tbody.querySelectorAll(".row-select-checkbox").forEach((checkbox) => {
        // جلوگیری از باز شدن پروفایل قطعه هنگام کلیک روی چک‌باکس
        checkbox.addEventListener("click", (e) => e.stopPropagation());
        checkbox.addEventListener("change", (e) => {
            const id = e.target.dataset.id;
            if (e.target.checked) {
                TrackingState.selectedPieceIds.add(id);
            } else {
                TrackingState.selectedPieceIds.delete(id);
            }
            updateSelectAllCheckboxState(filtered);
            renderBulkActionsBar();
        });
    });

    tbody.querySelectorAll(".process-date-cell").forEach((cell) => {
        cell.addEventListener("click", (e) => e.stopPropagation());
    });
    attachProcessDatePickers(tbody);

    if (showBulk) {
        updateSelectAllCheckboxState(filtered);
        renderBulkActionsBar();
    }
    renderTrackingPagination();
}

function trackingTableCountLabel() {
    const total = TrackingState.total || 0;
    if (total === 0) return "۰ قطعه";
    const from = TrackingState.page * TrackingState.pageSize + 1;
    const to = Math.min(total, (TrackingState.page + 1) * TrackingState.pageSize);
    return `${from.toLocaleString("fa-IR")} تا ${to.toLocaleString("fa-IR")} از ${total.toLocaleString("fa-IR")} قطعه`;
}

function renderTrackingPagination() {
    const bar = document.getElementById("tracking-pagination");
    if (!bar) return;
    const totalPages = TrackingState.totalPages || 0;
    const page = TrackingState.page || 0;
    bar.hidden = (TrackingState.totalPages || 0) <= 1;
    const info = document.getElementById("tracking-page-info");
    if (info) {
        info.textContent = totalPages === 0
            ? ""
            : `صفحه ${(page + 1).toLocaleString("fa-IR")} از ${totalPages.toLocaleString("fa-IR")}`;
    }
    const prev = document.getElementById("btn-tracking-prev");
    const next = document.getElementById("btn-tracking-next");
    if (prev) prev.disabled = page <= 0;
    if (next) next.disabled = totalPages === 0 || page >= totalPages - 1;
    const sizeSelect = document.getElementById("tracking-page-size");
    if (sizeSelect && String(TrackingState.pageSize) !== sizeSelect.value) {
        sizeSelect.value = String(TrackingState.pageSize);
    }
}

function setupTrackingArchiveListener() {
    const checkbox = document.getElementById("tracking-advanced-search");
    if (!checkbox) return;
    checkbox.checked = TrackingState.includeArchived;
    checkbox.addEventListener("change", () => {
        TrackingState.includeArchived = checkbox.checked;
        loadTracking({ resetPage: true });
    });
}

function setupTrackingPaginationListeners() {
    document.getElementById("btn-tracking-prev")?.addEventListener("click", () => {
        if (TrackingState.page <= 0) return;
        TrackingState.page -= 1;
        loadTracking({ includeSummary: false });
    });
    document.getElementById("btn-tracking-next")?.addEventListener("click", () => {
        if (TrackingState.page >= TrackingState.totalPages - 1) return;
        TrackingState.page += 1;
        loadTracking({ includeSummary: false });
    });
    document.getElementById("tracking-page-size")?.addEventListener("change", (e) => {
        TrackingState.pageSize = parseInt(e.target.value, 10) || 50;
        loadTracking({ includeSummary: false, resetPage: true });
    });
}

function updateSelectAllCheckboxState(visiblePieces) {
    visiblePieces = visiblePieces.filter((p) => !p.archivedAt);
    const selectAllCheckbox = document.getElementById("select-all-checkbox");
    if (!selectAllCheckbox) return;

    selectAllCheckbox.disabled = visiblePieces.length === 0;
    const selectedCount = visiblePieces.filter((p) => TrackingState.selectedPieceIds.has(p.id)).length;
    selectAllCheckbox.checked = visiblePieces.length > 0 && selectedCount === visiblePieces.length;
    selectAllCheckbox.indeterminate = selectedCount > 0 && selectedCount < visiblePieces.length;
}

/* ===========================
   نوار عملیات دسته‌جمعی (تغییر وضعیت / زیرمرحله‌ی رادیوگرافی برای چند قطعه)
   =========================== */

function getSelectedPieces() {
    return TrackingState.pieces.filter((p) => !p.archivedAt && TrackingState.selectedPieceIds.has(p.id));
}

function renderBulkActionsBar() {
    const bar = document.getElementById("bulk-actions-bar");
    if (!bar) return;

    if (!canUseBulkSelection()) {
        bar.style.display = "none";
        return;
    }

    const count = TrackingState.selectedPieceIds.size;

    if (count === 0) {
        bar.style.display = "none";
        return;
    }

    bar.style.display = "flex";
    document.getElementById("bulk-actions-count").textContent = `${count} قطعه انتخاب شده`;

    // تغییر دسته‌جمعی زیرمرحله‌ی رادیوگرافی فقط وقتی معنا دارد که همه‌ی قطعات انتخاب‌شده در همان مرحله باشند
    const selectedPieces = getSelectedPieces();
    const allInRadiography =
        selectedPieces.length > 0 && selectedPieces.every((p) => p.currentStage === "RADIOGRAPHY");
    const allInHeatTreatment =
        selectedPieces.length > 0 && selectedPieces.every((p) => p.currentStage === "HEAT_TREATMENT");
    const allInRoughMachining =
        selectedPieces.length > 0 && selectedPieces.every((p) => p.currentStage === "ROUGH_MACHINING");

    document.getElementById("bulk-radiography-substage-group").style.display =
        allInRadiography && hasPermission("ADVANCE_PIECE_STAGE") ? "flex" : "none";
    document.getElementById("bulk-heat-treatment-substage-group").style.display =
        allInHeatTreatment && hasPermission("ADVANCE_PIECE_STAGE") ? "flex" : "none";
    document.getElementById("bulk-rough-machining-substage-group").style.display =
        allInRoughMachining && hasPermission("ADVANCE_PIECE_STAGE") ? "flex" : "none";

    const stageGroup = document.getElementById("bulk-stage-group");
    if (stageGroup) {
        stageGroup.style.display = hasPermission("ADVANCE_PIECE_STAGE") || hasPermission("MOVE_PIECE_STAGE") ? "flex" : "none";
    }
    fillBulkSelects(selectedPieces);
}

function fillSelectOptions(select, values, labels) {
    if (!select) return;
    const previous = select.value;
    select.innerHTML = "";
    values.forEach((key) => {
        const option = document.createElement("option");
        option.value = key;
        option.textContent = typeof labels === "function" ? labels(key) : labels[key] || key;
        select.appendChild(option);
    });
    if (values.includes(previous)) {
        select.value = previous;
    }
}

function fillBulkSelects(selectedPieces) {
    const stageSelect = document.getElementById("bulk-stage-select");
    const radioSelect = document.getElementById("bulk-radiography-substage-select");
    const heatSelect = document.getElementById("bulk-heat-treatment-substage-select");
    const roughSelect = document.getElementById("bulk-rough-machining-substage-select");
    const applyStageBtn = document.getElementById("btn-bulk-apply-stage");
    const applyRadioBtn = document.getElementById("btn-bulk-apply-radiography-substage");
    const applyHeatBtn = document.getElementById("btn-bulk-apply-heat-treatment-substage");
    const applyRoughBtn = document.getElementById("btn-bulk-apply-rough-machining-substage");

    const stageValues = isManager()
        ? TRANSFERABLE_STAGES
        : TRANSFERABLE_STAGES.filter((s) =>
              intersectAllowedValues(selectedPieces, "allowedNextStages").includes(s)
                  && (s === "SCRAPPED" ? hasPermission("MOVE_PIECE_STAGE") : hasPermission("ADVANCE_PIECE_STAGE"))
          );
    fillSelectOptions(stageSelect, stageValues, stageLabel);
    if (applyStageBtn) applyStageBtn.disabled = stageValues.length === 0;

    const radioValues = isManager()
        ? RADIOGRAPHY_SUBSTAGE_OPTIONS
        : RADIOGRAPHY_SUBSTAGE_OPTIONS.filter((s) =>
              intersectAllowedValues(selectedPieces, "allowedNextRadiographySubStages").includes(s)
          );
    fillSelectOptions(radioSelect, radioValues, RADIOGRAPHY_SUBSTAGE_LABELS);
    if (applyRadioBtn) applyRadioBtn.disabled = radioValues.length === 0;

    const heatValues = isManager()
        ? HEAT_TREATMENT_SUBSTAGE_OPTIONS
        : HEAT_TREATMENT_SUBSTAGE_OPTIONS.filter((s) =>
              intersectAllowedValues(selectedPieces, "allowedNextHeatTreatmentSubStages").includes(s)
          );
    fillSelectOptions(heatSelect, heatValues, HEAT_TREATMENT_SUBSTAGE_LABELS);
    if (applyHeatBtn) applyHeatBtn.disabled = heatValues.length === 0;

    const roughValues = isManager()
        ? ROUGH_MACHINING_SUBSTAGE_OPTIONS
        : ROUGH_MACHINING_SUBSTAGE_OPTIONS.filter((s) =>
              intersectAllowedValues(selectedPieces, "allowedNextRoughMachiningSubStages").includes(s)
          );
    fillSelectOptions(roughSelect, roughValues, ROUGH_MACHINING_SUBSTAGE_LABELS);
    if (applyRoughBtn) applyRoughBtn.disabled = roughValues.length === 0;

    const radioGroup = document.getElementById("bulk-radiography-substage-group");
    const heatGroup = document.getElementById("bulk-heat-treatment-substage-group");
    const roughGroup = document.getElementById("bulk-rough-machining-substage-group");
    if (!isManager()) {
        if (radioGroup && radioValues.length === 0) radioGroup.style.display = "none";
        if (heatGroup && heatValues.length === 0) heatGroup.style.display = "none";
        if (roughGroup && roughValues.length === 0) roughGroup.style.display = "none";
        const stageGroup = document.getElementById("bulk-stage-group");
        if (stageGroup && stageValues.length === 0) stageGroup.style.display = "none";
    }
}

function setupBulkActionsListeners() {
    const selectAllCheckbox = document.getElementById("select-all-checkbox");
    const stageSelect = document.getElementById("bulk-stage-select");
    const radiographySubstageSelect = document.getElementById("bulk-radiography-substage-select");
    const heatTreatmentSubstageSelect = document.getElementById("bulk-heat-treatment-substage-select");
    const applyStageBtn = document.getElementById("btn-bulk-apply-stage");
    const applyRadiographySubstageBtn = document.getElementById("btn-bulk-apply-radiography-substage");
    const applyHeatTreatmentSubstageBtn = document.getElementById("btn-bulk-apply-heat-treatment-substage");
    const roughMachiningSubstageSelect = document.getElementById("bulk-rough-machining-substage-select");
    const applyRoughMachiningSubstageBtn = document.getElementById("btn-bulk-apply-rough-machining-substage");
    const clearBtn = document.getElementById("btn-bulk-clear-selection");

    selectAllCheckbox.addEventListener("change", (e) => {
        const filtered = getFilteredPieces().filter((p) => !p.archivedAt);
        if (e.target.checked) {
            filtered.forEach((p) => TrackingState.selectedPieceIds.add(p.id));
        } else {
            filtered.forEach((p) => TrackingState.selectedPieceIds.delete(p.id));
        }
        renderTrackingTable();
    });

    clearBtn.addEventListener("click", () => {
        TrackingState.selectedPieceIds.clear();
        renderTrackingTable();
    });

    applyStageBtn.addEventListener("click", async () => {
        const newStage = stageSelect.value;
        const selectedPieces = getSelectedPieces();
        if (selectedPieces.length === 0) return;
        if (!newStage) {
            showToast("برای قطعات انتخاب‌شده مقصد مجازی وجود ندارد", "error");
            return;
        }

        const toMove = selectedPieces.filter(
            (p) => p.currentStage !== newStage && (p.currentStage !== "WELDING" || newStage === "SCRAPPED")
        );
        const skipped = selectedPieces.length - toMove.length;
        if (toMove.length === 0) {
            const allWelding = selectedPieces.every((p) => p.currentStage === "WELDING");
            showToast(
                allWelding
                    ? "برای خروج از جوشکاری دکمهٔ «اتمام فرآیند» را در پروفایل قطعه بزنید"
                    : "همه قطعات انتخاب‌شده هم‌اکنون در این مرحله هستند",
                "error"
            );
            return;
        }

        const stageInfo = STAGE_DEFINITIONS.find((s) => s.key === newStage);
        if (!confirm(`آیا از تغییر وضعیت ${toMove.length} قطعه به «${stageInfo.label}» مطمئن هستید؟`)) return;

        await withButtonLoader(applyStageBtn, async () => {
            try {
                const scrap = newStage === "SCRAPPED" ? await requestScrapDetails({ count: toMove.length }) : undefined;
                if (scrap === null) return;
                await PieceAPI.bulkUpdateStage(
                    toMove.map((p) => p.id),
                    newStage,
                    scrap
                );
                if (skipped > 0) {
                    showToast(`${toMove.length} قطعه منتقل شد؛ ${skipped} قطعه به‌خاطر تکراری بودن رد شد`);
                } else {
                    showToast(`وضعیت ${toMove.length} قطعه با موفقیت تغییر کرد`);
                }
                TrackingState.selectedPieceIds.clear();
                loadTracking({ resetPage: true });
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });

    applyRadiographySubstageBtn.addEventListener("click", async () => {
        const subStage = radiographySubstageSelect.value;
        const selectedPieces = getSelectedPieces();
        if (selectedPieces.length === 0) return;

        const toMove = selectedPieces.filter((p) => p.radiographySubStage !== subStage);
        const skipped = selectedPieces.length - toMove.length;
        if (toMove.length === 0) {
            showToast("همه قطعات انتخاب‌شده هم‌اکنون در این زیرمرحله هستند", "error");
            return;
        }

        const label = RADIOGRAPHY_SUBSTAGE_LABELS[subStage];
        if (!confirm(`آیا از تغییر زیرمرحله ${toMove.length} قطعه به «${label}» مطمئن هستید؟`)) return;

        await withButtonLoader(applyRadiographySubstageBtn, async () => {
            try {
                await PieceAPI.bulkUpdateRadiographySubStage(
                    toMove.map((p) => p.id),
                    subStage
                );
                if (skipped > 0) {
                    showToast(`${toMove.length} قطعه منتقل شد؛ ${skipped} قطعه به‌خاطر تکراری بودن رد شد`);
                } else {
                    showToast(`زیرمرحله ${toMove.length} قطعه با موفقیت تغییر کرد`);
                }
                TrackingState.selectedPieceIds.clear();
                loadTracking({ resetPage: true });
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });

    applyHeatTreatmentSubstageBtn.addEventListener("click", async () => {
        const subStage = heatTreatmentSubstageSelect.value;
        const selectedPieces = getSelectedPieces();
        if (selectedPieces.length === 0) return;

        const toMove = selectedPieces.filter((p) => p.heatTreatmentSubStage !== subStage);
        const skipped = selectedPieces.length - toMove.length;
        if (toMove.length === 0) {
            showToast("همه قطعات انتخاب‌شده هم‌اکنون در این زیرمرحله هستند", "error");
            return;
        }

        const label = HEAT_TREATMENT_SUBSTAGE_LABELS[subStage];
        if (!confirm(`آیا از تغییر زیرمرحله ${toMove.length} قطعه به «${label}» مطمئن هستید؟`)) return;

        await withButtonLoader(applyHeatTreatmentSubstageBtn, async () => {
            try {
                await PieceAPI.bulkUpdateHeatTreatmentSubStage(
                    toMove.map((p) => p.id),
                    subStage
                );
                if (skipped > 0) {
                    showToast(`${toMove.length} قطعه منتقل شد؛ ${skipped} قطعه به‌خاطر تکراری بودن رد شد`);
                } else {
                    showToast(`زیرمرحله ${toMove.length} قطعه با موفقیت تغییر کرد`);
                }
                TrackingState.selectedPieceIds.clear();
                loadTracking({ resetPage: true });
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });

    applyRoughMachiningSubstageBtn.addEventListener("click", async () => {
        const subStage = roughMachiningSubstageSelect.value;
        const selectedPieces = getSelectedPieces();
        if (selectedPieces.length === 0) return;

        const toMove = selectedPieces.filter((p) => p.roughMachiningSubStage !== subStage);
        const skipped = selectedPieces.length - toMove.length;
        if (toMove.length === 0) {
            showToast("همه قطعات انتخاب‌شده هم‌اکنون در این زیرمرحله هستند", "error");
            return;
        }

        const label = ROUGH_MACHINING_SUBSTAGE_LABELS[subStage];
        if (!confirm(`آیا از تغییر زیرمرحله ${toMove.length} قطعه به «${label}» مطمئن هستید؟`)) return;

        await withButtonLoader(applyRoughMachiningSubstageBtn, async () => {
            try {
                await PieceAPI.bulkUpdateRoughMachiningSubStage(
                    toMove.map((p) => p.id),
                    subStage
                );
                if (skipped > 0) {
                    showToast(`${toMove.length} قطعه منتقل شد؛ ${skipped} قطعه به‌خاطر تکراری بودن رد شد`);
                } else {
                    showToast(`زیرمرحله ${toMove.length} قطعه با موفقیت تغییر کرد`);
                }
                TrackingState.selectedPieceIds.clear();
                loadTracking({ resetPage: true });
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });
}

function canEditProcessDate() {
    return hasPermission("MOVE_PIECE_STAGE") || hasPermission("MANAGE_PIECES");
}

function renderProcessDateCell(piece) {
    if (piece.archivedAt || !canEditProcessDate()) {
        return piece.processDate ? isoToJalaliString(piece.processDate) : "—";
    }
    return `<input type="text" class="process-date-input" data-piece-id="${piece.id}" placeholder="انتخاب تاریخ" />`;
}

function attachProcessDatePickers(root) {
    if (typeof attachPersianDatePicker !== "function") return;
    root.querySelectorAll(".process-date-input").forEach((input) => {
        const pieceId = input.dataset.pieceId;
        const piece = TrackingState.pieces.find((p) => p.id === pieceId);
        const picker = attachPersianDatePicker(input, {
            onSelect: async (d) => {
                const iso = jalaliToIsoDateString(d.jy, d.jm, d.jd);
                try {
                    const updated = await PieceAPI.updateProcessDate(pieceId, iso);
                    if (piece) piece.processDate = updated.processDate;
                    showToast("تاریخ انجام فرآیند ذخیره شد");
                    if (TrackingState.sortBy === "processDate") loadTracking({ includeSummary: false });
                } catch (error) {
                    showToast(error.message, "error");
                }
            },
            onClear: async () => {
                try {
                    const updated = await PieceAPI.updateProcessDate(pieceId, null);
                    if (piece) piece.processDate = updated.processDate;
                    showToast("تاریخ انجام فرآیند پاک شد");
                    if (TrackingState.sortBy === "processDate") loadTracking({ includeSummary: false });
                } catch (error) {
                    showToast(error.message, "error");
                }
            },
        });
        if (piece && piece.processDate) picker.setIsoDate(piece.processDate);
    });
}

/* ===========================
   پروفایل قطعه (مودال)
   =========================== */

async function openPieceProfile(pieceId) {
    openModal("در حال بارگذاری", `<div class="loader-wrap">${loaderMarkup()}</div>`, { size: "lg" });
    try {
        const [piece, history, subStageHistory, processDates, defects, notes, tensileTests, heatTreatments, weldingDispatches, scrapDispatches, stageSkips] = await Promise.all([
            PieceAPI.getById(pieceId),
            PieceAPI.getStageHistory(pieceId),
            PieceAPI.getSubStageHistory(pieceId),
            // تاریخ‌های مرحله (دارای stage) و تاریخ‌های زیرمرحله (دارای kind و subStage) در یک آرایه
            Promise.all([
                PieceAPI.getStageProcessDates(pieceId).catch(() => []),
                PieceAPI.getSubStageProcessDates(pieceId).catch(() => []),
            ]).then(([stageDates, subDates]) => [...(stageDates || []), ...(subDates || [])]),
            RadiographyDefectAPI.getByPiece(pieceId),
            PieceNoteAPI.getByPiece(pieceId),
            TensileTestAPI.getByPiece(pieceId),
            HeatTreatmentAPI.getByPiece(pieceId),
            PieceAPI.getWeldingDispatches(pieceId),
            PieceAPI.getScrapDispatches(pieceId),
            PieceAPI.getStageSkips(pieceId),
        ]);

        resetRadiographyState();
        resetNotesState();
        resetTensileState();
        renderPieceProfileModal(piece, history, defects, notes, subStageHistory, tensileTests, heatTreatments, weldingDispatches, processDates, scrapDispatches, stageSkips);
    } catch (error) {
        openModal(
            "پروفایل قطعه",
            `<p class="form-hint" role="alert">${escapeHtml(error.message)}</p>
            <button type="button" class="btn btn-primary" id="btn-profile-retry">تلاش مجدد</button>`
        );
        document.getElementById("btn-profile-retry")?.addEventListener("click", () => openPieceProfile(pieceId));
    }
}

function renderPieceProfileModal(
    piece,
    history,
    defects,
    notes,
    subStageHistory = [],
    tensileTests = [],
    heatTreatments = [],
    weldingDispatches = [],
    processDates = [],
    scrapDispatches = [],
    stageSkips = []
) {
    openModal(
        `قطعه شماره ${piece.pieceNumber} — ${piece.pieceName}`,
        `
        ${
            piece.archivedAt
                ? `<p class="form-hint">این قطعه بایگانی شده است و در فهرست‌های اصلی نمایش داده نمی‌شود.</p>`
                : ""
        }
        <div class="profile-info-grid">
            <div class="profile-info-item">
                <span class="profile-info-label">کد پروژه</span>
                <span class="profile-info-value">${escapeHtml(piece.projectCode)}</span>
            </div>
            <div class="profile-info-item">
                <span class="profile-info-label">نام قطعه</span>
                <span class="profile-info-value">${escapeHtml(piece.pieceName)}</span>
            </div>
            <div class="profile-info-item">
                <span class="profile-info-label">شماره قطعه</span>
                <span class="profile-info-value">${piece.pieceNumber}</span>
            </div>
            <div class="profile-info-item">
                <span class="profile-info-label">ورود به مرحله</span>
                <span class="profile-info-value">${formatDate(piece.currentStageEnteredAt)}</span>
            </div>
            <div class="profile-info-item">
                <span class="profile-info-label">تاریخ ثبت قطعه</span>
                <span class="profile-info-value">${formatDate(piece.createdAt)}</span>
            </div>
            <div class="profile-info-item">
                <span class="profile-info-label">تاریخ انجام فرآیند</span>
                <span class="profile-info-value">${piece.processDate ? isoToJalaliString(piece.processDate) : "—"}</span>
            </div>
        </div>

        <div class="profile-current-stage">
            <span class="profile-info-label">وضعیت فعلی</span>
            <div class="profile-current-stage-right">
                <span class="stage-badge stage-${piece.currentStage}">
                    <span class="badge-dot"></span>
                    ${stageLabel(piece.currentStage)}
                </span>
                ${renderCurrentSubStageBadge(piece)}
                <span id="profile-radiography-icon-container">${renderRadiographyIcon(defects)}</span>
                <span id="profile-welding-map-icon-container">${renderWeldingMapIcon(piece)}</span>
                <span id="profile-tensile-icon-container">${renderTensileIcon(tensileTests)}</span>
                <span id="profile-heat-log-icon-container">${renderHeatTreatmentLogIcon(heatTreatments)}</span>
                <span id="profile-melt-icon-container">${renderMeltPassportIcon()}</span>
            </div>
        </div>

        ${renderProfileStageActions(piece)}

        <div id="profile-radiography-workflow"></div>

        <div id="profile-defects-container" style="display: none;">
            ${renderDefectsSection(piece.id, defects)}
        </div>

        ${piece.archivedAt ? "" : renderWeldingStationSection(piece, weldingDispatches)}
        ${renderScrapSection(piece, scrapDispatches)}

        <div id="profile-notes-container">
            ${renderNotesSection(piece.id, notes)}
        </div>

        ${typeof renderWeldingDispatchHistorySection === "function" ? renderWeldingDispatchHistorySection(weldingDispatches) : ""}

        ${typeof renderProductionFlow === "function" ? renderProductionFlow(piece, history, subStageHistory, processDates, weldingDispatches) : ""}
        ${renderStageSkipHistory(stageSkips)}
    `,
        { size: "xl" }
    );

    attachDefectsSectionEvents(piece);
    attachWeldingMapIconEvents(piece);
    attachTensileIconEvents(piece);
    attachHeatTreatmentLogIconEvents(piece);
    attachNotesSectionEvents(piece.id);
    attachWeldingStationEvents(piece, defects, weldingDispatches);
    attachScrapEvents(piece, defects, scrapDispatches);
    attachProfileStageActions(piece);
    attachProductionFlowEditors(piece);
    if (typeof loadPieceRadiographyWorkflow === "function") loadPieceRadiographyWorkflow(piece);
    if (typeof loadPieceMeltPassports === "function") loadPieceMeltPassports(piece);
}

function attachProductionFlowEditors(piece) {
    if (!piece || piece.archivedAt || !canEditProcessDate()) return;
    document.querySelectorAll(".production-flow .flow-step.is-editable").forEach((stepEl) => {
        const open = () => openStageProcessDateEditor(piece, stepEl);
        stepEl.addEventListener("click", open);
        stepEl.addEventListener("keydown", (event) => {
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                open();
            }
        });
    });
}

function openStageProcessDateEditor(piece, stepEl) {
    const stage = stepEl.dataset.stage;
    const label = stepEl.dataset.label || stage;
    const group = stepEl.dataset.group || "";
    const existing = stepEl.dataset.processDate || "";
    const subKind = stepEl.dataset.kind || "";
    const subStage = stepEl.dataset.sub || "";
    openNestedModal(
        `تاریخ انجام — ${label}`,
        `
        <p class="form-hint">${group ? `این تاریخ فقط برای زیرمرحلهٔ «${escapeHtml(label)}» از مرحلهٔ «${escapeHtml(group)}» ثبت می‌شود و روی زیرمرحله‌های دیگر اثری ندارد.` : "تاریخ انجام فرآیند این مرحله را وارد کنید. برای پاک کردن، تاریخ را خالی بگذارید."}</p>
        <div class="form-group">
            <label for="stage-process-date">تاریخ انجام فرآیند</label>
            <input type="text" id="stage-process-date" placeholder="انتخاب تاریخ">
        </div>
        <div class="form-error" id="stage-process-date-error"></div>
        <div class="form-actions">
            <button type="button" class="btn btn-secondary" id="btn-cancel-stage-date">انصراف</button>
            <button type="button" class="btn btn-secondary" id="btn-clear-stage-date">پاک کردن تاریخ</button>
            <button type="button" class="btn btn-primary" id="btn-save-stage-date">ثبت</button>
        </div>`
    );

    let selectedIso = existing || null;
    const input = document.getElementById("stage-process-date");
    const picker = typeof attachPersianDatePicker === "function"
        ? attachPersianDatePicker(input, {
            onSelect: (d) => {
                selectedIso = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                selectedIso = null;
            },
        })
        : null;
    if (existing && picker) picker.setIsoDate(existing);

    const save = async (processDate) => {
        const button = document.getElementById(processDate ? "btn-save-stage-date" : "btn-clear-stage-date");
        await withButtonLoader(button, async () => {
            try {
                if (subKind && subStage) {
                    await PieceAPI.updateSubStageProcessDate(piece.id, subKind, subStage, processDate);
                } else {
                    await PieceAPI.updateStageProcessDate(piece.id, stage, processDate);
                }
                closeNestedModal();
                showToast(processDate ? "تاریخ انجام فرآیند ثبت شد" : "تاریخ انجام فرآیند پاک شد");
                await openPieceProfile(piece.id);
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    };

    document.getElementById("btn-cancel-stage-date")?.addEventListener("click", closeNestedModal);
    document.getElementById("btn-clear-stage-date")?.addEventListener("click", () => save(null));
    document.getElementById("btn-save-stage-date")?.addEventListener("click", () => {
        if (!selectedIso) {
            const err = document.getElementById("stage-process-date-error");
            if (err) {
                err.textContent = "تاریخ را انتخاب کنید، یا «پاک کردن تاریخ» را بزنید";
                err.style.display = "block";
            }
            return;
        }
        save(selectedIso);
    });
}

function renderCurrentSubStageBadge(piece) {
    if (piece.currentStage === "RADIOGRAPHY" && piece.radiographySubStage) {
        return `<span class="substage-badge ${radiographySubStageBadgeClass(piece.radiographySubStage)}">${RADIOGRAPHY_SUBSTAGE_LABELS[piece.radiographySubStage] || piece.radiographySubStage}</span>`;
    }
    if (piece.currentStage === "HEAT_TREATMENT" && piece.heatTreatmentSubStage) {
        return `<span class="substage-badge ${heatTreatmentSubStageBadgeClass()}">${HEAT_TREATMENT_SUBSTAGE_LABELS[piece.heatTreatmentSubStage] || piece.heatTreatmentSubStage}</span>`;
    }
    if (piece.currentStage === "ROUGH_MACHINING") {
        const sub = piece.roughMachiningSubStage || "DEBURRING";
        return `<span class="substage-badge ${roughMachiningSubStageBadgeClass()}">${ROUGH_MACHINING_SUBSTAGE_LABELS[sub] || sub}</span>`;
    }
    return "";
}

function forwardMoveLabel(move) {
    if (!move) return "";
    if (move.kind === "ROUGH_MACHINING_SUBSTAGE") {
        return `${stageLabel("ROUGH_MACHINING")} — ${ROUGH_MACHINING_SUBSTAGE_LABELS[move.roughMachiningSubStage] || move.roughMachiningSubStage}`;
    }
    if (move.kind === "RADIOGRAPHY_SUBSTAGE") {
        return `${stageLabel("RADIOGRAPHY")} — ${RADIOGRAPHY_SUBSTAGE_LABELS[move.radiographySubStage] || move.radiographySubStage}`;
    }
    if (move.kind === "HEAT_TREATMENT_SUBSTAGE") {
        return `${stageLabel("HEAT_TREATMENT")} — ${HEAT_TREATMENT_SUBSTAGE_LABELS[move.heatTreatmentSubStage] || move.heatTreatmentSubStage}`;
    }
    const parts = [stageLabel(move.stage)];
    if (move.roughMachiningSubStage) {
        parts.push(ROUGH_MACHINING_SUBSTAGE_LABELS[move.roughMachiningSubStage] || move.roughMachiningSubStage);
    }
    if (move.radiographySubStage) {
        parts.push(RADIOGRAPHY_SUBSTAGE_LABELS[move.radiographySubStage] || move.radiographySubStage);
    }
    if (move.heatTreatmentSubStage) {
        parts.push(HEAT_TREATMENT_SUBSTAGE_LABELS[move.heatTreatmentSubStage] || move.heatTreatmentSubStage);
    }
    return parts.filter(Boolean).join(" — ");
}

function canAdvancePiece(piece) {
    const blocked = new Set(["CASTING", "WELDING", "TENSILE", "DELIVERED", "SCRAPPED"]);
    return !!(
        piece &&
        !piece.archivedAt &&
        piece.forwardMove &&
        !blocked.has(piece.currentStage) &&
        hasPermission(piece.forwardMove.requiredPermission)
    );
}

function renderProfileStageActions(piece) {
    if (piece.archivedAt) return "";
    if (piece.currentStage === "CASTING") {
        return '<p class="form-hint">برای انتقال از ذوب‌ریزی، شناسنامه ذوب را تکمیل و برای تأیید ارسال کنید. پس از تأیید، قطعات مرتبط خودکار منتقل می‌شوند.</p>';
    }
    const bits = [];
    if (typeof canSendToWelding === "function" && canSendToWelding(piece)) {
        bits.push(renderWeldingSendIcon());
    }
    if (hasPermission("MOVE_PIECE_STAGE") && piece.currentStage !== "SCRAPPED") {
        bits.push(
            `<button type="button" class="profile-action-icon profile-action-scrap stage-card-icon stage-SCRAPPED" id="btn-profile-scrap" title="اسقاط" aria-label="اسقاط">${STAGE_ICON_SCRAPPED}</button>`
        );
    }
    if (canAdvancePiece(piece)) {
        bits.push(
            `<button type="button" class="btn btn-primary btn-sm" id="btn-profile-next">مرحله بعد: ${escapeHtml(forwardMoveLabel(piece.forwardMove))}</button>`
        );
    }
    if (canUseStageSkip() && !["WELDING", "SCRAPPED", "DELIVERED", "CASTING"].includes(piece.currentStage)) {
        bits.push('<button type="button" class="btn btn-secondary btn-sm" id="btn-profile-skip">پرش مرحله</button>');
    }
    if (isManager() && piece.currentStage !== "WELDING") {
        bits.push(
            `<button type="button" class="profile-action-icon" id="btn-admin-jump" title="اصلاح وضعیت" aria-label="اصلاح وضعیت">↕</button>`
        );
    }
    if (!bits.length) return "";
    return `<div class="profile-stage-actions">${bits.join("")}</div>`;
}

function attachProfileStageActions(piece) {
    const scrapBtn = document.getElementById("btn-profile-scrap");
    if (scrapBtn) {
        scrapBtn.addEventListener("click", async () => {
            if (!confirm("آیا از اسقاط این قطعه مطمئن هستید؟")) return;
            await withButtonLoader(scrapBtn, async () => {
                try {
                    const scrap = await requestScrapDetails({ pieceId: piece.id });
                    if (!scrap) return;
                    await PieceAPI.updateStage(piece.id, "SCRAPPED", scrap);
                    showToast("قطعه اسقاط شد");
                    loadTracking();
                    await openPieceProfile(piece.id);
                } catch (error) {
                    showToast(error.message, "error");
                }
            });
        });
    }

    const nextBtn = document.getElementById("btn-profile-next");
    if (nextBtn) {
        nextBtn.addEventListener("click", () => advancePieceFromProfile(piece, nextBtn));
    }

    document.getElementById("btn-profile-skip")?.addEventListener("click", () => openStageSkipModal(piece));
    const jumpBtn = document.getElementById("btn-admin-jump");
    if (jumpBtn) {
        jumpBtn.addEventListener("click", () => openAdminJumpModal(piece));
    }
}

async function advancePieceFromProfile(piece, btn) {
    const runAdvance = async (processDate) => {
        await withButtonLoader(btn, async () => {
            try {
                await PieceAPI.advance(piece.id, processDate);
                showToast("قطعه به مرحله بعد منتقل شد");
                loadTracking();
                await openPieceProfile(piece.id);
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    };

    if (!piece.forwardMove.processDateRequired || piece.processDate) {
        await runAdvance();
        return;
    }

    openNestedModal(
        "تاریخ انجام فرایند",
        `
        <p class="form-hint">تاریخ انجام فرایند این موقعیت خالی است. ابتدا تاریخ را وارد کنید، سپس قطعه به مرحله بعد می‌رود.</p>
        <div class="form-group">
            <label for="advance-process-date">تاریخ انجام فرایند</label>
            <input type="text" id="advance-process-date" placeholder="انتخاب تاریخ">
        </div>
        <div class="form-error" id="advance-process-date-error"></div>
        <div class="form-actions">
            <button type="button" class="btn btn-secondary" id="btn-cancel-advance-date">انصراف</button>
            <button type="button" class="btn btn-primary" id="btn-confirm-advance-date">مرحله بعد</button>
        </div>`
    );

    let selectedIso = null;
    const input = document.getElementById("advance-process-date");
    if (typeof attachPersianDatePicker === "function") {
        attachPersianDatePicker(input, {
            onSelect: (d) => {
                selectedIso = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                selectedIso = null;
            },
        });
    }
    document.getElementById("btn-cancel-advance-date")?.addEventListener("click", closeNestedModal);
    document.getElementById("btn-confirm-advance-date")?.addEventListener("click", async () => {
        if (!selectedIso) {
            const err = document.getElementById("advance-process-date-error");
            if (err) {
                err.textContent = "تاریخ انجام فرایند را انتخاب کنید";
                err.style.display = "block";
            } else {
                showToast("تاریخ انجام فرایند را انتخاب کنید", "error");
            }
            return;
        }
        closeNestedModal();
        await runAdvance(selectedIso);
    });
}

function openAdminJumpModal(piece) {
    const stageOptions = TRANSFERABLE_STAGES.map(
        (s) => `<option value="${s}" ${s === piece.currentStage ? "selected" : ""}>${stageLabel(s)}</option>`
    ).join("");
    const radioOptions = RADIOGRAPHY_SUBSTAGE_OPTIONS.map(
        (s) =>
            `<option value="${s}" ${s === piece.radiographySubStage ? "selected" : ""}>${RADIOGRAPHY_SUBSTAGE_LABELS[s]}</option>`
    ).join("");
    const heatOptions = HEAT_TREATMENT_SUBSTAGE_OPTIONS.map(
        (s) =>
            `<option value="${s}" ${s === (piece.heatTreatmentSubStage || "AWAITING_OPERATIONS") ? "selected" : ""}>${HEAT_TREATMENT_SUBSTAGE_LABELS[s]}</option>`
    ).join("");
    const roughOptions = ROUGH_MACHINING_SUBSTAGE_OPTIONS.map(
        (s) =>
            `<option value="${s}" ${s === (piece.roughMachiningSubStage || "DEBURRING") ? "selected" : ""}>${ROUGH_MACHINING_SUBSTAGE_LABELS[s]}</option>`
    ).join("");

    openNestedModal(
        "اصلاح وضعیت",
        `
        <p class="form-hint">برای پرش رو به جلو از دکمه «پرش مرحله» و ثبت دلیل استفاده کنید. این بخش برای اصلاح وضعیت است.</p>
        <div class="form-group">
            <label for="admin-jump-stage">مرحله</label>
            <select id="admin-jump-stage">${stageOptions}</select>
        </div>
        <div class="form-group admin-jump-sub" id="admin-jump-radio-wrap">
            <label for="admin-jump-radio">زیرمرحله رادیوگرافی</label>
            <select id="admin-jump-radio">${radioOptions}</select>
        </div>
        <div class="form-group admin-jump-sub" id="admin-jump-heat-wrap">
            <label for="admin-jump-heat">زیرمرحله عملیات حرارتی</label>
            <select id="admin-jump-heat">${heatOptions}</select>
        </div>
        <div class="form-group admin-jump-sub" id="admin-jump-rough-wrap">
            <label for="admin-jump-rough">زیرمرحله خشن‌تراشی</label>
            <select id="admin-jump-rough">${roughOptions}</select>
        </div>
        <div class="form-error" id="admin-jump-error"></div>
        <div class="form-actions">
            <button type="button" class="btn btn-secondary" id="btn-cancel-admin-jump">انصراف</button>
            <button type="button" class="btn btn-primary" id="btn-confirm-admin-jump">اعمال</button>
        </div>`
    );

    const stageSelect = document.getElementById("admin-jump-stage");
    const syncSubstageVisibility = () => {
        const stage = stageSelect.value;
        document.getElementById("admin-jump-radio-wrap").style.display = stage === "RADIOGRAPHY" ? "" : "none";
        document.getElementById("admin-jump-heat-wrap").style.display = stage === "HEAT_TREATMENT" ? "" : "none";
        document.getElementById("admin-jump-rough-wrap").style.display = stage === "ROUGH_MACHINING" ? "" : "none";
    };
    syncSubstageVisibility();
    stageSelect.addEventListener("change", syncSubstageVisibility);

    document.getElementById("btn-cancel-admin-jump")?.addEventListener("click", closeNestedModal);
    document.getElementById("btn-confirm-admin-jump")?.addEventListener("click", async (e) => {
        const btn = e.currentTarget;
        const errorBox = document.getElementById("admin-jump-error");
        const targetStage = stageSelect.value;
        const radio = document.getElementById("admin-jump-radio").value;
        const heat = document.getElementById("admin-jump-heat").value;
        const rough = document.getElementById("admin-jump-rough").value;
        await withButtonLoader(btn, async () => {
            try {
                if (targetStage !== piece.currentStage) {
                    const scrap = targetStage === "SCRAPPED" ? await requestScrapDetails({ pieceId: piece.id }) : undefined;
                    if (scrap === null) return;
                    await PieceAPI.updateStage(piece.id, targetStage, scrap, {
                        ...(targetStage === "RADIOGRAPHY" ? { radiographySubStage: radio } : {}),
                        ...(targetStage === "HEAT_TREATMENT" ? { heatTreatmentSubStage: heat } : {}),
                        ...(targetStage === "ROUGH_MACHINING" ? { roughMachiningSubStage: rough } : {}),
                    });
                } else if (targetStage === "RADIOGRAPHY") {
                    if (radio === piece.radiographySubStage) {
                        throw new Error("این قطعه هم‌اکنون در همین وضعیت است");
                    }
                    await PieceAPI.updateRadiographySubStage(piece.id, radio);
                } else if (targetStage === "HEAT_TREATMENT") {
                    if (heat === (piece.heatTreatmentSubStage || "AWAITING_OPERATIONS")) {
                        throw new Error("این قطعه هم‌اکنون در همین وضعیت است");
                    }
                    await PieceAPI.updateHeatTreatmentSubStage(piece.id, heat);
                } else if (targetStage === "ROUGH_MACHINING") {
                    if (rough === (piece.roughMachiningSubStage || "DEBURRING")) {
                        throw new Error("این قطعه هم‌اکنون در همین وضعیت است");
                    }
                    await PieceAPI.updateRoughMachiningSubStage(piece.id, rough);
                } else {
                    throw new Error("این قطعه هم‌اکنون در همین مرحله است و انتقال تکراری مجاز نیست");
                }
                showToast("وضعیت قطعه با موفقیت تغییر کرد");
                closeNestedModal();
                loadTracking();
                await openPieceProfile(piece.id);
            } catch (error) {
                if (errorBox?.isConnected) {
                    errorBox.textContent = error.message;
                    errorBox.style.display = "block";
                } else {
                    showToast(error.message, "error");
                }
            }
        });
    });
}
/* ===========================
   مدیریت جستجو (Debounce)
   =========================== */

function setupSearchListeners() {
    const searchInput = document.getElementById("tracking-search-input");
    const clearBtn = document.getElementById("btn-clear-search");
    const pieceNameInput = document.getElementById("tracking-filter-piece-name");
    const projectCodeInput = document.getElementById("tracking-filter-project-code");

    const pieceNumberInput = document.getElementById("tracking-filter-piece-number");

    /* جستجوی شماره‌ی قطعه همان جستجوی سراسری (q) است؛ دو input یک مقدار را نگه می‌دارند */
    const applyGlobalSearch = (value) => {
        const query = value.trim();
        TrackingState.searchQuery = query;
        if (searchInput.value.trim() !== query) searchInput.value = query;
        if (pieceNumberInput && pieceNumberInput.value.trim() !== query) pieceNumberInput.value = query;
        loadTracking({ resetPage: true });
    };

    const applyColumnFilters = () => {
        TrackingState.pieceNameQuery = (pieceNameInput?.value || "").trim();
        TrackingState.projectCodeQuery = (projectCodeInput?.value || "").trim();
        loadTracking({ resetPage: true });
    };

    searchInput.addEventListener("input", (e) => {
        clearTimeout(TrackingState.searchDebounceTimer);
        TrackingState.searchDebounceTimer = setTimeout(() => applyGlobalSearch(e.target.value), 300);
    });

    searchInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            e.preventDefault();
            clearTimeout(TrackingState.searchDebounceTimer);
            applyGlobalSearch(e.target.value);
        }
    });

    if (pieceNumberInput) {
        pieceNumberInput.addEventListener("click", (e) => e.stopPropagation());
        pieceNumberInput.addEventListener("mousedown", (e) => e.stopPropagation());
        pieceNumberInput.addEventListener("input", (e) => {
            clearTimeout(TrackingState.searchDebounceTimer);
            TrackingState.searchDebounceTimer = setTimeout(() => applyGlobalSearch(e.target.value), 300);
        });
        pieceNumberInput.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                clearTimeout(TrackingState.searchDebounceTimer);
                applyGlobalSearch(e.target.value);
            }
        });
    }

    [pieceNameInput, projectCodeInput].forEach((input) => {
        if (!input) return;
        input.addEventListener("click", (e) => e.stopPropagation());
        input.addEventListener("mousedown", (e) => e.stopPropagation());
        input.addEventListener("input", () => {
            clearTimeout(TrackingState.searchDebounceTimer);
            TrackingState.searchDebounceTimer = setTimeout(applyColumnFilters, 300);
        });
        input.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                clearTimeout(TrackingState.searchDebounceTimer);
                applyColumnFilters();
            }
        });
    });

    clearBtn.addEventListener("click", () => {
        searchInput.value = "";
        if (pieceNumberInput) pieceNumberInput.value = "";
        TrackingState.searchQuery = "";
        if (pieceNameInput) pieceNameInput.value = "";
        if (projectCodeInput) projectCodeInput.value = "";
        TrackingState.pieceNameQuery = "";
        TrackingState.projectCodeQuery = "";
        TrackingState.activeFilter = "all";
        loadTracking({ resetPage: true });
        searchInput.focus();
    });
}

/* ===========================
   مدیریت فیلترهای پیشرفته (تاریخ ورود به مرحله)
   =========================== */

function setupAdvancedFilterListeners() {
    const dateFromInput = document.getElementById("filter-date-from");
    const dateToInput = document.getElementById("filter-date-to");
    const subStageSelect = document.getElementById("filter-radiography-substage");
    const heatSubStageSelect = document.getElementById("filter-heat-treatment-substage");
    const roughSubStageSelect = document.getElementById("filter-rough-machining-substage");
    const clearBtn = document.getElementById("btn-clear-advanced-filters");

    // گزینه‌های زیرمرحله را از radiography.js می‌سازیم تا لیست فقط یک‌جا تعریف شده باشد
    RADIOGRAPHY_SUBSTAGE_OPTIONS.forEach((key) => {
        const option = document.createElement("option");
        option.value = key;
        option.textContent = RADIOGRAPHY_SUBSTAGE_LABELS[key];
        subStageSelect.appendChild(option);
    });

    HEAT_TREATMENT_SUBSTAGE_OPTIONS.forEach((key) => {
        const option = document.createElement("option");
        option.value = key;
        option.textContent = HEAT_TREATMENT_SUBSTAGE_LABELS[key];
        heatSubStageSelect.appendChild(option);
    });

    ROUGH_MACHINING_SUBSTAGE_OPTIONS.forEach((key) => {
        const option = document.createElement("option");
        option.value = key;
        option.textContent = ROUGH_MACHINING_SUBSTAGE_LABELS[key];
        roughSubStageSelect.appendChild(option);
    });

    subStageSelect.addEventListener("change", (e) => {
        TrackingState.radiographySubStageFilter = e.target.value;
        loadTracking({ includeSummary: false, resetPage: true });
    });

    roughSubStageSelect.addEventListener("change", (e) => {
        TrackingState.roughMachiningSubStageFilter = e.target.value;
        loadTracking({ includeSummary: false, resetPage: true });
    });

    heatSubStageSelect.addEventListener("change", (e) => {
        TrackingState.heatTreatmentSubStageFilter = e.target.value;
        loadTracking({ includeSummary: false, resetPage: true });
    });

    // تقویم شمسی برای انتخاب بازه‌ی تاریخ؛ مقدار داخلی هنوز به شکل رشته‌ی میلادی yyyy-mm-dd
    const dateFromPicker = attachPersianDatePicker(dateFromInput, {
        onSelect: (d) => {
            TrackingState.dateFrom = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            loadTracking({ resetPage: true });
        },
        onClear: () => {
            TrackingState.dateFrom = "";
            loadTracking({ resetPage: true });
        },
    });

    const dateToPicker = attachPersianDatePicker(dateToInput, {
        onSelect: (d) => {
            TrackingState.dateTo = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            loadTracking({ resetPage: true });
        },
        onClear: () => {
            TrackingState.dateTo = "";
            loadTracking({ resetPage: true });
        },
    });

    clearBtn.addEventListener("click", () => {
        dateFromPicker.clear();
        dateToPicker.clear();
        subStageSelect.value = "";
        heatSubStageSelect.value = "";
        roughSubStageSelect.value = "";
        TrackingState.dateFrom = "";
        TrackingState.dateTo = "";
        TrackingState.radiographySubStageFilter = "";
        TrackingState.heatTreatmentSubStageFilter = "";
        TrackingState.roughMachiningSubStageFilter = "";
        loadTracking({ resetPage: true });
    });
}

// راه‌اندازی event listener‌های جستجو و فیلترهای پیشرفته هنگام بارگذاری صفحه
document.addEventListener("DOMContentLoaded", () => {
    setupSearchListeners();
    setupAdvancedFilterListeners();
    setupBulkActionsListeners();
    setupTrackingSortListeners();
    setupTrackingPaginationListeners();
    setupTrackingArchiveListener();
});


function canUseStageSkip() {
    return !!AuthState.user && ["MANAGER", "ADMIN"].includes(AuthState.user.role) && hasPermission("SKIP_PIECE_STAGE");
}

function stageSkipLocation(target) {
    const radio = target.radiographySubStage;
    const heat = target.heatTreatmentSubStage;
    const rough = target.roughMachiningSubStage;
    const sub = radio ? RADIOGRAPHY_SUBSTAGE_LABELS[radio] : heat ? HEAT_TREATMENT_SUBSTAGE_LABELS[heat] : rough ? ROUGH_MACHINING_SUBSTAGE_LABELS[rough] : "";
    return [stageLabel(target.stage), sub].filter(Boolean).join(" — ");
}

async function openStageSkipModal(piece) {
    if (!canUseStageSkip()) return;
    try {
        const targets = await PieceAPI.getSkipTargets(piece.id);
        if (!targets.length) { showToast("مقصدی برای پرش رو به جلو وجود ندارد", "error"); return; }
        openNestedModal("پرش مرحله", `
            <p class="form-hint">این انتقال با نام شما و دلیل ثبت می‌شود.</p>
            <div class="form-group"><label for="stage-skip-target">مقصد</label>
                <select id="stage-skip-target">${targets.map((target, i) => `<option value="${i}">${escapeHtml(stageSkipLocation(target))}</option>`).join("")}</select></div>
            <div class="form-group"><label for="stage-skip-reason">دلیل پرش</label>
                <textarea id="stage-skip-reason" maxlength="1000" required></textarea></div>
            <div id="stage-skip-error" class="form-error"></div>
            <div class="form-actions"><button type="button" class="btn btn-secondary" id="stage-skip-cancel">انصراف</button>
                <button type="button" class="btn btn-primary" id="stage-skip-submit">ثبت پرش</button></div>`);
        document.getElementById("stage-skip-cancel").addEventListener("click", closeNestedModal);
        document.getElementById("stage-skip-submit").addEventListener("click", async e => {
            const reason = document.getElementById("stage-skip-reason").value.trim();
            const box = document.getElementById("stage-skip-error");
            if (!reason) { box.textContent = "دلیل پرش را وارد کنید"; box.style.display = "block"; return; }
            await withButtonLoader(e.currentTarget, async () => {
                try {
                    const target = targets[Number(document.getElementById("stage-skip-target").value)];
                    await PieceAPI.skipStage(piece.id, { ...target, reason });
                    closeNestedModal(); showToast("پرش مرحله ثبت شد"); loadTracking(); await openPieceProfile(piece.id);
                } catch (error) { box.textContent = error.message; box.style.display = "block"; }
            });
        });
    } catch (error) { showToast(error.message, "error"); }
}

function renderStageSkipHistory(events = []) {
    if (!events.length) return "";
    const location = (stage, sub) => stageSkipLocation({stage,
        radiographySubStage: stage === "RADIOGRAPHY" ? sub : null,
        heatTreatmentSubStage: stage === "HEAT_TREATMENT" ? sub : null,
        roughMachiningSubStage: stage === "ROUGH_MACHINING" ? sub : null});
    return `<section class="task-card"><h3>سوابق پرش مرحله</h3>${events.map(event => `
        <article><p>${escapeHtml(location(event.fromStage,event.fromSubStage))} ← ${escapeHtml(location(event.toStage,event.toSubStage))}</p>
            <p>${escapeHtml(event.reason)}</p>
            <p class="form-hint">${escapeHtml(event.actorName)} — ${escapeHtml(formatDate(event.occurredAt))}</p></article>`).join("")}</section>`;
}

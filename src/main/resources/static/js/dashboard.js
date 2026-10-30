/**
 * dashboard.js
 * داشبورد عملیاتی: KPIها، گلوگاه، هشدارها، فیلتر پروژه/تاریخ، به‌روزرسانی خودکار هر ۲ دقیقه.
 */

const DASHBOARD_REFRESH_MS = 2 * 60 * 1000;

const DashboardState = {
    projectId: "",
    projectLabel: "",
    dateFrom: "",
    dateTo: "",
    stuckThresholdDays: 7,
    subStageThresholdDays: 3,
    refreshTimer: null,
    projectsLoaded: false,
    dateFromPicker: null,
    dateToPicker: null,
    alertsDateFromPicker: null,
    alertsDateToPicker: null,
    activeTab: "overview",
    isLoading: false,
    lastUpdatedAt: null,
    projectsCache: [],
};

const DefectReportState = {
    projectId: "",
    pieceDefinitionIds: [],
    defectTypes: [],
    statuses: [],
    weldingConfirmed: [],
    dateFrom: "",
    dateTo: "",
    dateFromPicker: null,
    dateToPicker: null,
    rows: [],
    projectCode: "",
};

const OccupancyReportState = {
    projectId: "",
    dateFrom: "",
    dateTo: "",
    dateFromPicker: null,
    dateToPicker: null,
    rows: [],
    projectCode: "",
};

const WeldingReportState = {
    projectId: "",
    rows: [],
    projectCode: "",
};

const DASHBOARD_REPORT_TABS = new Set(["defect-report", "occupancy-report", "welding-report", "tensile-quality-report", "tensile-trend-report"]);

function formatFaNumber(value) {
    if (value == null || value === "" || Number.isNaN(Number(value))) return "—";
    return Number(value).toLocaleString("fa-IR");
}

function formatPercent(value) {
    if (value == null || Number.isNaN(value)) return "—";
    return `${Number(value).toLocaleString("fa-IR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}٪`;
}

function setDashboardLoading(loading) {
    DashboardState.isLoading = loading;
    document.querySelectorAll("#dashboard-kpi-primary .stat-card, #dashboard-kpi-secondary .stat-card").forEach((el) => {
        el.classList.toggle("is-loading", loading);
    });
    const section = document.getElementById("dashboard-section");
    setElementLoading(section, loading);
}

function showDashboardError(message) {
    const banner = document.getElementById("dashboard-error-banner");
    const text = document.getElementById("dashboard-error-text");
    if (!banner || !text) return;
    if (message) {
        text.textContent = message;
        banner.classList.remove("hidden");
    } else {
        banner.classList.add("hidden");
        text.textContent = "";
    }
}

function updateDashboardLastUpdated() {
    const el = document.getElementById("dashboard-last-updated");
    if (!el) return;
    if (!DashboardState.lastUpdatedAt) {
        el.textContent = "";
        return;
    }
    const time = DashboardState.lastUpdatedAt.toLocaleTimeString("fa-IR", {
        hour: "2-digit",
        minute: "2-digit",
    });
    el.textContent = `آخرین به‌روزرسانی: ${time}`;
}

function renderDashboardFilterSummary() {
    const container = document.getElementById("dashboard-filter-summary");
    if (!container) return;

    const chips = [];
    if (DashboardState.projectLabel) {
        chips.push(`<span class="dashboard-filter-chip">پروژه: ${escapeHtml(DashboardState.projectLabel)}</span>`);
    } else {
        chips.push(`<span class="dashboard-filter-chip">همه پروژه‌ها</span>`);
    }

    const fromInput = document.getElementById("dashboard-date-from");
    const toInput = document.getElementById("dashboard-date-to");
    const fromLabel = fromInput?.value?.trim();
    const toLabel = toInput?.value?.trim();

    if (fromLabel || toLabel) {
        const range = [fromLabel || "…", toLabel || "…"].join(" — ");
        chips.push(`<span class="dashboard-filter-chip">بازه: ${escapeHtml(range)}</span>`);
    } else {
        chips.push(`<span class="dashboard-filter-chip">بدون محدودیت تاریخ</span>`);
    }

    container.innerHTML = chips.join("");
}

function switchDashboardTab(tabId) {
    DashboardState.activeTab = tabId;
    const reportParent = DASHBOARD_REPORT_TABS.has(tabId);

    document.querySelectorAll(".dashboard-tab").forEach((btn) => {
        const active = btn.dataset.tab === tabId || (reportParent && btn.dataset.tab === "reports");
        btn.classList.toggle("active", active);
        btn.setAttribute("aria-selected", active ? "true" : "false");
    });

    document.querySelectorAll(".dashboard-report-link[data-tab]").forEach((btn) => {
        btn.classList.toggle("active", btn.dataset.tab === tabId);
    });

    document.querySelectorAll(".dashboard-tab-panel").forEach((panel) => {
        const active = panel.id === `dashboard-tab-${tabId}`;
        panel.classList.toggle("active", active);
        panel.hidden = !active;
    });

    if (!reportParent) setReportsMenuOpen(false);

    if (tabId === "tensile-quality-report" && typeof openTensileQualityReport === "function") {
        openTensileQualityReport();
    }
    if (tabId === "tensile-trend-report" && typeof openTensileTrendReport === "function") {
        openTensileTrendReport();
    }
}

function setReportsMenuOpen(open) {
    const menu = document.getElementById("dashboard-reports-menu");
    const btn = document.getElementById("dashboard-tab-btn-reports");
    if (menu) menu.classList.toggle("hidden", !open);
    if (btn) btn.setAttribute("aria-expanded", open ? "true" : "false");
}

function toggleReportsMenu() {
    const btn = document.getElementById("dashboard-tab-btn-reports");
    const open = btn?.getAttribute("aria-expanded") === "true";
    setReportsMenuOpen(!open);
}

function initDashboardReportsMenu() {
    const reportsBtn = document.getElementById("dashboard-tab-btn-reports");
    reportsBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        toggleReportsMenu();
    });

    document.querySelectorAll(".dashboard-report-link[data-tab]").forEach((btn) => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            switchDashboardTab(btn.dataset.tab);
            setReportsMenuOpen(false);
        });
    });

    document.getElementById("btn-dashboard-furnaces")?.addEventListener("click", () => {
        setReportsMenuOpen(false);
    });

    document.addEventListener("click", (e) => {
        const wrap = document.getElementById("dashboard-nav");
        if (wrap && !wrap.contains(e.target)) setReportsMenuOpen(false);
    });
}

async function loadDashboard() {
    // ویجت «وظایف من» مستقل از گزارش‌های داشبورد بارگذاری می‌شود تا خطای یکی دیگری را خراب نکند
    if (typeof loadMyTasksWidget === "function") loadMyTasksWidget();
    setDashboardLoading(true);
    showDashboardError("");
    if (typeof syncDashboardFurnaceShortcut === "function") {
        syncDashboardFurnaceShortcut();
    }

    try {
        await ensureDashboardProjects();
        const overview = await DashboardAPI.getOverview({
            projectId: DashboardState.projectId || undefined,
            dateFrom: DashboardState.dateFrom || undefined,
            dateTo: DashboardState.dateTo || undefined,
            stuckThresholdDays: DashboardState.stuckThresholdDays,
            subStageThresholdDays: DashboardState.subStageThresholdDays,
        });

        document.getElementById("stat-total-projects").textContent = formatFaNumber(overview.totalProjects);
        document.getElementById("stat-total-pieces").textContent = formatFaNumber(overview.totalPieces);
        document.getElementById("stat-wip").textContent = formatFaNumber(overview.wipCount);
        const wipPercentEl = document.getElementById("stat-wip-percent");
        if (wipPercentEl) {
            wipPercentEl.textContent =
                overview.wipPercent != null ? `${formatPercent(overview.wipPercent)} از کل` : "";
        }
        document.getElementById("stat-cycle-time").textContent =
            overview.avgCycleTimeDays != null ? formatFaNumber(overview.avgCycleTimeDays) : "—";
        document.getElementById("stat-scrap-rate").textContent =
            overview.scrapRatePercent != null ? formatPercent(overview.scrapRatePercent) : "—";
        document.getElementById("stat-scrapped-count").textContent = formatFaNumber(overview.scrappedInPeriod);
        document.getElementById("stat-delivered-period").textContent = formatFaNumber(overview.deliveredInPeriod);
        document.getElementById("stat-ready-period").textContent = formatFaNumber(overview.readyInPeriod);
        document.getElementById("stat-shot-blasting-period").textContent = formatFaNumber(overview.shotBlastingInPeriod);
        document.getElementById("stat-heat-stage1-period").textContent = formatFaNumber(overview.heatTreatmentStage1InPeriod);

        renderBottlenecks(overview.bottleneckStage, overview.bottleneckProject);
        renderStageDistribution(overview.stageCounts || {});
        // سرور هر لیست هشدار را سقف‌دار برمی‌گرداند؛ تعداد واقعی در فیلدهای *Total است
        renderNeedsWeldingAlerts(overview.needsWelding || [], overview.needsWeldingTotal);
        renderStuckPieceAlerts(overview.stuckPieces || [], overview.stuckThresholdDays, overview.stuckPiecesTotal);
        renderSubStageOverdueAlerts(
            "alert-interp-count",
            "alert-interp-list",
            overview.awaitingInterpretationOverdue || [],
            overview.subStageThresholdDays,
            "تفسیر",
            overview.awaitingInterpretationOverdueTotal
        );
        renderSubStageOverdueAlerts(
            "alert-ops-count",
            "alert-ops-list",
            overview.awaitingOperationsOverdue || [],
            overview.subStageThresholdDays,
            "منتظر عملیات",
            overview.awaitingOperationsOverdueTotal
        );
        renderRecentMoves(overview.recentStageMoves || []);
        await renderPendingTensileAlerts(DashboardState.projectId);
        syncDashboardFilterInputs(overview);
        renderDashboardFilterSummary();

        DashboardState.lastUpdatedAt = new Date();
        updateDashboardLastUpdated();
        startDashboardAutoRefresh();
    } catch (error) {
        showDashboardError(error.message || "خطا در بارگذاری داشبورد");
        showToast(error.message, "error");
    } finally {
        setDashboardLoading(false);
    }
}

async function ensureDashboardProjects() {
    if (DashboardState.projectsLoaded) return;
    const select = document.getElementById("dashboard-project-filter");
    const reportSelect = document.getElementById("defect-report-project");
    if (!select && !reportSelect) return;
    try {
        const projects = await ProjectAPI.getAll();
        DashboardState.projectsCache = projects;
        const optionsHtml = projects
            .map(
                (p) =>
                    `<option value="${p.id}">${escapeHtml(p.code)}${
                        p.name ? " — " + escapeHtml(p.name) : ""
                    }</option>`
            )
            .join("");

        if (select) {
            select.innerHTML = `<option value="">همه پروژه‌ها</option>` + optionsHtml;
            select.value = DashboardState.projectId || "";
            updateProjectLabelFromSelect(select);
        }
        const alertsSelect = document.getElementById("dashboard-alerts-project-filter");
        if (alertsSelect) {
            alertsSelect.innerHTML = `<option value="">همه پروژه‌ها</option>` + optionsHtml;
            alertsSelect.value = DashboardState.projectId || "";
        }
        if (reportSelect) {
            reportSelect.innerHTML = `<option value="">انتخاب پروژه</option>` + optionsHtml;
            reportSelect.value = DefectReportState.projectId || "";
        }
        const occupancySelect = document.getElementById("occupancy-report-project");
        if (occupancySelect) {
            occupancySelect.innerHTML = `<option value="">همه پروژه‌ها</option>` + optionsHtml;
            occupancySelect.value = OccupancyReportState.projectId || "";
        }
        const weldingSelect = document.getElementById("welding-report-project");
        if (weldingSelect) {
            weldingSelect.innerHTML = `<option value="">همه پروژه‌ها</option>` + optionsHtml;
            weldingSelect.value = WeldingReportState.projectId || "";
            const opt = weldingSelect.options[weldingSelect.selectedIndex];
            WeldingReportState.projectCode = opt && opt.value ? opt.textContent : "";
        }
        const tensileQualitySelect = document.getElementById("tensile-quality-project");
        if (tensileQualitySelect) {
            const previous = tensileQualitySelect.value;
            tensileQualitySelect.innerHTML = `<option value="">همه پروژه‌ها</option>` + optionsHtml;
            tensileQualitySelect.value = previous || "";
        }
        const tensileTrendSelect = document.getElementById("tensile-trend-project");
        if (tensileTrendSelect) {
            const previous = tensileTrendSelect.value;
            tensileTrendSelect.innerHTML = `<option value="">همه پروژه‌ها</option>` + optionsHtml;
            tensileTrendSelect.value = previous || "";
        }
        DashboardState.projectsLoaded = true;
    } catch (error) {
        showToast(error.message, "error");
    }
}

function syncDashboardProjectSelects() {
    ["dashboard-project-filter", "dashboard-alerts-project-filter"].forEach((id) => {
        const el = document.getElementById(id);
        if (el) el.value = DashboardState.projectId || "";
    });
}

function updateProjectLabelFromSelect(select) {
    if (!select) return;
    const opt = select.options[select.selectedIndex];
    DashboardState.projectLabel = opt && opt.value ? opt.textContent : "";
}

function renderMultiFilterOptions(containerId, options) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = options.length
        ? options
              .map(
                  (opt) => `
        <label class="multi-filter-option">
            <input type="checkbox" value="${escapeHtml(opt.value)}" />
            <span>${escapeHtml(opt.label)}</span>
        </label>`
              )
              .join("")
        : `<div class="multi-filter-empty">گزینه‌ای موجود نیست</div>`;

    container.querySelectorAll('input[type="checkbox"]').forEach((cb) => {
        cb.addEventListener("change", () => updateMultiFilterBadge(containerId));
    });
    updateMultiFilterBadge(containerId);
}

function updateMultiFilterBadge(containerId) {
    const badge = document.querySelector(`.multi-filter-badge[data-for="${containerId}"]`);
    if (!badge) return;

    const checked = getMultiFilterValues(containerId);
    const total = document.querySelectorAll(`#${containerId} input[type="checkbox"]`).length;

    if (!total) {
        badge.textContent = "—";
        badge.classList.add("is-empty");
        return;
    }
    if (checked.length === 0) {
        badge.textContent = "بدون محدودیت";
        badge.classList.add("is-empty");
        return;
    }

    badge.classList.remove("is-empty");
    if (checked.length === 1) {
        const label = document.querySelector(`#${containerId} input:checked`)?.nextElementSibling?.textContent;
        badge.textContent = label || "۱ مورد";
        return;
    }
    badge.textContent = `${formatFaNumber(checked.length)} مورد`;
}

function getMultiFilterValues(containerId) {
    return Array.from(document.querySelectorAll(`#${containerId} input[type="checkbox"]:checked`)).map(
        (cb) => cb.value
    );
}

function clearMultiFilter(containerId) {
    document.querySelectorAll(`#${containerId} input[type="checkbox"]`).forEach((cb) => {
        cb.checked = false;
    });
    updateMultiFilterBadge(containerId);
}

function clearDashboardDateFilters() {
    DashboardState.dateFrom = "";
    DashboardState.dateTo = "";
    DashboardState.dateFromPicker?.clear();
    DashboardState.dateToPicker?.clear();
    DashboardState.alertsDateFromPicker?.clear();
    DashboardState.alertsDateToPicker?.clear();
}

function syncDashboardFilterInputs(overview) {
    const stuck = document.getElementById("alert-stuck-threshold");
    const sub = document.getElementById("alert-substage-threshold");
    if (stuck) stuck.value = overview.stuckThresholdDays;
    if (sub) sub.value = overview.subStageThresholdDays;
    DashboardState.stuckThresholdDays = overview.stuckThresholdDays;
    DashboardState.subStageThresholdDays = overview.subStageThresholdDays;
}

function renderBottlenecks(stageItem, projectItem) {
    const stageEl = document.getElementById("bottleneck-stage");
    const projectEl = document.getElementById("bottleneck-project");
    const projectCard = document.getElementById("bottleneck-project-card");

    if (stageEl) {
        stageEl.textContent = stageItem
            ? `${stageLabel(stageItem.stage)} — ${formatFaNumber(stageItem.pieceCount)} قطعه`
            : "—";
    }

    if (projectEl && projectCard) {
        if (projectItem && projectItem.projectCode) {
            projectEl.textContent = `${projectItem.projectCode} — ${formatFaNumber(projectItem.pieceCount)} قطعه`;
            projectCard.classList.remove("hidden");
        } else {
            projectEl.textContent = "—";
            projectCard.classList.toggle("hidden", !!DashboardState.projectId);
        }
    }
}

function renderStageDistribution(stageCounts) {
    const container = document.getElementById("stage-distribution");
    if (!container) return;

    const entries = ALL_STAGES.map((stage) => ({
        stage,
        count: stageCounts[stage] || 0,
    }));
    const maxCount = Math.max(...entries.map((e) => e.count), 1);

    if (entries.every((e) => e.count === 0)) {
        container.innerHTML = `<div class="alert-widget-empty">داده‌ای برای نمایش توزیع مراحل وجود ندارد</div>`;
        return;
    }

    container.innerHTML = entries
        .map(({ stage, count }) => {
            const width = maxCount > 0 ? Math.max((count / maxCount) * 100, count > 0 ? 2 : 0) : 0;
            return `
                <div class="stage-bar-row${count === 0 ? " is-zero" : ""}">
                    <span class="stage-bar-label">${stageLabel(stage)}</span>
                    <div class="stage-bar-track" aria-hidden="true">
                        <div class="stage-bar-fill stage-${stage}" style="width: ${width}%"></div>
                    </div>
                    <span class="stage-bar-count">${formatFaNumber(count)}</span>
                </div>`;
        })
        .join("");
}

function renderAlertItemButton(item, options = {}) {
    const { extraHtml = "", className = "", trailingHtml = "" } = options;
    const label = `${item.projectCode} — ${item.pieceName} — قطعه ${formatFaNumber(item.pieceNumber)}`;
    return `
        <button type="button" class="alert-item-btn ${className}" data-piece-id="${item.pieceId}"
            aria-label="مشاهده پروفایل ${escapeHtml(label)}">
            <div class="alert-item-main">
                <span class="code-num">${escapeHtml(item.projectCode)}</span>
                <span class="alert-item-name">${escapeHtml(item.pieceName)} — قطعه ${formatFaNumber(item.pieceNumber)}</span>
                ${extraHtml}
            </div>
            ${trailingHtml}
        </button>`;
}

/**
 * کلیک روی آیتم‌های لیست با «event delegation»: به‌جای یک listener برای هر آیتم،
 * فقط یک listener روی خود container (یک‌بار) ثبت می‌شود و handler فعلی هر بار که لیست
 * دوباره رندر می‌شود عوض می‌شود. روی لیست‌های بلند و سیستم‌های ضعیف خیلی سبک‌تر است.
 */
function bindDelegatedItemClick(container, itemSelector, handler) {
    container._delegatedItemHandler = handler;
    if (container.dataset.delegatedClick === "1") return;
    container.dataset.delegatedClick = "1";
    container.addEventListener("click", (event) => {
        const el = event.target.closest(itemSelector);
        if (!el || !container.contains(el)) return;
        if (typeof container._delegatedItemHandler === "function") {
            container._delegatedItemHandler(el);
        }
    });
}

/** وقتی سرور فقط بخشی از لیست را فرستاده، یک خط «نمایش X از Y» انتهای لیست می‌آید */
function renderTruncatedNote(shown, total) {
    if (!total || total <= shown) return "";
    return `<div class="alert-widget-empty alert-widget-more">نمایش ${formatFaNumber(shown)} مورد از ${formatFaNumber(total)}</div>`;
}

function renderNeedsWeldingAlerts(items, total) {
    document.getElementById("alert-welding-count").textContent = formatFaNumber(total ?? items.length);
    const container = document.getElementById("alert-welding-list");

    if (items.length === 0) {
        container.innerHTML = `<div class="alert-widget-empty">هیچ قطعه‌ای در ایستگاه جوشکاری نیست</div>`;
        return;
    }

    container.innerHTML = items
        .map((item) => {
            const typeLabel =
                typeof weldingDefectTypeLabel === "function"
                    ? weldingDefectTypeLabel(item.defectType)
                    : item.defectType || "—";
            const extra = `<div class="alert-item-defects">
                <span class="alert-defect-line">عیب: ${escapeHtml(typeLabel)}</span>
                ${item.notes ? `<span class="alert-defect-line">${escapeHtml(item.notes)}</span>` : ""}
            </div>`;
            return renderAlertItemButton(item, {
                extra,
                className: "alert-item-welding",
            });
        })
        .join("") + renderTruncatedNote(items.length, total);

    attachAlertItemClickHandlers(container);
}

function renderStuckPieceAlerts(items, thresholdDays, total) {
    document.getElementById("alert-stuck-count").textContent = formatFaNumber(total ?? items.length);
    const container = document.getElementById("alert-stuck-list");

    if (items.length === 0) {
        container.innerHTML = `<div class="alert-widget-empty">هیچ قطعه‌ای بیش از ${formatFaNumber(thresholdDays)} روز در یک مرحله گیر نکرده</div>`;
        return;
    }

    container.innerHTML = items
        .map((item) =>
            renderAlertItemButton(item, {
                trailingHtml: `
                <span class="stage-badge stage-${item.currentStage}">
                    <span class="badge-dot"></span>
                    ${stageLabel(item.currentStage)}
                </span>
                <span class="alert-item-badge alert-badge-warning">${formatFaNumber(item.daysInStage)} روز</span>`,
            })
        )
        .join("") + renderTruncatedNote(items.length, total);

    attachAlertItemClickHandlers(container);
}

function renderSubStageOverdueAlerts(countId, listId, items, thresholdDays, label, total) {
    document.getElementById(countId).textContent = formatFaNumber(total ?? items.length);
    const container = document.getElementById(listId);

    if (items.length === 0) {
        container.innerHTML = `<div class="alert-widget-empty">هیچ قطعه‌ای بیش از ${formatFaNumber(thresholdDays)} روز در «${label}» نمانده</div>`;
        return;
    }

    container.innerHTML = items
        .map((item) =>
            renderAlertItemButton(item, {
                trailingHtml: `<span class="alert-item-badge alert-badge-warning">${formatFaNumber(item.daysInSubStage)} روز</span>`,
            })
        )
        .join("") + renderTruncatedNote(items.length, total);

    attachAlertItemClickHandlers(container);
}

function renderRecentMoves(items) {
    const container = document.getElementById("recent-moves-list");
    if (!container) return;

    if (!items.length) {
        container.innerHTML = `<div class="alert-widget-empty">تغییر وضعیتی در بازهٔ انتخابی ثبت نشده</div>`;
        return;
    }

    container.innerHTML = items
        .map(
            (item) => `
        <button type="button" class="recent-move-item-btn" data-piece-id="${item.pieceId}"
            aria-label="مشاهده پروفایل ${escapeHtml(item.projectCode)} — ${escapeHtml(item.pieceName)}">
            <div class="alert-item-main">
                <span class="code-num">${escapeHtml(item.projectCode)}</span>
                <span class="alert-item-name">${escapeHtml(item.pieceName)} — قطعه ${formatFaNumber(item.pieceNumber)}</span>
            </div>
            <span class="stage-badge stage-${item.stage}">
                <span class="badge-dot"></span>
                ${stageLabel(item.stage)}
            </span>
            <span class="history-meta">
                ${item.changedBy ? `<span class="history-user">${escapeHtml(item.changedBy)}</span>` : ""}
                <span class="history-time">${formatDateTime(item.changedAt)}</span>
            </span>
        </button>`
        )
        .join("");

    bindDelegatedItemClick(container, ".recent-move-item-btn", (el) => openPieceProfile(el.dataset.pieceId));
}

/**
 * ویجت «نیازمند تعیین‌تکلیف کشش». برخلاف بقیه‌ی هشدارها، داده‌اش از overview نمی‌آید
 * و مستقیماً از سرویس تست کشش گرفته می‌شود.
 */
async function renderPendingTensileAlerts(projectId) {
    const countEl = document.getElementById("alert-tensile-count");
    const container = document.getElementById("alert-tensile-list");
    if (!countEl || !container) return;

    let items;
    try {
        items = await TensileTestAPI.getPendingReview(projectId || undefined);
    } catch (error) {
        container.innerHTML = `<div class="alert-widget-empty">${escapeHtml(error.message)}</div>`;
        return;
    }

    countEl.textContent = formatFaNumber(items.length);

    if (items.length === 0) {
        container.innerHTML = `<div class="alert-widget-empty">هیچ تست کششی در انتظار تعیین‌تکلیف نیست</div>`;
        return;
    }

    container.innerHTML = items
        .map((item) => {
            const badges = [];
            if (item.belowThreshold) {
                badges.push(`<span class="alert-item-badge alert-badge-danger">زیر حد مجاز</span>`);
            }
            badges.push(
                `<span class="alert-item-badge alert-badge-warning">${formatFaNumber(item.waitingDays)} روز</span>`
            );

            return renderAlertItemButton(item, {
                className: item.belowThreshold ? "alert-item-welding" : "",
                extraHtml: `<span class="alert-defect-line">تست ${formatFaNumber(item.testIndex)}</span>`,
                trailingHtml: badges.join(""),
            });
        })
        .join("");

    // برخلاف بقیه‌ی هشدارها، اینجا مستقیم پاپ‌آپ کشش باز می‌شود نه پروفایل قطعه
    bindDelegatedItemClick(container, ".alert-item-btn", (el) => openTensileModalByPieceId(el.dataset.pieceId));
}

function attachAlertItemClickHandlers(container) {
    bindDelegatedItemClick(container, ".alert-item-btn", (el) => openPieceProfile(el.dataset.pieceId));
}

function readDashboardFiltersFromDom(source = "overview") {
    const selectId = source === "alerts" ? "dashboard-alerts-project-filter" : "dashboard-project-filter";
    const projectSelect = document.getElementById(selectId);
    DashboardState.projectId = projectSelect?.value || "";
    updateProjectLabelFromSelect(projectSelect);
    syncDashboardProjectSelects();
}

function readAlertThresholdsFromDom() {
    const stuck = parseInt(document.getElementById("alert-stuck-threshold")?.value, 10);
    const sub = parseInt(document.getElementById("alert-substage-threshold")?.value, 10);
    DashboardState.stuckThresholdDays = stuck > 0 ? stuck : 1;
    DashboardState.subStageThresholdDays = sub > 0 ? sub : 1;
}

function startDashboardAutoRefresh() {
    if (DashboardState.refreshTimer) {
        clearInterval(DashboardState.refreshTimer);
    }
    DashboardState.refreshTimer = setInterval(() => {
        const section = document.getElementById("dashboard-section");
        if (section && section.classList.contains("active")) {
            loadDashboard();
        }
    }, DASHBOARD_REFRESH_MS);
}

document.addEventListener("DOMContentLoaded", () => {
    const debounce = (fn, ms) => {
        let t = null;
        return (...args) => {
            clearTimeout(t);
            t = setTimeout(() => fn(...args), ms);
        };
    };

    const reloadFromOverviewFilters = debounce(() => {
        readDashboardFiltersFromDom("overview");
        loadDashboard();
    }, 400);

    const reloadFromAlertsFilters = debounce(() => {
        readDashboardFiltersFromDom("alerts");
        loadDashboard();
    }, 400);

    document.querySelectorAll(".dashboard-tab").forEach((btn) => {
        if (btn.dataset.tab === "reports") return;
        btn.addEventListener("click", () => switchDashboardTab(btn.dataset.tab));
    });
    initDashboardReportsMenu();

    const projectFilter = document.getElementById("dashboard-project-filter");
    if (projectFilter) projectFilter.addEventListener("change", reloadFromOverviewFilters);

    const alertsProjectFilter = document.getElementById("dashboard-alerts-project-filter");
    if (alertsProjectFilter) alertsProjectFilter.addEventListener("change", reloadFromAlertsFilters);

    const attachDashboardDatePicker = (inputEl, stateKey, pickerKey, reloadFn) => {
        if (!inputEl || typeof attachPersianDatePicker !== "function") return;
        DashboardState[pickerKey] = attachPersianDatePicker(inputEl, {
            onSelect: (d) => {
                DashboardState[stateKey] = jalaliToIsoDateString(d.jy, d.jm, d.jd);
                reloadFn();
            },
            onClear: () => {
                DashboardState[stateKey] = "";
                reloadFn();
            },
        });
    };

    attachDashboardDatePicker(
        document.getElementById("dashboard-date-from"),
        "dateFrom",
        "dateFromPicker",
        reloadFromOverviewFilters
    );
    attachDashboardDatePicker(
        document.getElementById("dashboard-date-to"),
        "dateTo",
        "dateToPicker",
        reloadFromOverviewFilters
    );
    attachDashboardDatePicker(
        document.getElementById("dashboard-alerts-date-from"),
        "dateFrom",
        "alertsDateFromPicker",
        reloadFromAlertsFilters
    );
    attachDashboardDatePicker(
        document.getElementById("dashboard-alerts-date-to"),
        "dateTo",
        "alertsDateToPicker",
        reloadFromAlertsFilters
    );

    const clearBtn = document.getElementById("btn-dashboard-clear-filters");
    if (clearBtn) {
        clearBtn.addEventListener("click", () => {
            DashboardState.projectId = "";
            syncDashboardProjectSelects();
            clearDashboardDateFilters();
            readDashboardFiltersFromDom("overview");
            loadDashboard();
        });
    }

    document.getElementById("btn-alerts-clear-filters")?.addEventListener("click", () => {
        DashboardState.projectId = "";
        syncDashboardProjectSelects();
        clearDashboardDateFilters();
        readDashboardFiltersFromDom("alerts");
        loadDashboard();
    });

    const refreshBtn = document.getElementById("btn-dashboard-refresh");
    if (refreshBtn) {
        refreshBtn.addEventListener("click", () => {
            readDashboardFiltersFromDom(DashboardState.activeTab === "alerts" ? "alerts" : "overview");
            loadDashboard();
        });
    }

    document.getElementById("btn-dashboard-retry")?.addEventListener("click", () => {
        readDashboardFiltersFromDom(DashboardState.activeTab === "alerts" ? "alerts" : "overview");
        loadDashboard();
    });

    document.getElementById("btn-alert-thresholds-apply")?.addEventListener("click", () => {
        readAlertThresholdsFromDom();
        loadDashboard();
    });

    const aggBtn = document.getElementById("btn-aggregate-welding-map");
    if (aggBtn) {
        aggBtn.addEventListener("click", () => openAggregateWeldingMapPicker());
    }

    initDefectReportUi();
    initOccupancyReportUi();
    initWeldingReportUi();
    if (typeof initTensileQualityReportUi === "function") initTensileQualityReportUi();
    if (typeof initTensileTrendReportUi === "function") initTensileTrendReportUi();
    switchDashboardTab("overview");
});

/* ===========================
   گزارش عیوب رادیوگرافی
   =========================== */

function initDefectReportUi() {
    const reportTypes = DEFECT_REPORT_TYPE_OPTIONS;

    renderMultiFilterOptions(
        "defect-report-defect-types",
        reportTypes.map((t) => ({ value: t, label: typeof defectTypeOptionLabel === "function" ? defectTypeOptionLabel(t) : t }))
    );
    renderMultiFilterOptions("defect-report-statuses", [
        { value: "NEEDS_WELDING", label: "نیاز به جوش" },
        { value: "APPROVED", label: "بدون نیاز به جوش" },
    ]);
    renderMultiFilterOptions("defect-report-welding-confirmed", [
        { value: "true", label: "تاییدشده" },
        { value: "false", label: "تاییدنشده" },
    ]);

    const multiFilters = Array.from(document.querySelectorAll("#defect-report-filters .multi-filter-details"));
    const closeMultiFilters = (except) => {
        multiFilters.forEach((d) => {
            if (d !== except) d.open = false;
        });
    };

    multiFilters.forEach((details) => {
        details.addEventListener("toggle", () => {
            if (details.open) closeMultiFilters(details);
        });
    });

    if (multiFilters.length) {
        document.addEventListener("click", (e) => {
            const inside =
                e.target instanceof Element && e.target.closest("#defect-report-filters .multi-filter-details");
            if (!inside) closeMultiFilters(null);
        });
        document.addEventListener("keydown", (e) => {
            if (e.key !== "Escape") return;
            const open = multiFilters.find((d) => d.open);
            if (!open) return;
            closeMultiFilters(null);
            open.querySelector(".multi-filter-summary")?.focus();
        });
    }

    const dateFromInput = document.getElementById("defect-report-date-from");
    const dateToInput = document.getElementById("defect-report-date-to");
    if (dateFromInput && typeof attachPersianDatePicker === "function") {
        DefectReportState.dateFromPicker = attachPersianDatePicker(dateFromInput, {
            onSelect: (d) => {
                DefectReportState.dateFrom = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                DefectReportState.dateFrom = "";
            },
        });
    }
    if (dateToInput && typeof attachPersianDatePicker === "function") {
        DefectReportState.dateToPicker = attachPersianDatePicker(dateToInput, {
            onSelect: (d) => {
                DefectReportState.dateTo = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                DefectReportState.dateTo = "";
            },
        });
    }

    const projectSelect = document.getElementById("defect-report-project");
    if (projectSelect) {
        projectSelect.addEventListener("change", async () => {
            DefectReportState.projectId = projectSelect.value;
            await loadDefectReportDefinitions(DefectReportState.projectId);
            clearDefectReportTable("برای مشاهده گزارش، «اجرای گزارش» را بزنید");
        });
    }

    document.getElementById("btn-defect-report-run")?.addEventListener("click", () => runDefectReport());
    document.getElementById("btn-defect-report-clear")?.addEventListener("click", () => clearDefectReportFilters());
    document.getElementById("btn-defect-report-export-summary")?.addEventListener("click", () => exportDefectReportSummaryCsv());
    document.getElementById("btn-defect-report-export-detail")?.addEventListener("click", () => exportDefectReportDetailCsv());
}

async function loadDefectReportDefinitions(projectId) {
    const container = document.getElementById("defect-report-definitions");
    if (!container) return;

    container.innerHTML = "";
    DefectReportState.pieceDefinitionIds = [];
    updateMultiFilterBadge("defect-report-definitions");

    if (!projectId) return;

    try {
        const definitions = await PieceDefinitionAPI.getByProject(projectId);
        const options = definitions
            .slice()
            .sort((a, b) => (a.rowNum || 0) - (b.rowNum || 0))
            .map((d) => ({
                value: d.id,
                label: `ردیف ${d.rowNum} — ${d.pieceName}`,
            }));
        renderMultiFilterOptions("defect-report-definitions", options);
    } catch (error) {
        showToast(error.message, "error");
    }
}

function readDefectReportFilters() {
    DefectReportState.projectId = document.getElementById("defect-report-project")?.value || "";
    DefectReportState.pieceDefinitionIds = getMultiFilterValues("defect-report-definitions");
    DefectReportState.defectTypes = getMultiFilterValues("defect-report-defect-types");
    DefectReportState.statuses = getMultiFilterValues("defect-report-statuses");
    DefectReportState.weldingConfirmed = getMultiFilterValues("defect-report-welding-confirmed").map(
        (v) => v === "true"
    );
}

async function runDefectReport() {
    readDefectReportFilters();
    if (!DefectReportState.projectId) {
        showToast("لطفاً یک پروژه انتخاب کنید", "error");
        clearDefectReportTable("برای مشاهده گزارش، یک پروژه انتخاب کنید و «اجرای گزارش» را بزنید");
        return;
    }

    const tbody = document.getElementById("defect-report-table-body");
    showTableLoader(tbody, 9);
    const runBtn = document.getElementById("btn-defect-report-run");
    await withButtonLoader(runBtn, async () => {
        try {
            const report = await DashboardAPI.getRadiographyDefectReport({
                projectId: DefectReportState.projectId,
                pieceDefinitionIds: DefectReportState.pieceDefinitionIds,
                defectTypes: DefectReportState.defectTypes,
                statuses: DefectReportState.statuses,
                weldingConfirmed: DefectReportState.weldingConfirmed,
                dateFrom: DefectReportState.dateFrom || undefined,
                dateTo: DefectReportState.dateTo || undefined,
            });
            DefectReportState.rows = report.rows || [];
            DefectReportState.projectCode = report.projectCode || "";
            renderDefectReportTable(DefectReportState.rows);
        } catch (error) {
            showToast(error.message, "error");
            clearDefectReportTable("خطا در اجرای گزارش");
        }
    });
}

function setDefectReportExportEnabled(enabled) {
    const summaryBtn = document.getElementById("btn-defect-report-export-summary");
    const detailBtn = document.getElementById("btn-defect-report-export-detail");
    if (summaryBtn) summaryBtn.disabled = !enabled;
    if (detailBtn) detailBtn.disabled = !enabled;
}

function clearDefectReportTable(message) {
    DefectReportState.rows = [];
    DefectReportState.projectCode = "";
    const tbody = document.getElementById("defect-report-table-body");
    if (tbody) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="9">${escapeHtml(message)}</td></tr>`;
    }
    setDefectReportExportEnabled(false);
}

function renderDefectReportTable(rows) {
    const tbody = document.getElementById("defect-report-table-body");
    if (!tbody) return;

    if (!rows.length) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="9">برای فیلترهای انتخاب‌شده نتیجه‌ای یافت نشد</td></tr>`;
        setDefectReportExportEnabled(false);
        return;
    }

    tbody.innerHTML = rows
        .map(
            (r, index) => `
        <tr class="defect-report-row" data-row-index="${index}">
            <td>${escapeHtml(r.projectCode || "")}</td>
            <td>${r.rowNum != null ? formatFaNumber(r.rowNum) : "—"}</td>
            <td>${escapeHtml(r.pieceName || "")}</td>
            <td>${escapeHtml(r.defectType || "")}</td>
            <td>${formatFaNumber(r.defectCount)}</td>
            <td>${formatFaNumber(r.piecesWithDefect)}</td>
            <td>${formatFaNumber(r.totalPiecesInRow)}</td>
            <td>${r.percentPiecesWithDefect != null ? formatPercent(r.percentPiecesWithDefect) : "—"}</td>
            <td class="defect-report-action-col">
                <button type="button" class="btn btn-secondary btn-sm defect-report-action-btn" data-row-index="${index}">مشاهده</button>
            </td>
        </tr>`
        )
        .join("");

    tbody.querySelectorAll(".defect-report-action-btn").forEach((btn) => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            const idx = Number(btn.dataset.rowIndex);
            const row = DefectReportState.rows[idx];
            if (row) openDefectReportPiecesModal(row);
        });
    });

    tbody.querySelectorAll(".defect-report-row").forEach((tr) => {
        tr.addEventListener("click", () => {
            const idx = Number(tr.dataset.rowIndex);
            const row = DefectReportState.rows[idx];
            if (row) openDefectReportPiecesModal(row);
        });
    });

    setDefectReportExportEnabled(true);
}

function openDefectReportPiecesModal(row) {
    const pieces = row.pieces || [];
    const title = `قطعات دارای ${row.defectType || "عیب"} — ردیف ${row.rowNum ?? "—"} (${row.pieceName || ""})`;

    const body = pieces.length
        ? `
        <p class="defect-report-pieces-hint">برای مشاهده پروفایل، روی هر قطعه کلیک کنید.</p>
        <div class="table-wrap table-wrap-scroll">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>شماره قطعه</th>
                        <th>وضعیت فعلی</th>
                        <th>تعداد عیب</th>
                        <th>نیاز به جوش</th>
                        <th>بدون نیاز به جوش</th>
                        <th>جوش تاییدشده</th>
                    </tr>
                </thead>
                <tbody>
                    ${pieces
                        .map(
                            (p) => `
                        <tr class="defect-report-piece-row" data-piece-id="${p.pieceId}" tabindex="0" role="button">
                            <td>${formatFaNumber(p.pieceNumber)}</td>
                            <td>
                                <span class="stage-badge stage-${p.currentStage}">
                                    <span class="badge-dot"></span>
                                    ${stageLabel(p.currentStage)}
                                </span>
                            </td>
                            <td>${formatFaNumber(p.defectCount)}</td>
                            <td>${formatFaNumber(p.needsWeldingCount)}</td>
                            <td>${formatFaNumber(p.approvedCount)}</td>
                            <td>${formatFaNumber(p.weldingConfirmedCount)}</td>
                        </tr>`
                        )
                        .join("")}
                </tbody>
            </table>
        </div>`
        : `<div class="alert-widget-empty">قطعه‌ای با این شرایط یافت نشد</div>`;

    openModal(title, body, { size: "lg" });

    document.querySelectorAll(".defect-report-piece-row").forEach((tr) => {
        const open = () => {
            const pieceId = tr.dataset.pieceId;
            if (pieceId && typeof openPieceProfile === "function") {
                openPieceProfile(pieceId);
            }
        };
        tr.addEventListener("click", open);
        tr.addEventListener("keydown", (e) => {
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                open();
            }
        });
    });
}

function clearDefectReportFilters() {
    const projectSelect = document.getElementById("defect-report-project");
    if (projectSelect) projectSelect.value = "";
    DefectReportState.projectId = "";
    DefectReportState.pieceDefinitionIds = [];
    DefectReportState.defectTypes = [];
    DefectReportState.statuses = [];
    DefectReportState.weldingConfirmed = [];
    DefectReportState.dateFrom = "";
    DefectReportState.dateTo = "";
    clearMultiFilter("defect-report-definitions");
    clearMultiFilter("defect-report-defect-types");
    clearMultiFilter("defect-report-statuses");
    clearMultiFilter("defect-report-welding-confirmed");
    if (DefectReportState.dateFromPicker) DefectReportState.dateFromPicker.clear();
    if (DefectReportState.dateToPicker) DefectReportState.dateToPicker.clear();
    loadDefectReportDefinitions("");
    clearDefectReportTable("برای مشاهده گزارش، یک پروژه انتخاب کنید و «اجرای گزارش» را بزنید");
}

function downloadCsv(filename, lines) {
    const bom = "\uFEFF";
    const blob = new Blob([bom + lines.join("\r\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}

function exportDefectReportSummaryCsv() {
    const rows = DefectReportState.rows || [];
    if (!rows.length) {
        showToast("ابتدا گزارش را اجرا کنید", "error");
        return;
    }

    const headers = [
        "کد پروژه",
        "ردیف",
        "نام قطعه",
        "نوع عیب",
        "تعداد عیب",
        "قطعات دارای عیب",
        "کل قطعات ردیف",
        "درصد قطعات دارای عیب",
    ];

    const lines = [headers.join(",")];
    rows.forEach((r) => {
        const percent =
            r.percentPiecesWithDefect != null ? String(r.percentPiecesWithDefect).replace(".", ",") : "";
        lines.push(
            [
                csvEscape(r.projectCode),
                r.rowNum ?? "",
                csvEscape(r.pieceName),
                csvEscape(r.defectType),
                r.defectCount,
                r.piecesWithDefect,
                r.totalPiecesInRow,
                percent,
            ].join(",")
        );
    });

    const code = DefectReportState.projectCode || "project";
    const today = new Date().toISOString().slice(0, 10);
    downloadCsv(`radiography-defects-summary-${code}-${today}.csv`, lines);
}

function exportDefectReportDetailCsv() {
    const rows = DefectReportState.rows || [];
    if (!rows.length) {
        showToast("ابتدا گزارش را اجرا کنید", "error");
        return;
    }

    const headers = [
        "کد پروژه",
        "ردیف",
        "نام قطعه",
        "نوع عیب",
        "شماره قطعه",
        "وضعیت فعلی",
        "تعداد عیب",
        "نیاز به جوش",
        "بدون نیاز به جوش",
        "جوش تاییدشده",
    ];

    const lines = [headers.join(",")];
    rows.forEach((r) => {
        (r.pieces || []).forEach((p) => {
            lines.push(
                [
                    csvEscape(r.projectCode),
                    r.rowNum ?? "",
                    csvEscape(r.pieceName),
                    csvEscape(r.defectType),
                    p.pieceNumber ?? "",
                    csvEscape(stageLabel(p.currentStage)),
                    p.defectCount,
                    p.needsWeldingCount,
                    p.approvedCount,
                    p.weldingConfirmedCount,
                ].join(",")
            );
        });
    });

    if (lines.length === 1) {
        showToast("جزئیات قطعه‌ای برای خروجی وجود ندارد", "error");
        return;
    }

    const code = DefectReportState.projectCode || "project";
    const today = new Date().toISOString().slice(0, 10);
    downloadCsv(`radiography-defects-pieces-${code}-${today}.csv`, lines);
}

function csvEscape(value) {
    const s = value == null ? "" : String(value);
    if (/[",\r\n]/.test(s)) {
        return `"${s.replace(/"/g, '""')}"`;
    }
    return s;
}

/* ===========================
   گزارش حضور قطعات در مراحل (بازه تاریخی)
   =========================== */

function initOccupancyReportUi() {
    const dateFromInput = document.getElementById("occupancy-report-date-from");
    const dateToInput = document.getElementById("occupancy-report-date-to");
    if (dateFromInput && typeof attachPersianDatePicker === "function") {
        OccupancyReportState.dateFromPicker = attachPersianDatePicker(dateFromInput, {
            onSelect: (d) => {
                OccupancyReportState.dateFrom = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                OccupancyReportState.dateFrom = "";
            },
        });
    }
    if (dateToInput && typeof attachPersianDatePicker === "function") {
        OccupancyReportState.dateToPicker = attachPersianDatePicker(dateToInput, {
            onSelect: (d) => {
                OccupancyReportState.dateTo = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            },
            onClear: () => {
                OccupancyReportState.dateTo = "";
            },
        });
    }

    document.getElementById("occupancy-report-project")?.addEventListener("change", (e) => {
        OccupancyReportState.projectId = e.target.value;
        const opt = e.target.options[e.target.selectedIndex];
        OccupancyReportState.projectCode = opt && opt.value ? opt.textContent : "";
    });

    document.getElementById("btn-occupancy-report-run")?.addEventListener("click", runOccupancyReport);
    document.getElementById("btn-occupancy-report-clear")?.addEventListener("click", clearOccupancyReportFilters);
    document.getElementById("btn-occupancy-report-export")?.addEventListener("click", exportOccupancyReportCsv);
}

function clearOccupancyReportFilters() {
    const projectSelect = document.getElementById("occupancy-report-project");
    if (projectSelect) projectSelect.value = "";
    OccupancyReportState.projectId = "";
    OccupancyReportState.projectCode = "";
    OccupancyReportState.dateFrom = "";
    OccupancyReportState.dateTo = "";
    OccupancyReportState.rows = [];
    if (OccupancyReportState.dateFromPicker) OccupancyReportState.dateFromPicker.clear();
    if (OccupancyReportState.dateToPicker) OccupancyReportState.dateToPicker.clear();
    clearOccupancyReportTable("تاریخ شروع و پایان را انتخاب کنید و «اجرای گزارش» را بزنید");
}

function clearOccupancyReportTable(message) {
    OccupancyReportState.rows = [];
    const tbody = document.getElementById("occupancy-report-table-body");
    if (tbody) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="8">${escapeHtml(message)}</td></tr>`;
    }
    const exportBtn = document.getElementById("btn-occupancy-report-export");
    if (exportBtn) exportBtn.disabled = true;
}

async function runOccupancyReport() {
    OccupancyReportState.projectId = document.getElementById("occupancy-report-project")?.value || "";
    if (!OccupancyReportState.dateFrom || !OccupancyReportState.dateTo) {
        showToast("انتخاب تاریخ شروع و پایان الزامی است", "error");
        return;
    }

    const tbody = document.getElementById("occupancy-report-table-body");
    showTableLoader(tbody, 8);
    const runBtn = document.getElementById("btn-occupancy-report-run");
    await withButtonLoader(runBtn, async () => {
        try {
            const report = await DashboardAPI.getStageOccupancyReport({
                projectId: OccupancyReportState.projectId || undefined,
                dateFrom: OccupancyReportState.dateFrom,
                dateTo: OccupancyReportState.dateTo,
            });
            OccupancyReportState.rows = report.rows || [];
            OccupancyReportState.projectCode = report.projectCode || "";
            renderOccupancyReportTable(OccupancyReportState.rows);
        } catch (error) {
            showToast(error.message, "error");
            clearOccupancyReportTable("خطا در اجرای گزارش");
        }
    });
}

function renderOccupancyReportTable(rows) {
    const tbody = document.getElementById("occupancy-report-table-body");
    const exportBtn = document.getElementById("btn-occupancy-report-export");
    if (!tbody) return;

    if (!rows.length) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="8">در این بازه قطعه‌ای با تاریخ انجام فرآیند پیدا نشد</td></tr>`;
        if (exportBtn) exportBtn.disabled = true;
        return;
    }

    tbody.innerHTML = rows
        .map(
            (r) => `
        <tr class="occupancy-report-row" data-piece-id="${r.pieceId}" tabindex="0" role="button">
            <td><span class="code-num">${escapeHtml(r.projectCode || "")}</span></td>
            <td>${formatFaNumber(r.rowNum)}</td>
            <td>${escapeHtml(r.pieceName || "")}</td>
            <td>${formatFaNumber(r.pieceNumber)}</td>
            <td>
                <span class="stage-badge stage-${r.stage}">
                    <span class="badge-dot"></span>
                    ${escapeHtml(stageLabel(r.stage))}
                </span>
                ${r.subStage ? `<span class="substage-badge">${escapeHtml(occupancySubStageLabel(r))}</span>` : ""}
            </td>
            <td>${formatDate(r.enteredAt)}</td>
            <td>${r.stillInStage ? "هنوز در این مرحله" : r.leftAt ? formatDate(r.leftAt) : "—"}</td>
            <td>${r.stillInStage ? "بله" : "خیر"}</td>
        </tr>`
        )
        .join("");

    tbody.querySelectorAll(".occupancy-report-row").forEach((tr) => {
        const open = () => {
            if (tr.dataset.pieceId && typeof openPieceProfile === "function") {
                openPieceProfile(tr.dataset.pieceId);
            }
        };
        tr.addEventListener("click", open);
        tr.addEventListener("keydown", (e) => {
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                open();
            }
        });
    });

    if (exportBtn) exportBtn.disabled = false;
}

function occupancySubStageLabel(row) {
    const labels = row.subStageKind === "RADIOGRAPHY" ? RADIOGRAPHY_SUBSTAGE_LABELS
        : row.subStageKind === "HEAT_TREATMENT" ? HEAT_TREATMENT_SUBSTAGE_LABELS
        : row.subStageKind === "ROUGH_MACHINING" ? ROUGH_MACHINING_SUBSTAGE_LABELS : {};
    return labels[row.subStage] || row.subStage || "";
}

function exportOccupancyReportCsv() {
    const rows = OccupancyReportState.rows || [];
    if (!rows.length) {
        showToast("ابتدا گزارش را اجرا کنید", "error");
        return;
    }

    const headers = [
        "کد پروژه",
        "ردیف",
        "نام قطعه",
        "شماره قطعه",
        "مرحله",
        "زیرمرحله",
        "ورود به مرحله",
        "خروج از مرحله",
        "هنوز در این مرحله",
    ];
    const lines = [headers.join(",")];
    rows.forEach((r) => {
        lines.push(
            [
                csvEscape(r.projectCode),
                r.rowNum ?? "",
                csvEscape(r.pieceName),
                r.pieceNumber ?? "",
                csvEscape(stageLabel(r.stage)),
                csvEscape(occupancySubStageLabel(r)),
                csvEscape(formatDate(r.enteredAt)),
                csvEscape(r.stillInStage ? "هنوز در این مرحله" : r.leftAt ? formatDate(r.leftAt) : ""),
                r.stillInStage ? "بله" : "خیر",
            ].join(",")
        );
    });

    const code = OccupancyReportState.projectCode || "all";
    const today = new Date().toISOString().slice(0, 10);
    downloadCsv(`stage-occupancy-${code}-${OccupancyReportState.dateFrom}-${OccupancyReportState.dateTo}-${today}.csv`, lines);
}

/* ===========================
   گزارش قطعات ایستگاه جوشکاری
   =========================== */

function initWeldingReportUi() {
    document.getElementById("welding-report-project")?.addEventListener("change", (e) => {
        WeldingReportState.projectId = e.target.value;
        const opt = e.target.options[e.target.selectedIndex];
        WeldingReportState.projectCode = opt && opt.value ? opt.textContent : "";
    });
    document.getElementById("btn-welding-report-run")?.addEventListener("click", runWeldingReport);
    document.getElementById("btn-welding-report-export")?.addEventListener("click", exportWeldingReportCsv);
}

function setWeldingReportExportEnabled(enabled) {
    const exportBtn = document.getElementById("btn-welding-report-export");
    if (exportBtn) exportBtn.disabled = !enabled;
}

function clearWeldingReportTable(message) {
    WeldingReportState.rows = [];
    const tbody = document.getElementById("welding-report-table-body");
    if (tbody) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="7">${escapeHtml(message)}</td></tr>`;
    }
    setWeldingReportExportEnabled(false);
}

async function runWeldingReport() {
    WeldingReportState.projectId = document.getElementById("welding-report-project")?.value || "";
    const tbody = document.getElementById("welding-report-table-body");
    showTableLoader(tbody, 7);
    const runBtn = document.getElementById("btn-welding-report-run");
    await withButtonLoader(runBtn, async () => {
        try {
            const rows = await DashboardAPI.getWeldingStationReport({
                projectId: WeldingReportState.projectId || undefined,
            });
            WeldingReportState.rows = rows || [];
            renderWeldingReportTable(WeldingReportState.rows);
        } catch (error) {
            showToast(error.message, "error");
            clearWeldingReportTable("خطا در اجرای گزارش");
        }
    });
}

function renderWeldingReportTable(rows) {
    const tbody = document.getElementById("welding-report-table-body");
    if (!tbody) return;

    if (!rows.length) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="7">قطعه‌ای در ایستگاه جوشکاری نیست</td></tr>`;
        setWeldingReportExportEnabled(false);
        return;
    }

    tbody.innerHTML = rows
        .map(
            (r) => `
        <tr class="welding-report-row" data-piece-id="${r.pieceId}" tabindex="0" role="button">
            <td><span class="code-num">${escapeHtml(r.projectCode || "")}</span></td>
            <td>${escapeHtml(r.pieceName || "")}</td>
            <td>${formatFaNumber(r.pieceNumber)}</td>
            <td>${escapeHtml(r.defectType || "—")}</td>
            <td>${escapeHtml(r.notes || "—")}</td>
            <td>${escapeHtml(r.sentBy || "—")}</td>
            <td>${r.sentAt ? formatDateTime(r.sentAt) : "—"}</td>
        </tr>`
        )
        .join("");

    tbody.querySelectorAll(".welding-report-row").forEach((tr) => {
        const open = () => {
            if (tr.dataset.pieceId && typeof openPieceProfile === "function") {
                openPieceProfile(tr.dataset.pieceId);
            }
        };
        tr.addEventListener("click", open);
        tr.addEventListener("keydown", (e) => {
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                open();
            }
        });
    });

    setWeldingReportExportEnabled(true);
}

function exportWeldingReportCsv() {
    const rows = WeldingReportState.rows || [];
    if (!rows.length) {
        showToast("ابتدا گزارش را اجرا کنید", "error");
        return;
    }

    const headers = [
        "کد پروژه",
        "نام قطعه",
        "شماره قطعه",
        "نوع عیب",
        "توضیحات",
        "ارسال‌کننده",
        "تاریخ ارسال",
    ];
    const lines = [headers.join(",")];
    rows.forEach((r) => {
        lines.push(
            [
                csvEscape(r.projectCode),
                csvEscape(r.pieceName),
                r.pieceNumber ?? "",
                csvEscape(r.defectType || ""),
                csvEscape(r.notes || ""),
                csvEscape(r.sentBy || ""),
                csvEscape(r.sentAt ? formatDateTime(r.sentAt) : ""),
            ].join(",")
        );
    });

    const code = WeldingReportState.projectCode || "all";
    const today = new Date().toISOString().slice(0, 10);
    downloadCsv(`welding-station-${code}-${today}.csv`, lines);
}

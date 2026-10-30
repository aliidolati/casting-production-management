/**
 * pieces.js
 * منطق مربوط به نمایش لیست Piece های یک PieceDefinition،
 * ساخت Piece جدید، و تغییر وضعیت (Stage) هر Piece.
 */

// آخرین لیست قطعاتی که برای تعریف قطعه‌ی جاری لود شده (برای محاسبه‌ی شماره‌ی پیشنهادی شروع در ساخت دسته‌ای)
let currentDefinitionPieces = [];
let currentDefinitionArchivedPieces = [];
let showArchivedPieces = false;
let selectedPieceIds = new Set();
let pendingOpenRowReport = false;
let currentRowStatusReport = null;

function canManagePieces() {
    return hasPermission("MANAGE_PIECES");
}

function piecesTableColspan() {
    return canManagePieces() ? 6 : 5;
}

/* ===========================
   جزئیات یک تعریف قطعه (لیست Piece ها)
   =========================== */

async function openDefinitionDetail(definitionId) {
    AppState.currentDefinitionId = definitionId;
    selectedPieceIds = new Set();
    currentRowStatusReport = null;
    const tbody = document.getElementById("pieces-table-body");
    showTableLoader(tbody, piecesTableColspan());
    showSection("definition-detail-section");
    renderPiecesBulkBar();
    hideRowStatusReport();

    try {
        const definition = await PieceDefinitionAPI.getById(definitionId);
        document.getElementById("definition-detail-title").textContent =
            `ردیف ${definition.rowNum} — قطعه ${definition.pieceName}`;

        const [pieces, archivedPieces] = await Promise.all([
            PieceAPI.getByDefinition(definitionId, false),
            PieceAPI.getByDefinition(definitionId, true),
        ]);
        currentDefinitionPieces = pieces;
        currentDefinitionArchivedPieces = archivedPieces;
        const chk = document.getElementById("chk-show-archived-pieces");
        if (chk) chk.checked = showArchivedPieces;
        renderRowStatusSummary();
        renderPiecesTable(showArchivedPieces ? archivedPieces : pieces);
        if (pendingOpenRowReport) {
            pendingOpenRowReport = false;
            await openRowStatusReport();
        }
    } catch (error) {
        showToast(error.message, "error");
    }
}

function nextSuggestedPieceNumber() {
    const numbers = [...currentDefinitionPieces, ...currentDefinitionArchivedPieces].map((p) => p.pieceNumber);
    if (numbers.length === 0) return 1;
    return Math.max(...numbers) + 1;
}

function renderPiecesTable(pieces) {
    const tbody = document.getElementById("pieces-table-body");
    const selectAllCell = document.getElementById("pieces-select-all-cell");
    const showChecks = canManagePieces();
    if (selectAllCell) selectAllCell.style.display = showChecks ? "" : "none";

    if (pieces.length === 0) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="${piecesTableColspan()}">${showArchivedPieces ? "قطعه بایگانی‌شده‌ای وجود ندارد" : "هنوز هیچ قطعه‌ای برای این تعریف ثبت نشده است"}</td></tr>`;
        const selectAll = document.getElementById("pieces-select-all-checkbox");
        if (selectAll) {
            selectAll.checked = false;
            selectAll.indeterminate = false;
        }
        renderPiecesBulkBar();
        return;
    }

    // مرتب‌سازی بر اساس شماره قطعه
    const sorted = [...pieces].sort((a, b) => a.pieceNumber - b.pieceNumber);

    tbody.innerHTML = sorted
        .map(
            (p) => `
        <tr data-piece-id="${p.id}">
            ${
                showChecks
                    ? `<td class="checkbox-cell"><input type="checkbox" class="piece-row-checkbox" data-id="${p.id}" ${selectedPieceIds.has(p.id) ? "checked" : ""} /></td>`
                    : ""
            }
            <td>${p.pieceNumber}</td>
            <td>
                ${
                    hasPermission("MOVE_PIECE_STAGE") && p.currentStage !== "WELDING" && !p.archivedAt
                        ? `<select class="stage-select" data-piece-id="${p.id}">
                    ${selectableStages(p)
                        .map(
                            (s) => `<option value="${s}" ${s === p.currentStage ? "selected" : ""}>${stageLabel(s)}</option>`
                        )
                        .join("")}
                </select>`
                        : `<span class="stage-badge stage-${p.currentStage}"><span class="badge-dot"></span>${stageLabel(p.currentStage)}</span>`
                }
            </td>
            <td>${formatDate(p.currentStageEnteredAt)}</td>
            <td>${formatDate(p.createdAt)}</td>
            <td class="row-actions">${
                hasPermission("MANAGE_PIECES")
                    ? p.archivedAt
                        ? `<button class="btn btn-secondary btn-restore-piece" data-id="${p.id}">بازگردانی</button>`
                        : `<button class="btn btn-edit btn-edit-piece" data-id="${p.id}" data-piece-number="${p.pieceNumber}">ویرایش</button>
                       <button class="btn btn-danger btn-delete-piece" data-id="${p.id}">بایگانی</button>`
                    : ""
            }</td>
        </tr>
    `
        )
        .join("");

    tbody.querySelectorAll("tr[data-piece-id]").forEach((row) => {
        row.addEventListener("click", (e) => {
            if (e.target.closest(".checkbox-cell") || e.target.closest(".row-actions") || e.target.closest(".stage-select")) {
                return;
            }
            openPieceProfile(row.dataset.pieceId);
        });
    });

    tbody.querySelectorAll(".piece-row-checkbox").forEach((checkbox) => {
        checkbox.addEventListener("click", (e) => e.stopPropagation());
        checkbox.addEventListener("change", () => {
            if (checkbox.checked) selectedPieceIds.add(checkbox.dataset.id);
            else selectedPieceIds.delete(checkbox.dataset.id);
            syncPiecesSelectAllCheckbox();
            renderPiecesBulkBar();
        });
    });

    // تغییر وضعیت از طریق select
    tbody.querySelectorAll(".stage-select").forEach((select) => {
        select.addEventListener("click", (e) => e.stopPropagation());
        select.addEventListener("change", async (e) => {
            e.stopPropagation();
            const pieceId = select.dataset.pieceId;
            const newStage = select.value;
            const piece = currentDefinitionPieces.find((p) => p.id === pieceId);
            const previousStage = piece ? piece.currentStage : null;

            if (newStage === "WELDING") {
                showToast("جوشکاری ایستگاه است؛ از پروفایل قطعه ارسال کنید", "error");
                openDefinitionDetail(AppState.currentDefinitionId);
                return;
            }
            if (piece && piece.currentStage === newStage) {
                showToast("این قطعه هم‌اکنون در همین مرحله است و انتقال تکراری مجاز نیست", "error");
                return;
            }

            const confirmed = confirm(
                `وضعیت قطعه شماره ${piece ? piece.pieceNumber : ""} به «${stageLabel(newStage)}» تغییر کند؟`
            );
            if (!confirmed) {
                if (previousStage) select.value = previousStage;
                return;
            }

            try {
                await PieceAPI.updateStage(pieceId, newStage);
                showToast("وضعیت قطعه با موفقیت تغییر کرد");
                openDefinitionDetail(AppState.currentDefinitionId);
            } catch (error) {
                showToast(error.message, "error");
                openDefinitionDetail(AppState.currentDefinitionId);
            }
        });
    });

    tbody.querySelectorAll(".row-actions").forEach((cell) => {
        cell.addEventListener("click", (e) => e.stopPropagation());
    });
    tbody.querySelectorAll(".btn-edit-piece").forEach((btn) => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            openEditPieceModal(btn.dataset.id, btn.dataset.pieceNumber);
        });
    });

    // حذف یک قطعه
    tbody.querySelectorAll(".btn-delete-piece").forEach((btn) => {
        btn.addEventListener("click", async (e) => {
            e.stopPropagation();
            if (!confirm("این قطعه بایگانی می‌شود و از فهرست‌های اصلی پنهان می‌گردد؛ از دیتابیس حذف نخواهد شد. ادامه می‌دهید؟")) return;

            try {
                await PieceAPI.delete(btn.dataset.id);
                showToast("قطعه بایگانی شد");
                openDefinitionDetail(AppState.currentDefinitionId);
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });

    tbody.querySelectorAll(".btn-restore-piece").forEach((btn) => {
        btn.addEventListener("click", async (e) => {
            e.stopPropagation();
            if (!confirm("این قطعه دوباره در فهرست‌های اصلی نمایش داده می‌شود. ادامه می‌دهید؟")) return;
            try {
                await PieceAPI.restore(btn.dataset.id);
                showToast("قطعه بازگردانی شد");
                openDefinitionDetail(AppState.currentDefinitionId);
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });

    syncPiecesSelectAllCheckbox();
    renderPiecesBulkBar();
}

function visiblePieceIds() {
    const list = showArchivedPieces ? currentDefinitionArchivedPieces : currentDefinitionPieces;
    return list.map((p) => p.id);
}

function syncPiecesSelectAllCheckbox() {
    const selectAll = document.getElementById("pieces-select-all-checkbox");
    if (!selectAll) return;
    const ids = visiblePieceIds();
    if (ids.length === 0) {
        selectAll.checked = false;
        selectAll.indeterminate = false;
        return;
    }
    const selectedVisible = ids.filter((id) => selectedPieceIds.has(id)).length;
    selectAll.checked = selectedVisible === ids.length;
    selectAll.indeterminate = selectedVisible > 0 && selectedVisible < ids.length;
}

function renderPiecesBulkBar() {
    const bar = document.getElementById("pieces-bulk-actions-bar");
    if (!bar) return;
    const archiveBtn = document.getElementById("btn-bulk-archive-pieces");
    const restoreBtn = document.getElementById("btn-bulk-restore-pieces");

    if (!canManagePieces() || selectedPieceIds.size === 0) {
        bar.style.display = "none";
        return;
    }

    bar.style.display = "flex";
    document.getElementById("pieces-bulk-actions-count").textContent = `${selectedPieceIds.size} قطعه انتخاب شده`;
    if (archiveBtn) archiveBtn.style.display = showArchivedPieces ? "none" : "";
    if (restoreBtn) restoreBtn.style.display = showArchivedPieces ? "" : "none";
}

function clearPieceSelection() {
    selectedPieceIds = new Set();
    document.querySelectorAll(".piece-row-checkbox").forEach((cb) => {
        cb.checked = false;
    });
    syncPiecesSelectAllCheckbox();
    renderPiecesBulkBar();
}

async function archiveProjectPieces(projectId) {
    if (!confirm("همه قطعات فعال این پروژه بایگانی می‌شوند و از فهرست‌های اصلی پنهان می‌گردند؛ از دیتابیس حذف نخواهند شد. ادامه می‌دهید؟")) {
        return false;
    }
    try {
        const result = await ProjectAPI.archivePieces(projectId);
        showToast(`${result.processedCount} قطعه بایگانی شد`);
        return true;
    } catch (error) {
        showToast(error.message, "error");
        return false;
    }
}

async function archiveDefinitionPieces(definitionId) {
    if (!confirm("همه قطعات فعال این ردیف بایگانی می‌شوند و از فهرست‌های اصلی پنهان می‌گردند؛ از دیتابیس حذف نخواهند شد. ادامه می‌دهید؟")) {
        return false;
    }
    try {
        const result = await PieceDefinitionAPI.archivePieces(definitionId);
        showToast(`${result.processedCount} قطعه بایگانی شد`);
        return true;
    } catch (error) {
        showToast(error.message, "error");
        return false;
    }
}

/* ===========================
   خلاصه و گزارش وضعیت ردیف
   =========================== */

function renderRowStatusSummary() {
    const box = document.getElementById("row-status-summary");
    if (!box) return;
    const active = currentDefinitionPieces.length;
    const archived = currentDefinitionArchivedPieces.length;
    const total = active + archived;
    box.innerHTML = `
        <div class="row-status-summary-card card-tilt">
            <span class="metric-label">کل قطعات ردیف</span>
            <span class="metric-value">${total}</span>
        </div>
        <div class="row-status-summary-card card-tilt">
            <span class="metric-label">فعال</span>
            <span class="metric-value">${active}</span>
        </div>
        <div class="row-status-summary-card card-tilt">
            <span class="metric-label">بایگانی‌شده</span>
            <span class="metric-value">${archived}</span>
        </div>
    `;
}

function hideRowStatusReport() {
    const panel = document.getElementById("row-status-report");
    if (panel) {
        panel.hidden = true;
        panel.innerHTML = "";
    }
}

function pieceSubStageLabel(piece) {
    if (piece.currentStage === "RADIOGRAPHY") {
        return (typeof RADIOGRAPHY_SUBSTAGE_LABELS !== "undefined" && RADIOGRAPHY_SUBSTAGE_LABELS[piece.radiographySubStage]) || "";
    }
    if (piece.currentStage === "HEAT_TREATMENT") {
        return (typeof HEAT_TREATMENT_SUBSTAGE_LABELS !== "undefined" && HEAT_TREATMENT_SUBSTAGE_LABELS[piece.heatTreatmentSubStage]) || "";
    }
    if (piece.currentStage === "ROUGH_MACHINING") {
        return (typeof ROUGH_MACHINING_SUBSTAGE_LABELS !== "undefined" && ROUGH_MACHINING_SUBSTAGE_LABELS[piece.roughMachiningSubStage]) || "";
    }
    return "";
}

function renderRowStatusReport(report) {
    const panel = document.getElementById("row-status-report");
    if (!panel) return;
    currentRowStatusReport = report;
    const stageRows = (report.stageCounts || [])
        .filter((s) => s.totalCount > 0)
        .map(
            (s) => `
            <tr>
                <td>${escapeHtml(stageLabel(s.stage))}</td>
                <td>${s.activeCount}</td>
                <td>${s.archivedCount}</td>
                <td>${s.totalCount}</td>
            </tr>`
        )
        .join("");

    const pieceRows = [...(report.pieces || [])]
        .sort((a, b) => a.pieceNumber - b.pieceNumber)
        .map((p) => {
            const sub = pieceSubStageLabel(p);
            return `
            <tr class="${p.archivedAt ? "archived-piece-row" : ""}">
                <td>${p.pieceNumber}</td>
                <td>
                    <span class="stage-badge stage-${p.currentStage}"><span class="badge-dot"></span>${escapeHtml(stageLabel(p.currentStage))}</span>
                    ${sub ? `<span class="substage-badge">${escapeHtml(sub)}</span>` : ""}
                </td>
                <td>${p.archivedAt ? "بایگانی‌شده" : "فعال"}</td>
                <td>${p.archivedAt ? formatDateTime(p.archivedAt) : "—"}</td>
                <td>${formatDate(p.currentStageEnteredAt)}</td>
                <td>${formatDate(p.createdAt)}</td>
            </tr>`;
        })
        .join("");

    panel.innerHTML = `
        <div class="row-status-report-header">
            <h3 class="subsection-title">گزارش وضعیت قطعات ردیف ${escapeHtml(String(report.rowNum))} — ${escapeHtml(report.pieceName)}</h3>
            <div class="section-header-actions">
                <button type="button" class="btn btn-secondary btn-sm" id="btn-export-row-status-report">خروجی CSV</button>
                <button type="button" class="btn btn-secondary btn-sm" id="btn-print-row-status-report">چاپ</button>
                <button type="button" class="btn btn-secondary btn-sm" id="btn-close-row-status-report">بستن گزارش</button>
            </div>
        </div>
        <p class="form-hint">پروژه ${escapeHtml(report.projectCode)} — ${escapeHtml(report.projectName || "")} | کل: ${report.totalCount} | فعال: ${report.activeCount} | بایگانی: ${report.archivedCount}</p>
        <div class="table-wrap">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>مرحله</th>
                        <th>فعال</th>
                        <th>بایگانی‌شده</th>
                        <th>جمع</th>
                    </tr>
                </thead>
                <tbody>
                    ${stageRows || `<tr class="empty-row"><td colspan="4">قطعه‌ای در این ردیف ثبت نشده است</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="table-wrap table-wrap-scroll">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>شماره قطعه</th>
                        <th>وضعیت فعلی</th>
                        <th>بایگانی</th>
                        <th>تاریخ بایگانی</th>
                        <th>ورود به مرحله</th>
                        <th>تاریخ ثبت</th>
                    </tr>
                </thead>
                <tbody>
                    ${pieceRows || `<tr class="empty-row"><td colspan="6">قطعه‌ای در این ردیف ثبت نشده است</td></tr>`}
                </tbody>
            </table>
        </div>
    `;
    panel.hidden = false;

    document.getElementById("btn-export-row-status-report")?.addEventListener("click", exportRowStatusReportCsv);
    document.getElementById("btn-print-row-status-report")?.addEventListener("click", printRowStatusReport);
    document.getElementById("btn-close-row-status-report")?.addEventListener("click", hideRowStatusReport);
}

async function openRowStatusReport() {
    if (!AppState.currentDefinitionId) return;
    const panel = document.getElementById("row-status-report");
    if (panel) {
        panel.hidden = false;
        panel.innerHTML = `<div class="loader-wrap">${loaderMarkup()}</div>`;
    }
    try {
        const report = await PieceDefinitionAPI.getStatusReport(AppState.currentDefinitionId);
        renderRowStatusReport(report);
        panel?.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (error) {
        hideRowStatusReport();
        showToast(error.message, "error");
    }
}

function exportRowStatusReportCsv() {
    const report = currentRowStatusReport;
    if (!report) {
        showToast("ابتدا گزارش را باز کنید", "error");
        return;
    }
    const escape = typeof csvEscape === "function" ? csvEscape : (v) => String(v ?? "");
    const download = typeof downloadCsv === "function" ? downloadCsv : null;
    const headers = ["شماره قطعه", "مرحله", "زیرمرحله", "وضعیت بایگانی", "تاریخ بایگانی", "ورود به مرحله", "تاریخ ثبت"];
    const lines = [headers.map(escape).join(",")];
    [...(report.pieces || [])]
        .sort((a, b) => a.pieceNumber - b.pieceNumber)
        .forEach((p) => {
            lines.push(
                [
                    p.pieceNumber,
                    stageLabel(p.currentStage),
                    pieceSubStageLabel(p),
                    p.archivedAt ? "بایگانی‌شده" : "فعال",
                    p.archivedAt ? formatDateTime(p.archivedAt) : "",
                    formatDate(p.currentStageEnteredAt),
                    formatDate(p.createdAt),
                ]
                    .map(escape)
                    .join(",")
            );
        });
    const filename = `row-${report.rowNum}-${report.projectCode || "report"}.csv`;
    if (download) {
        download(filename, lines);
    } else {
        const blob = new Blob(["\uFEFF" + lines.join("\r\n")], { type: "text/csv;charset=utf-8;" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
    }
}

function printRowStatusReport() {
    const panel = document.getElementById("row-status-report");
    if (!panel || panel.hidden) {
        showToast("ابتدا گزارش را باز کنید", "error");
        return;
    }
    document.body.classList.add("printing-row-report");
    const cleanup = () => document.body.classList.remove("printing-row-report");
    window.addEventListener("afterprint", cleanup, { once: true });
    window.print();
}

/* ===========================
   ویرایش قطعه (مودال)
   =========================== */

function openEditPieceModal(id, pieceNumber) {
    openModal(
        "ویرایش قطعه",
        `
        <form id="form-edit-piece">
            <div class="form-group">
                <label for="input-edit-piece-number">شماره قطعه</label>
                <input type="number" id="input-edit-piece-number" value="${pieceNumber}" required />
            </div>
            <div class="form-error" id="form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-edit-piece">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
    `
    );

    document.getElementById("btn-cancel-edit-piece").addEventListener("click", closeModal);

    document.getElementById("form-edit-piece").addEventListener("submit", async (e) => {
        e.preventDefault();

        const newPieceNumber = parseInt(document.getElementById("input-edit-piece-number").value, 10);
        const errorBox = document.getElementById("form-error");

        try {
            await PieceAPI.update(id, { pieceNumber: newPieceNumber });
            closeModal();
            showToast("قطعه با موفقیت ویرایش شد");
            openDefinitionDetail(AppState.currentDefinitionId);
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
}

/* ===========================
   ساخت قطعه جدید (مودال)
   =========================== */

document.getElementById("btn-add-piece").addEventListener("click", (e) => {
    e.stopPropagation();
    openModal(
        "افزودن قطعه جدید",
        `
        <form id="form-add-piece">
            <div class="form-group">
                <label for="input-piece-number">شماره قطعه</label>
                <input type="number" id="input-piece-number" placeholder="مثلاً 1" required />
            </div>
            <div class="form-error" id="form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-add-piece">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
    `
    );

    document.getElementById("btn-cancel-add-piece").addEventListener("click", closeModal);

    document.getElementById("form-add-piece").addEventListener("submit", async (e) => {
        e.preventDefault();

        const pieceNumber = parseInt(document.getElementById("input-piece-number").value, 10);
        const errorBox = document.getElementById("form-error");

        try {
            await PieceAPI.create({
                pieceDefinitionId: AppState.currentDefinitionId,
                pieceNumber,
            });
            closeModal();
            showToast("قطعه با موفقیت ساخته شد");
            openDefinitionDetail(AppState.currentDefinitionId);
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
});

/* ===========================
   ساخت دسته‌ای قطعات (مودال)
   =========================== */

document.getElementById("chk-show-archived-pieces")?.addEventListener("change", (e) => {
    showArchivedPieces = e.target.checked;
    selectedPieceIds = new Set();
    renderPiecesTable(showArchivedPieces ? currentDefinitionArchivedPieces : currentDefinitionPieces);
});

document.getElementById("btn-bulk-add-piece").addEventListener("click", (e) => {
    e.stopPropagation();
    const suggestedStart = nextSuggestedPieceNumber();

    openModal(
        "افزودن دسته‌ای قطعات",
        `
        <form id="form-bulk-add-piece">
            <div class="form-group">
                <label for="input-bulk-start-number">شماره شروع</label>
                <input type="number" id="input-bulk-start-number" value="${suggestedStart}" min="1" required />
            </div>
            <div class="form-group">
                <label for="input-bulk-quantity">تعداد قطعات</label>
                <input type="number" id="input-bulk-quantity" placeholder="مثلاً 100" min="1" max="1000" required />
            </div>
            <div class="form-error" id="form-error" style="display: none;"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-bulk-add-piece">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
    `
    );

    document.getElementById("btn-cancel-bulk-add-piece").addEventListener("click", closeModal);

    document.getElementById("form-bulk-add-piece").addEventListener("submit", async (e) => {
        e.preventDefault();

        const startNumber = parseInt(document.getElementById("input-bulk-start-number").value, 10);
        const quantity = parseInt(document.getElementById("input-bulk-quantity").value, 10);
        const errorBox = document.getElementById("form-error");
        const submitBtn = e.target.querySelector('button[type="submit"]');

        submitBtn.disabled = true;

        try {
            const created = await PieceAPI.bulkCreate({
                pieceDefinitionId: AppState.currentDefinitionId,
                startNumber,
                quantity,
            });
            closeModal();
            showToast(`${created.length} قطعه با موفقیت ساخته شد (شماره ${startNumber} تا ${startNumber + created.length - 1})`);
            openDefinitionDetail(AppState.currentDefinitionId);
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
            submitBtn.disabled = false;
        }
    });
});

document.getElementById("pieces-select-all-checkbox")?.addEventListener("click", (e) => e.stopPropagation());
document.getElementById("pieces-select-all-checkbox")?.addEventListener("change", (e) => {
    const ids = visiblePieceIds();
    if (e.target.checked) ids.forEach((id) => selectedPieceIds.add(id));
    else ids.forEach((id) => selectedPieceIds.delete(id));
    document.querySelectorAll(".piece-row-checkbox").forEach((cb) => {
        cb.checked = selectedPieceIds.has(cb.dataset.id);
    });
    syncPiecesSelectAllCheckbox();
    renderPiecesBulkBar();
});

document.getElementById("btn-pieces-clear-selection")?.addEventListener("click", () => clearPieceSelection());

document.getElementById("btn-bulk-archive-pieces")?.addEventListener("click", async () => {
    const ids = [...selectedPieceIds];
    if (ids.length === 0) return;
    if (!confirm(`${ids.length} قطعه انتخاب‌شده بایگانی می‌شوند و از فهرست‌های اصلی پنهان می‌گردند. ادامه می‌دهید؟`)) return;
    try {
        const result = await PieceAPI.bulkArchive(ids);
        showToast(`${result.processedCount} قطعه بایگانی شد`);
        openDefinitionDetail(AppState.currentDefinitionId);
    } catch (error) {
        showToast(error.message, "error");
    }
});

document.getElementById("btn-bulk-restore-pieces")?.addEventListener("click", async () => {
    const ids = [...selectedPieceIds];
    if (ids.length === 0) return;
    if (!confirm(`${ids.length} قطعه انتخاب‌شده دوباره در فهرست‌های اصلی نمایش داده می‌شوند. ادامه می‌دهید؟`)) return;
    try {
        const result = await PieceAPI.bulkRestore(ids);
        showToast(`${result.processedCount} قطعه بازگردانی شد`);
        openDefinitionDetail(AppState.currentDefinitionId);
    } catch (error) {
        showToast(error.message, "error");
    }
});

document.getElementById("btn-archive-all-definition-pieces")?.addEventListener("click", async () => {
    if (!AppState.currentDefinitionId) return;
    const archived = await archiveDefinitionPieces(AppState.currentDefinitionId);
    if (archived) openDefinitionDetail(AppState.currentDefinitionId);
});

document.getElementById("btn-row-status-report")?.addEventListener("click", async () => {
    const panel = document.getElementById("row-status-report");
    if (panel && !panel.hidden) {
        hideRowStatusReport();
        return;
    }
    await openRowStatusReport();
});

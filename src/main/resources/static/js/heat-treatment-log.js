/**
 * heat-treatment-log.js
 * لاگ عملیات حرارتی: آیکن پروفایل قطعه، پاپ‌آپ تاریخچه، و میانبر کوره‌های داشبورد.
 *
 * پارامترهای سیکل (تاریخ، دما، زمان، سایز کوره) روی محموله‌ی کوره ذخیره می‌شوند
 * و بین همه‌ی قطعات آن محموله مشترک‌اند؛ تست پلیت مخصوص هر قطعه است.
 */

const FURNACE_SIZE_LABELS = {
    ONE_METER: "۱ متری",
    TWO_METER: "۲ متری",
    TWO_METER_C: "۲ متری چ",
};

const HeatLogState = {
    mode: "profile",
    treatmentDateIso: null,
    todayBatches: [],
    suggestedBatchCode: null,
    lockedBatch: null,
    batchPieces: [],
    dispositions: [],
    batchLookupTimer: null,
    pieceLookupTimer: null,
    profilePiece: null,
};

function canManageHeatTreatmentLog() {
    return hasPermission("MANAGE_HEAT_TREATMENT_LOG");
}

function formatHeatLogNumber(value) {
    if (value === null || value === undefined) return "—";
    const num = Number(value);
    return Number.isNaN(num) ? "—" : num.toLocaleString("fa-IR", { maximumFractionDigits: 2 });
}

function pieceRefLabel(piece) {
    if (!piece) return "";
    const code = piece.projectCode ?? "";
    const row = piece.rowNum ?? "";
    const number = piece.pieceNumber ?? "";
    return `${code}-${row}-${number}`;
}

/* ===========================
   آیکن در پروفایل قطعه
   =========================== */

function renderHeatTreatmentLogIcon(records) {
    const badge = records.length
        ? `<span class="heat-log-icon-badge">${records.length}</span>`
        : "";

    const label = records.length ? `عملیات حرارتی — ${records.length} رکورد` : "عملیات حرارتی";
    return `
        <button type="button"
            class="profile-heat-log-icon"
            id="btn-heat-log-icon"
            title="${label}"
            aria-label="${label}">
            <span class="heat-log-icon-symbol"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3c1 3-1 5-2 6 2 0 4 2 4 4.5a4 4 0 1 1-8 0c0-1.5 1-3 2-4-.5 1.5-.5 3 .5 4 0-3 2-6 3.5-10.5z"/></svg></span>
            ${badge}
        </button>
    `;
}

function attachHeatTreatmentLogIconEvents(piece) {
    const btn = document.getElementById("btn-heat-log-icon");
    if (!btn) return;

    btn.addEventListener("click", (e) => {
        e.stopPropagation();
        openHeatTreatmentLogModal(piece);
    });
}

function syncDashboardFurnaceShortcut() {
    const btn = document.getElementById("btn-dashboard-furnaces");
    if (!btn) return;
    btn.classList.toggle("hidden", !(canManageHeatTreatmentLog() || hasPermission("VIEW_DASHBOARD") || hasPermission("EDIT_HEAT_TREATMENT_BATCH")));
}

/* ===========================
   پاپ‌آپ تاریخچه پروفایل
   =========================== */

async function openHeatTreatmentLogModal(piece) {
    let records;
    let dispositions = [];
    let nextCode = null;
    try {
        records = await HeatTreatmentAPI.getByPiece(piece.id);
        if (canManageHeatTreatmentLog()) {
            [dispositions, HeatLogState.todayBatches, nextCode] = await Promise.all([
                loadPieceDispositionOptions(piece.id),
                HeatTreatmentAPI.getTodayBatches(),
                HeatTreatmentAPI.getNextBatchCode(),
            ]);
        }
    } catch (error) {
        showToast(error.message, "error");
        return;
    }

    HeatLogState.mode = "profile";
    HeatLogState.profilePiece = piece;
    HeatLogState.treatmentDateIso = null;
    HeatLogState.lockedBatch = null;
    HeatLogState.batchPieces = [];
    HeatLogState.dispositions = dispositions;
    HeatLogState.suggestedBatchCode = nextCode ? nextCode.code : null;

    openModal(
        `عملیات حرارتی — قطعه ${formatHeatLogNumber(piece.pieceNumber)}`,
        `<div id="heat-log-modal-body">${renderHeatTreatmentLogPanel(records, dispositions)}</div>`,
        { size: "lg" }
    );

    attachHeatTreatmentLogPanelEvents(piece);
}

async function openFurnaceDashboardModal() {
    if (!canManageHeatTreatmentLog()) {
        if(hasPermission('VIEW_DASHBOARD') || hasPermission('EDIT_HEAT_TREATMENT_BATCH')) return openHeatBatchArchive();
        showToast("شما اجازه مشاهده محموله‌های کوره را ندارید", "error");
        return;
    }

    let nextCode = null;
    try {
        [HeatLogState.todayBatches, nextCode] = await Promise.all([
            HeatTreatmentAPI.getTodayBatches(),
            HeatTreatmentAPI.getNextBatchCode(),
        ]);
    } catch (error) {
        showToast(error.message, "error");
        return;
    }

    HeatLogState.mode = "furnace";
    HeatLogState.profilePiece = null;
    HeatLogState.treatmentDateIso = null;
    HeatLogState.lockedBatch = null;
    HeatLogState.batchPieces = [];
    HeatLogState.dispositions = [];
    HeatLogState.suggestedBatchCode = nextCode ? nextCode.code : null;

    openModal(
        "کوره‌های عملیات حرارتی",
        `<button type="button" class="btn btn-secondary" id="btn-heat-batch-archive">آرشیو محموله‌های کوره</button><div id="heat-log-modal-body">${renderFurnaceDashboardPanel()}</div>`,
        { size: "lg" }
    );

    attachHeatTreatmentLogPanelEvents(null);
    document.getElementById("btn-heat-batch-archive").onclick=()=>openHeatBatchArchive();
}

const HEAT_TREATMENT_DISPOSITION_STAGES = ["RE_OPERATION", "AGING"];

async function loadPieceDispositionOptions(pieceId) {
    const tests = await TensileTestAPI.getByPiece(pieceId);
    return tests
        .filter((t) => t.disposition && HEAT_TREATMENT_DISPOSITION_STAGES.includes(t.disposition.nextStage))
        .map((t) => ({
            id: t.disposition.id,
            testIndex: t.testIndex,
            nextStageLabel: t.disposition.nextStageLabel || t.disposition.nextStage,
            temperatureC: t.disposition.temperatureC,
            durationHours: t.disposition.durationHours,
        }));
}

function buildDispositionOptions(dispositions) {
    const counts = {};
    dispositions.forEach((d) => {
        counts[d.nextStageLabel] = (counts[d.nextStageLabel] || 0) + 1;
    });

    return dispositions
        .map((d) => {
            const label =
                counts[d.nextStageLabel] > 1
                    ? `${d.nextStageLabel} — تست ${formatHeatLogNumber(d.testIndex)}`
                    : d.nextStageLabel;
            return `<option value="${d.id}">${escapeHtml(label)}</option>`;
        })
        .join("");
}

function renderHeatTreatmentLogPanel(records, dispositions) {
    return `
        ${renderAddHeatTreatmentForm(dispositions)}
        <div class="heat-log-list card-tilt-wrap">
            ${renderHeatTreatmentRecords(records)}
        </div>
        <div class="form-actions">
            <button type="button" class="btn btn-secondary" id="btn-heat-log-back-to-profile">بازگشت به پروفایل</button>
        </div>
    `;
}

function renderFurnaceDashboardPanel() {
    return `
        ${renderAddHeatTreatmentForm([])}
        <div class="form-actions">
            <button type="button" class="btn btn-secondary" id="btn-heat-log-close">بستن</button>
        </div>
    `;
}

function renderAddHeatTreatmentForm(dispositions) {
    if (!canManageHeatTreatmentLog()) return "";

    const furnaceMode = HeatLogState.mode === "furnace";
    const dispositionOptions = buildDispositionOptions(dispositions);
    const furnaceOptions = Object.entries(FURNACE_SIZE_LABELS)
        .map(([key, label]) => `<option value="${key}">${label}</option>`)
        .join("");

    return `
        <form id="form-add-heat-treatment" class="heat-log-form">
            <div class="heat-log-form-title">${furnaceMode ? "محموله‌ی کوره" : "ثبت عملیات حرارتی جدید"}</div>

            <div class="heat-log-form-fields">
                <div class="form-group">
                    <label for="heat-log-batch-code">شناسه محموله کوره</label>
                    <input type="text" id="heat-log-batch-code" maxlength="50" list="heat-log-batch-list"
                        value="${escapeHtml(HeatLogState.suggestedBatchCode || "")}"
                        placeholder="مثلاً HT-1404-012" required />
                    <datalist id="heat-log-batch-list"></datalist>
                </div>
                <div class="form-group">
                    <label for="heat-log-date">تاریخ عملیات</label>
                    <input type="text" id="heat-log-date" placeholder="انتخاب تاریخ" />
                </div>
            </div>

            <div class="heat-log-cycle-note" id="heat-log-cycle-note" style="display: none;"></div>

            <div class="heat-log-form-fields heat-log-form-fields-3">
                <div class="form-group">
                    <label for="heat-log-temperature">دما (°C)</label>
                    <input type="number" step="any" id="heat-log-temperature" />
                </div>
                <div class="form-group">
                    <label for="heat-log-duration">زمان (ساعت)</label>
                    <input type="number" step="any" min="0" id="heat-log-duration" />
                </div>
                <div class="form-group">
                    <label for="heat-log-furnace-size">سایز کوره</label>
                    <select id="heat-log-furnace-size">
                        <option value="">— انتخاب کنید —</option>
                        ${furnaceOptions}
                    </select>
                </div>
            </div>

            <div class="form-actions heat-log-cycle-actions" id="heat-log-cycle-actions" style="display: none;">
                <button type="button" class="btn btn-secondary btn-sm" id="btn-heat-log-save-cycle">ذخیره پارامترهای سیکل</button>
            </div>

            <div class="heat-log-form-fields heat-log-form-fields-3">
                <div class="form-group">
                    <label for="heat-log-stage">مرحله</label>
                    <input type="text" id="heat-log-stage" maxlength="50" placeholder="مثلاً مرحله اول" required />
                </div>
                <div class="form-group">
                    <label for="heat-log-plate-label">شماره تست پلیت</label>
                    <input type="text" id="heat-log-plate-label" maxlength="20" placeholder="مثلاً C4" />
                </div>
                <div class="form-group">
                    <label for="heat-log-plate-hardness">سختی تست پلیت (HB)</label>
                    <input type="number" step="any" min="0" id="heat-log-plate-hardness" />
                </div>
            </div>

            <div class="form-group">
                <label for="heat-log-disposition">نوع عملیات</label>
                <select id="heat-log-disposition">
                    <option value="">عملیات عادی مسیر تولید</option>
                    ${dispositionOptions}
                </select>
            </div>

            <div class="form-group">
                <label for="heat-log-details">توضیحات (اختیاری)</label>
                <textarea id="heat-log-details" rows="2"></textarea>
            </div>

            ${
                furnaceMode
                    ? `
            <div class="heat-log-piece-block">
                <label for="heat-log-piece-ref">افزودن قطعه</label>
                <div class="heat-log-piece-add">
                    <input type="text" id="heat-log-piece-ref" placeholder="کدپروژه-ردیف-شماره، مثلاً 498-2-54" />
                    <button type="button" class="btn btn-primary btn-sm" id="btn-heat-log-add-piece" title="افزودن قطعه" aria-label="افزودن قطعه"><span aria-hidden="true">+</span></button>
                </div>
                <div class="heat-log-piece-hint" id="heat-log-piece-hint"></div>
                <ul class="heat-log-piece-list" id="heat-log-piece-list"></ul>
            </div>`
                    : ""
            }

            <div class="form-error" id="heat-log-form-error"></div>
            ${
                furnaceMode
                    ? ""
                    : `<div class="form-actions" style="margin-top: 8px;">
                <button type="submit" class="btn btn-primary btn-sm">ثبت</button>
            </div>`
            }
        </form>
    `;
}

function renderHeatTreatmentRecords(records) {
    if (!records.length) {
        return `<div class="history-empty">هنوز عملیات حرارتی‌ای برای این قطعه ثبت نشده است</div>`;
    }

    return records.map(renderHeatTreatmentCard).join("");
}

function renderHeatTreatmentCard(r) {
    const cycle = [`تاریخ: ${isoToJalaliString(r.treatmentDate) || "—"}`];
    if (r.temperatureC != null) cycle.push(`دما: ${formatHeatLogNumber(r.temperatureC)}°C`);
    if (r.durationHours != null) cycle.push(`زمان: ${formatHeatLogNumber(r.durationHours)} ساعت`);
    if (r.furnaceSizeLabel) cycle.push(`کوره: ${r.furnaceSizeLabel}`);
    if (r.batchPieceCount > 1) cycle.push(`${formatHeatLogNumber(r.batchPieceCount)} قطعه در محموله`);

    const plate = [];
    if (r.treatmentStage) plate.push(`مرحله: ${r.treatmentStage}`);
    if (r.testPlateLabel) plate.push(`تست پلیت ${r.testPlateLabel}`);
    if (r.testPlateHardnessHb != null) plate.push(`سختی ${formatHeatLogNumber(r.testPlateHardnessHb)} HB`);
    if (r.tensileTestIndex != null) {
        plate.push(`تجویزشده از تست کشش ${formatHeatLogNumber(r.tensileTestIndex)}`);
    }

    return `
        <div class="heat-log-card card-tilt">
            <div class="heat-log-card-header">
                <span class="heat-log-batch-code">${escapeHtml(r.batchCode)}</span>
                <span class="heat-log-meta">${escapeHtml(cycle.join(" · "))}</span>
            </div>
            ${plate.length ? `<div class="heat-log-plate">${escapeHtml(plate.join(" · "))}</div>` : ""}
            ${r.details ? `<div class="heat-log-details">${escapeHtml(r.details).replaceAll("\n", "<br>")}</div>` : ""}
            ${r.batchNotes ? `<div class="heat-log-batch-notes">یادداشت محموله: ${escapeHtml(r.batchNotes)}</div>` : ""}
            <div class="heat-log-card-footer">
                <div class="heat-log-card-meta">
                    ${r.createdBy ? `<span class="history-user">${escapeHtml(r.createdBy)}</span>` : ""}
                    <span class="history-time">${formatDateTime(r.createdAt)}</span>
                </div>
                ${
                    r.canDelete
                        ? `<button type="button" class="btn btn-danger btn-sm btn-delete-heat-log" data-id="${r.id}">حذف</button>`
                        : ""
                }
            </div>
        </div>`;
}

function renderHeatLogPieceList() {
    const list = document.getElementById("heat-log-piece-list");
    if (!list) return;

    const pieces = HeatLogState.batchPieces || [];
    if (!pieces.length) {
        list.innerHTML = `<li class="heat-log-piece-empty">هنوز قطعه‌ای به این محموله اضافه نشده است</li>`;
        return;
    }

    list.innerHTML = pieces
        .map((p) => {
            const ref = pieceRefLabel(p);
            return `
            <li class="heat-log-piece-item">
                <span class="heat-log-piece-ref">${escapeHtml(ref)}</span>
                <span class="heat-log-piece-name">${escapeHtml(p.pieceName || "")}</span>
                <button type="button" class="heat-log-piece-remove" data-id="${p.recordId}" title="حذف قطعه" aria-label="حذف قطعه">×</button>
            </li>`;
        })
        .join("");
}

function fillDispositionSelect(dispositions) {
    const select = document.getElementById("heat-log-disposition");
    if (!select) return;
    HeatLogState.dispositions = dispositions || [];
    select.innerHTML = `<option value="">عملیات عادی مسیر تولید</option>${buildDispositionOptions(HeatLogState.dispositions)}`;
}

/* ===========================
   رویدادها
   =========================== */

function attachHeatTreatmentLogPanelEvents(piece) {
    document.getElementById("btn-heat-log-back-to-profile")?.addEventListener("click", () => {
        openPieceProfile(piece.id);
    });
    document.getElementById("btn-heat-log-close")?.addEventListener("click", () => closeModal());

    document.querySelectorAll(".btn-delete-heat-log").forEach((btn) => {
        btn.addEventListener("click", async () => {
            if (!confirm("آیا از حذف این رکورد عملیات حرارتی مطمئن هستید؟")) return;
            try {
                await HeatTreatmentAPI.delete(btn.dataset.id);
                showToast("رکورد عملیات حرارتی حذف شد");
                await openHeatTreatmentLogModal(piece);
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });

    const form = document.getElementById("form-add-heat-treatment");
    if (!form) return;

    populateBatchSuggestions();
    renderHeatLogPieceList();

    attachPersianDatePicker(document.getElementById("heat-log-date"), {
        onSelect: ({ jy, jm, jd }) => {
            HeatLogState.treatmentDateIso = jalaliToIsoDateString(jy, jm, jd);
        },
        onClear: () => {
            HeatLogState.treatmentDateIso = null;
        },
    });

    document.getElementById("heat-log-batch-code").addEventListener("input", scheduleBatchLookup);
    document.getElementById("heat-log-disposition")?.addEventListener("change", applyDispositionPrefill);
    const saveCycle=document.getElementById("btn-heat-log-save-cycle");
    if(saveCycle){saveCycle.hidden=!hasPermission('EDIT_HEAT_TREATMENT_BATCH');saveCycle.addEventListener('click',saveHeatLogCycleParams);}

    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        if (HeatLogState.mode === "furnace") return;
        await submitHeatTreatmentRecord(piece);
    });

    const pieceRefInput = document.getElementById("heat-log-piece-ref");
    if (pieceRefInput) {
        pieceRefInput.addEventListener("input", schedulePieceRefLookup);
        pieceRefInput.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                addPieceToCurrentBatch();
            }
        });
    }
    document.getElementById("btn-heat-log-add-piece")?.addEventListener("click", addPieceToCurrentBatch);
    document.getElementById("heat-log-piece-list")?.addEventListener("click", onHeatLogPieceListClick);

    if (HeatLogState.suggestedBatchCode) {
        scheduleBatchLookup();
    }
}

function scheduleBatchLookup() {
    clearTimeout(HeatLogState.batchLookupTimer);
    HeatLogState.batchLookupTimer = setTimeout(syncLoadedBatch, 300);
}

function schedulePieceRefLookup() {
    clearTimeout(HeatLogState.pieceLookupTimer);
    HeatLogState.pieceLookupTimer = setTimeout(syncPieceRefDispositions, 350);
}

async function syncLoadedBatch() {
    const input = document.getElementById("heat-log-batch-code");
    if (!input) return;

    const code = input.value.trim();
    let batch = null;
    if (code) {
        try {
            batch = await HeatTreatmentAPI.findBatchByCode(code);
        } catch (_) {
            batch = null;
        }
    }

    if (!document.body.contains(input) || input.value.trim() !== code) return;
    applyLoadedBatch(batch);
}

function applyLoadedBatch(batch) {
    HeatLogState.lockedBatch = batch;
    HeatLogState.batchPieces = batch && Array.isArray(batch.pieces) ? batch.pieces : [];

    const dateInput = document.getElementById("heat-log-date");
    const temperature = document.getElementById("heat-log-temperature");
    const duration = document.getElementById("heat-log-duration");
    const furnaceSize = document.getElementById("heat-log-furnace-size");
    const note = document.getElementById("heat-log-cycle-note");
    const cycleActions = document.getElementById("heat-log-cycle-actions");

    if (batch) {
        dateInput.value = isoToJalaliString(batch.treatmentDate);
        HeatLogState.treatmentDateIso = batch.treatmentDate;
        temperature.value = batch.temperatureC ?? "";
        duration.value = batch.durationHours ?? "";
        furnaceSize.value = batch.furnaceSize || "";
        const count = batch.pieceCount ?? HeatLogState.batchPieces.length;
        note.textContent = `این محموله قبلاً با ${formatHeatLogNumber(count)} قطعه ثبت شده؛ می‌توانید پارامترها را ویرایش کنید.`;
        note.style.display = "block";
        if (cycleActions) cycleActions.style.display = "";
    } else {
        note.style.display = "none";
        note.textContent = "";
        if (cycleActions) cycleActions.style.display = "none";
        HeatLogState.batchPieces = [];
    }

    [dateInput, temperature, duration, furnaceSize].forEach((el) => {
        if (el) el.disabled = false;
    });
    renderHeatLogPieceList();
}

function applyDispositionPrefill() {
    const select = document.getElementById("heat-log-disposition");
    const disposition = HeatLogState.dispositions.find((d) => d.id === select.value);
    if (!disposition) return;

    if (HeatLogState.lockedBatch) {
        warnOnCycleMismatch(disposition);
        return;
    }

    if (disposition.temperatureC != null) {
        document.getElementById("heat-log-temperature").value = disposition.temperatureC;
    }
    if (disposition.durationHours != null) {
        document.getElementById("heat-log-duration").value = disposition.durationHours;
    }
}

function warnOnCycleMismatch(disposition) {
    const batch = HeatLogState.lockedBatch;
    const differs = (a, b) => a != null && b != null && Number(a) !== Number(b);

    if (differs(disposition.temperatureC, batch.temperatureC) || differs(disposition.durationHours, batch.durationHours)) {
        showToast("توجه: تجویز این تست با سیکل محموله‌ی انتخاب‌شده یکسان نیست", "error");
    }
}

function populateBatchSuggestions() {
    const list = document.getElementById("heat-log-batch-list");
    if (!list) return;

    list.innerHTML = HeatLogState.todayBatches
        .map(
            (b) =>
                `<option value="${escapeHtml(b.batchCode)}">${isoToJalaliString(b.treatmentDate)} — ${formatHeatLogNumber(b.pieceCount)} قطعه</option>`
        )
        .join("");
}

function collectHeatLogNumeric(id) {
    const raw = document.getElementById(id).value.trim();
    if (raw === "") return null;
    const num = Number(raw);
    return Number.isNaN(num) ? undefined : num;
}

function collectHeatLogPayload() {
    const errorBox = document.getElementById("heat-log-form-error");
    const showError = (message) => {
        if (errorBox) {
            errorBox.textContent = message;
            errorBox.style.display = "block";
        } else {
            showToast(message, "error");
        }
        return null;
    };
    if (errorBox) {
        errorBox.textContent = "";
        errorBox.style.display = "none";
    }

    const batchCode = document.getElementById("heat-log-batch-code").value.trim();
    if (!batchCode) return showError("شناسه محموله را وارد کنید");
    if (!HeatLogState.treatmentDateIso) return showError("تاریخ عملیات را انتخاب کنید");

    const treatmentStage = document.getElementById("heat-log-stage").value.trim();
    if (!treatmentStage) return showError("مرحله را وارد کنید");

    const temperatureC = collectHeatLogNumeric("heat-log-temperature");
    const durationHours = collectHeatLogNumeric("heat-log-duration");
    const testPlateHardnessHb = collectHeatLogNumeric("heat-log-plate-hardness");
    if ([temperatureC, durationHours, testPlateHardnessHb].includes(undefined)) {
        return showError("مقادیر عددی وارد‌شده معتبر نیستند");
    }

    return {
        batchCode,
        treatmentDate: HeatLogState.treatmentDateIso,
        treatmentStage,
        temperatureC,
        durationHours,
        furnaceSize: document.getElementById("heat-log-furnace-size").value || null,
        testPlateLabel: document.getElementById("heat-log-plate-label").value.trim() || null,
        testPlateHardnessHb,
        tensileDispositionId: document.getElementById("heat-log-disposition").value || null,
        details: document.getElementById("heat-log-details").value.trim() || null,
    };
}

async function submitHeatTreatmentRecord(piece) {
    const payload = collectHeatLogPayload();
    if (!payload) return;
    const errorBox = document.getElementById("heat-log-form-error");
    try {
        await HeatTreatmentAPI.create(piece.id, payload);
        showToast("عملیات حرارتی ثبت شد");
        await openHeatTreatmentLogModal(piece);
    } catch (error) {
        if (errorBox) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        } else {
            showToast(error.message, "error");
        }
    }
}

async function saveHeatLogCycleParams() {
    if(!hasPermission('EDIT_HEAT_TREATMENT_BATCH')) {showToast('مجوز اصلاح سیکل لازم است','error');return;}
    const batch = HeatLogState.lockedBatch;
    if (!batch) return;
    if (!HeatLogState.treatmentDateIso) {
        showToast("تاریخ عملیات را انتخاب کنید", "error");
        return;
    }
    const temperatureC = collectHeatLogNumeric("heat-log-temperature");
    const durationHours = collectHeatLogNumeric("heat-log-duration");
    if ([temperatureC, durationHours].includes(undefined)) {
        showToast("مقادیر عددی وارد‌شده معتبر نیستند", "error");
        return;
    }
    try {
        await previewHeatBatchChange(batch.id, {
            treatmentDate: HeatLogState.treatmentDateIso,
            temperatureC,
            durationHours,
            furnaceSize: document.getElementById("heat-log-furnace-size").value || null,
            notes: batch.notes || null,
        }, batch.version);
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function syncPieceRefDispositions() {
    const input = document.getElementById("heat-log-piece-ref");
    const hint = document.getElementById("heat-log-piece-hint");
    if (!input) return;
    const code = input.value.trim();
    if (!code) {
        fillDispositionSelect([]);
        if (hint) hint.textContent = "";
        return;
    }
    try {
        const piece = await PieceAPI.getByRef(code);
        if (!document.body.contains(input) || input.value.trim() !== code) return;
        const dispositions = await loadPieceDispositionOptions(piece.id);
        fillDispositionSelect(dispositions);
        if (hint) {
            hint.textContent = `${pieceRefLabel(piece)} — ${piece.pieceName || ""}`;
        }
    } catch (error) {
        if (!document.body.contains(input) || input.value.trim() !== code) return;
        fillDispositionSelect([]);
        if (hint) hint.textContent = error.message;
    }
}

async function addPieceToCurrentBatch() {
    const input = document.getElementById("heat-log-piece-ref");
    const code = input ? input.value.trim() : "";
    if (!code) {
        showToast("کد قطعه را به شکل کدپروژه-ردیف-شماره وارد کنید", "error");
        return;
    }
    const payload = collectHeatLogPayload();
    if (!payload) return;
    try {
        const piece = await PieceAPI.getByRef(code);
        await HeatTreatmentAPI.create(piece.id, payload);
        showToast("قطعه به محموله اضافه شد");
        if (input) input.value = "";
        const hint = document.getElementById("heat-log-piece-hint");
        if (hint) hint.textContent = "";
        fillDispositionSelect([]);
        const batch = await HeatTreatmentAPI.findBatchByCode(payload.batchCode);
        applyLoadedBatch(batch);
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function onHeatLogPieceListClick(e) {
    const btn = e.target.closest(".heat-log-piece-remove");
    if (!btn) return;
    if (!confirm("این قطعه از محموله حذف شود؟")) return;
    try {
        await HeatTreatmentAPI.delete(btn.dataset.id);
        showToast("قطعه از محموله حذف شد");
        const code = document.getElementById("heat-log-batch-code")?.value.trim();
        const batch = code ? await HeatTreatmentAPI.findBatchByCode(code) : null;
        applyLoadedBatch(batch);
    } catch (error) {
        showToast(error.message, "error");
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const furnaceBtn = document.getElementById("btn-dashboard-furnaces");
    if (furnaceBtn) {
        furnaceBtn.addEventListener("click", () => openFurnaceDashboardModal());
    }
});

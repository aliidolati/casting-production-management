/**
 * tensile.js
 * منطق «تست کشش»: آیکن دسترسی در پروفایل قطعه، پاپ‌آپ نتایج تست‌ها،
 * ثبت تست جدید توسط اپراتور (هر تست شامل دو نمونه) و فرم تعیین‌تکلیف مسئول کشش.
 *
 * تست‌هایی که همه‌ی حداقل پارامترهای تاییدی را برآورده کنند، سمت سرور به‌صورت
 * خودکار تایید می‌شوند و اصلاً وارد صف تعیین‌تکلیف نمی‌شوند.
 */

const TENSILE_STATUS_LABELS = {
    PENDING_REVIEW: "در انتظار تعیین‌تکلیف",
    APPROVED: "تایید شده",
    REJECTED: "مردود",
    RETEST_REQUIRED: "نیازمند تکرار تست",
};

const TENSILE_NEXT_STAGE_LABELS = {
    APPROVED: "تایید",
    RE_OPERATION: "عملیات مجدد",
    CONCESSION_APPROVAL: "تایید ارفاقی",
    AGING: "پیرسازی",
    NEXT_TENSILE: "کشش بعدی",
    SCRAP: "اسقاط",
    OTHER: "سایر",
};

const TENSILE_INSPECTION_RESULT_LABELS = {
    ACCEPTED: "قبول",
    REJECTED: "مردود",
};

/** تعداد نمونه‌هایی که فرم ثبت می‌گیرد. ساختار دیتابیس تعداد متغیر را پشتیبانی می‌کند. */
const TENSILE_SAMPLES_PER_TEST = 2;

const TensileState = {
    dispositionDateIso: null,
    sampleLabels: null,
    visibleTests: [],
    editingTestId: null,
};

function resetTensileState() {
    TensileState.dispositionDateIso = null;
    TensileState.editingTestId = null;
    TensileState.visibleTests = [];
}

function canRecordTensileTest() {
    return hasPermission("MANAGE_PIECE_ASSIGNMENT");
}

function canReviewTensileTest() {
    return hasPermission("REVIEW_TENSILE_TEST");
}

function formatTensileNumber(value) {
    if (value === null || value === undefined || value === "") return "—";
    const num = Number(value);
    if (Number.isNaN(num)) return "—";
    return num.toLocaleString("fa-IR", { maximumFractionDigits: 2 });
}

/** مقدار خالی مجاز است؛ رشته‌ی نامعتبر به‌جای صفر باید undefined برگرداند تا جلوی ارسال گرفته شود. */
function parseOptionalNumber(rawValue) {
    const trimmed = (rawValue || "").trim();
    if (trimmed === "") return null;
    const num = Number(trimmed);
    return Number.isNaN(num) ? undefined : num;
}

/* ===========================
   آیکن تست کشش در پروفایل قطعه
   =========================== */

function renderTensileIcon(tests) {
    const pendingCount = tests.filter((t) => t.status === "PENDING_REVIEW").length;
    const badge = tests.length
        ? `<span class="tensile-icon-badge${pendingCount ? " tensile-icon-badge-warning" : ""}">${tests.length}</span>`
        : "";

    const label = pendingCount ? `تست کشش — ${pendingCount} تست در انتظار تعیین‌تکلیف` : "تست کشش";
    return `
        <button type="button"
            class="profile-tensile-icon${pendingCount ? " has-pending" : ""}"
            id="btn-tensile-icon"
            title="${label}"
            aria-label="${label}">
            <span class="tensile-icon-symbol"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 2v7.3a2 2 0 0 1-.2.9L4.7 20.6a1 1 0 0 0 .9 1.4h12.8a1 1 0 0 0 .9-1.4l-5.1-10.4a2 2 0 0 1-.2-.9V2"/><path d="M8.5 2h7"/><path d="M7 16h10"/></svg></span>
            ${badge}
        </button>
    `;
}

function attachTensileIconEvents(piece) {
    const btn = document.getElementById("btn-tensile-icon");
    if (!btn) return;

    btn.addEventListener("click", (e) => {
        e.stopPropagation();
        openTensileModal(piece);
    });
}

/* ===========================
   پاپ‌آپ نتایج تست کشش
   =========================== */

async function ensureTensileSampleLabels() {
    if (TensileState.sampleLabels) return;
    TensileState.sampleLabels = await TensileTestAPI.getSampleLabels();
}

async function openTensileModal(piece) {
    let tests;
    try {
        [tests] = await Promise.all([
            TensileTestAPI.getByPiece(piece.id),
            ensureTensileSampleLabels(),
        ]);
    } catch (error) {
        showToast(error.message, "error");
        return;
    }

    openModal(
        `تست کشش — قطعه ${formatTensileNumber(piece.pieceNumber)}`,
        `<div id="tensile-modal-body">${renderTensilePanel(piece, tests)}</div>`,
        { size: "lg" }
    );

    attachTensilePanelEvents(piece);
}

function renderTensilePanel(piece, tests) {
    TensileState.visibleTests = tests || [];
    return `
        ${renderTensileThresholdHint(piece)}
        ${renderAddTensileTestForm(piece)}
        <div class="tensile-list">
            ${renderTensileTestList(tests)}
        </div>
        <div class="form-actions">
            <button type="button" class="btn btn-secondary" id="btn-tensile-back-to-profile">بازگشت به پروفایل</button>
        </div>
    `;
}

/** بازخوانی محتوای پاپ‌آپ بدون بستن و باز کردن دوباره‌ی آن. */
async function refreshTensilePanel(piece) {
    try {
        const [tests] = await Promise.all([
            TensileTestAPI.getByPiece(piece.id),
            ensureTensileSampleLabels(),
        ]);
        const container = document.getElementById("tensile-modal-body");
        if (!container) return;

        container.innerHTML = renderTensilePanel(piece, tests);
        attachTensilePanelEvents(piece);
    } catch (error) {
        showToast(error.message, "error");
    }
}

function renderTensileThresholdHint(piece) {
    const parts = [];
    if (piece.minUts != null) parts.push(`UTS ≥ ${formatTensileNumber(piece.minUts)}`);
    if (piece.minYs != null) parts.push(`YS ≥ ${formatTensileNumber(piece.minYs)}`);
    if (piece.minEl != null) parts.push(`EL ≥ ${formatTensileNumber(piece.minEl)}`);
    if (piece.minHb != null) parts.push(`HB ≥ ${formatTensileNumber(piece.minHb)}`);

    if (!parts.length) {
        return `<div class="tensile-threshold-hint tensile-threshold-missing">
            برای این تعریف قطعه هنوز حداقل پارامتر تاییدی ثبت نشده؛ تایید خودکار انجام نمی‌شود
            و همه‌ی تست‌ها باید دستی تعیین‌تکلیف شوند.
        </div>`;
    }

    return `<div class="tensile-threshold-hint">حداقل مجاز: ${parts.join(" · ")}</div>`;
}

function renderAddTensileTestForm(piece) {
    if (!canRecordTensileTest()) return "";

    const editing = (TensileState.visibleTests || []).find((t) => t.id === TensileState.editingTestId) || null;
    if (!editing && piece.currentStage !== "TENSILE") {
        return `<div class="tensile-form-locked">
            ثبت تست کشش فقط وقتی ممکن است که قطعه در مرحله‌ی «تست کشش» باشد.
        </div>`;
    }

    const sampleRows = Array.from({ length: TENSILE_SAMPLES_PER_TEST }, (_, i) => {
        const n = i + 1;
        const sample = editing?.samples?.[i] || {};
        const currentLabel = sample.sampleLabel || "";
        const labels = [...(TensileState.sampleLabels || [])];
        if (currentLabel && !labels.includes(currentLabel)) labels.push(currentLabel);
        const labelOptions = labels
            .map((label) => `<option value="${escapeHtml(label)}"${label === currentLabel ? " selected" : ""}>${escapeHtml(label)}</option>`)
            .join("");
        return `
        <div class="tensile-sample-row">
            <div class="tensile-sample-label">نمونه ${n}</div>
            <div class="tensile-sample-fields">
                <div class="form-group tensile-field">
                    <label for="tensile-${n}-label">شماره نمونه</label>
                    <select id="tensile-${n}-label">
                        <option value="">انتخاب کنید</option>
                        ${labelOptions}
                    </select>
                </div>
                <div class="form-group tensile-field">
                    <label for="tensile-${n}-uts">UTS</label>
                    <input type="number" step="any" min="0" id="tensile-${n}-uts" value="${sample.uts ?? ""}" />
                </div>
                <div class="form-group tensile-field">
                    <label for="tensile-${n}-ys">YS</label>
                    <input type="number" step="any" min="0" id="tensile-${n}-ys" value="${sample.ys ?? ""}" />
                </div>
                <div class="form-group tensile-field">
                    <label for="tensile-${n}-el">EL</label>
                    <input type="number" step="any" min="0" id="tensile-${n}-el" value="${sample.el ?? ""}" />
                </div>
                <div class="form-group tensile-field">
                    <label for="tensile-${n}-hb">HB</label>
                    <input type="number" step="any" min="0" id="tensile-${n}-hb" value="${sample.hb ?? ""}" />
                </div>
                <div class="form-group tensile-field tensile-sample-description">
                    <label for="tensile-${n}-description">توضیحات</label>
                    <input type="text" id="tensile-${n}-description" maxlength="500" value="${escapeHtml(sample.description || "")}" />
                </div>
            </div>
        </div>`;
    }).join("");

    const title = editing
        ? `ویرایش تست ${formatTensileNumber(editing.testIndex)}`
        : "ثبت تست کشش جدید";
    const submitLabel = editing ? "ذخیره تغییرات" : "ثبت تست";
    const cancel = editing
        ? `<button type="button" class="btn btn-secondary btn-sm" id="btn-tensile-cancel-edit">انصراف</button>`
        : "";

    return `
        <form id="form-add-tensile-test" class="tensile-form">
            <div class="tensile-form-title">${title}</div>
            ${sampleRows}
            <p class="form-hint">${editing
                ? "ویرایش مقادیر، تعیین‌تکلیف ثبت‌شده را تغییر نمی‌دهد."
                : "اگر همه‌ی مقادیر بالای حد مجاز باشند، تست بلافاصله توسط سیستم تایید می‌شود."}</p>
            <div class="form-error" id="tensile-form-error"></div>
            <div class="form-actions" style="margin-top: 8px;">
                ${cancel}
                <button type="submit" class="btn btn-primary btn-sm">${submitLabel}</button>
            </div>
        </form>
    `;
}

function renderTensileTestList(tests) {
    if (!tests.length) {
        return `<div class="history-empty">هنوز تست کششی برای این قطعه ثبت نشده است</div>`;
    }
    return tests.map(renderTensileTestCard).join("");
}

function renderTensileTestCard(test) {
    const statusLabel = test.statusLabel || TENSILE_STATUS_LABELS[test.status] || test.status;
    const warning = test.belowThreshold
        ? `<span class="tensile-badge tensile-badge-danger">زیر حد مجاز: ${escapeHtml(
              (test.thresholdFailures || []).join("، ")
          )}</span>`
        : "";
    const autoBadge = test.disposition && test.disposition.systemGenerated
        ? `<span class="tensile-badge tensile-badge-auto">تایید خودکار</span>`
        : "";

    const actions = [];
    if (canRecordTensileTest()) {
        actions.push(
            `<button type="button" class="btn btn-secondary btn-sm btn-tensile-edit" data-id="${test.id}">ویرایش</button>`
        );
    }
    if (canReviewTensileTest()) {
        actions.push(
            `<button type="button" class="btn btn-primary btn-sm btn-tensile-disposition" data-id="${test.id}">
                ${test.disposition ? "ویرایش تعیین‌تکلیف" : "تعیین‌تکلیف"}
            </button>`
        );
    }
    if (canRecordTensileTest() && test.canDelete) {
        actions.push(
            `<button type="button" class="btn btn-danger btn-sm btn-tensile-delete" data-id="${test.id}">حذف</button>`
        );
    }

    return `
        <div class="tensile-card tensile-card-${test.status}">
            <div class="tensile-card-header">
                <span class="tensile-card-title">تست ${formatTensileNumber(test.testIndex)}</span>
                <span class="tensile-badge tensile-badge-${test.status}">${escapeHtml(statusLabel)}</span>
                ${autoBadge}
                ${warning}
            </div>

            ${renderTensileSamplesTable(test)}

            <div class="tensile-card-meta">
                ${test.createdBy ? `<span class="history-user">${escapeHtml(test.createdBy)}</span>` : ""}
                <span class="history-time">${formatDateTime(test.createdAt)}</span>
            </div>

            ${renderTensileDispositionSummary(test)}

            ${actions.length ? `<div class="tensile-card-actions">${actions.join("")}</div>` : ""}
        </div>
    `;
}

function renderTensileSamplesTable(test) {
    const cell = (value, minimum) => {
        const below = value != null && minimum != null && Number(value) < Number(minimum);
        return `<td class="${below ? "tensile-cell-below" : ""}">${formatTensileNumber(value)}</td>`;
    };

    const rows = test.samples
        .map(
            (s) => `
        <tr>
            <td>${escapeHtml(s.sampleLabel || `نمونه ${s.sampleIndex}`)}</td>
            ${cell(s.uts, test.minUts)}
            ${cell(s.ys, test.minYs)}
            ${cell(s.el, test.minEl)}
            ${cell(s.hb, test.minHb)}
            <td class="tensile-sample-note">${escapeHtml(s.description || "—")}</td>
        </tr>`
        )
        .join("");

    return `
        <table class="tensile-samples-table">
            <thead>
                <tr><th>نمونه</th><th>UTS</th><th>YS</th><th>EL</th><th>HB</th><th>توضیحات</th></tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
    `;
}

function renderTensileDispositionSummary(test) {
    const d = test.disposition;
    if (!d) {
        return `<div class="tensile-disposition-empty">هنوز تعیین‌تکلیف نشده است</div>`;
    }

    const responsible = d.systemGenerated ? "خودکار (سیستم)" : d.responsibleUserName || "—";
    const parts = [
        `تاریخ: ${isoToJalaliString(d.dispositionDate) || "—"}`,
        `نتیجه بازرسی: ${d.inspectionResultLabel || TENSILE_INSPECTION_RESULT_LABELS[d.inspectionResult] || d.inspectionResult}`,
        `مرحله بعدی: ${d.nextStageLabel || TENSILE_NEXT_STAGE_LABELS[d.nextStage] || d.nextStage}`,
    ];
    if (d.quantity != null) parts.push(`تعداد: ${formatTensileNumber(d.quantity)}`);
    if (d.durationHours != null) parts.push(`زمان: ${formatTensileNumber(d.durationHours)} ساعت`);
    if (d.temperatureC != null) parts.push(`دما: ${formatTensileNumber(d.temperatureC)}°C`);
    parts.push(`مسئول: ${responsible}`);

    return `
        <div class="tensile-disposition-summary">
            <div class="tensile-disposition-line">${escapeHtml(parts.join(" · "))}</div>
            ${d.notes ? `<div class="tensile-disposition-notes">${escapeHtml(d.notes)}</div>` : ""}
        </div>
    `;
}

/* ===========================
   رویدادهای پاپ‌آپ
   =========================== */

function attachTensilePanelEvents(piece) {
    const form = document.getElementById("form-add-tensile-test");
    if (form) {
        form.addEventListener("submit", async (e) => {
            e.preventDefault();
            await submitNewTensileTest(piece);
        });
    }

    document.getElementById("btn-tensile-back-to-profile")?.addEventListener("click", () => {
        openPieceProfile(piece.id);
    });

    document.getElementById("btn-tensile-cancel-edit")?.addEventListener("click", () => {
        TensileState.editingTestId = null;
        const container = document.getElementById("tensile-modal-body");
        if (!container) return;
        container.innerHTML = renderTensilePanel(piece, TensileState.visibleTests);
        attachTensilePanelEvents(piece);
    });

    document.querySelectorAll(".btn-tensile-edit").forEach((btn) => {
        btn.addEventListener("click", () => {
            TensileState.editingTestId = btn.dataset.id;
            const container = document.getElementById("tensile-modal-body");
            if (!container) return;
            container.innerHTML = renderTensilePanel(piece, TensileState.visibleTests);
            attachTensilePanelEvents(piece);
            document.getElementById("form-add-tensile-test")?.scrollIntoView({ block: "nearest" });
        });
    });

    document.querySelectorAll(".btn-tensile-disposition").forEach((btn) => {
        btn.addEventListener("click", () => openTensileDispositionModal(piece, btn.dataset.id));
    });

    document.querySelectorAll(".btn-tensile-delete").forEach((btn) => {
        btn.addEventListener("click", async () => {
            if (!confirm("آیا از حذف این تست کشش مطمئن هستید؟")) return;
            try {
                await TensileTestAPI.delete(btn.dataset.id);
                showToast("تست کشش حذف شد");
                await refreshTensilePanel(piece);
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });
}

async function submitNewTensileTest(piece) {
    const errorBox = document.getElementById("tensile-form-error");
    const samples = [];

    for (let n = 1; n <= TENSILE_SAMPLES_PER_TEST; n++) {
        const values = {
            sampleLabel: document.getElementById(`tensile-${n}-label`).value.trim(),
            uts: parseOptionalNumber(document.getElementById(`tensile-${n}-uts`).value),
            ys: parseOptionalNumber(document.getElementById(`tensile-${n}-ys`).value),
            el: parseOptionalNumber(document.getElementById(`tensile-${n}-el`).value),
            hb: parseOptionalNumber(document.getElementById(`tensile-${n}-hb`).value),
            description: document.getElementById(`tensile-${n}-description`).value.trim(),
        };

        if ([values.uts, values.ys, values.el, values.hb].includes(undefined)) {
            errorBox.textContent = `مقادیر عددی نمونه ${n} معتبر نیستند`;
            errorBox.style.display = "block";
            return;
        }

        const hasAnyValue =
            values.sampleLabel !== "" ||
            values.uts !== null ||
            values.ys !== null ||
            values.el !== null ||
            values.hb !== null ||
            values.description !== "";

        if (!hasAnyValue) {
            errorBox.textContent = `اطلاعات نمونه ${n} وارد نشده است`;
            errorBox.style.display = "block";
            return;
        }

        samples.push(values);
    }

    try {
        const editingId = TensileState.editingTestId;
        const created = editingId
            ? await TensileTestAPI.update(editingId, { samples })
            : await TensileTestAPI.create(piece.id, { samples });
        TensileState.editingTestId = null;
        showToast(
            editingId
                ? "تست کشش ویرایش شد"
                : created.disposition && created.disposition.systemGenerated
                ? "تست کشش ثبت و به‌صورت خودکار تایید شد؛ قطعه به شات‌بلاست منتقل شد"
                : "تست کشش ثبت شد"
        );
        if (typeof loadTracking === "function") loadTracking();
        if (typeof refreshNotificationCenter === "function") refreshNotificationCenter();
        const updatedPiece = await PieceAPI.getById(piece.id);
        await refreshTensilePanel(updatedPiece);
    } catch (error) {
        errorBox.textContent = error.message;
        errorBox.style.display = "block";
    }
}

/* ===========================
   مودال تعیین‌تکلیف
   =========================== */

async function openTensileDispositionModal(piece, tensileTestId) {
    let test;
    try {
        const tests = await TensileTestAPI.getByPiece(piece.id);
        test = tests.find((t) => t.id === tensileTestId);
    } catch (error) {
        showToast(error.message, "error");
        return;
    }

    if (!test) {
        showToast("تست کشش پیدا نشد", "error");
        return;
    }

    const d = test.disposition;
    TensileState.dispositionDateIso = d ? d.dispositionDate : null;

    const nextStageOptions = Object.entries(TENSILE_NEXT_STAGE_LABELS)
        .map(([key, label]) => `<option value="${key}" ${d && d.nextStage === key ? "selected" : ""}>${label}</option>`)
        .join("");

    const inspectionOptions = Object.entries(TENSILE_INSPECTION_RESULT_LABELS)
        .map(
            ([key, label]) =>
                `<option value="${key}" ${d && d.inspectionResult === key ? "selected" : ""}>${label}</option>`
        )
        .join("");

    openModal(
        `تعیین‌تکلیف تست ${formatTensileNumber(test.testIndex)} — قطعه ${formatTensileNumber(piece.pieceNumber)}`,
        `
        <div class="tensile-disposition-context">
            ${renderTensileSamplesTable(test)}
            ${
                test.belowThreshold
                    ? `<div class="tensile-threshold-hint tensile-threshold-missing">
                        این تست زیر حد مجاز است: ${escapeHtml((test.thresholdFailures || []).join("، "))}
                       </div>`
                    : ""
            }
            ${
                d && d.systemGenerated
                    ? `<div class="tensile-threshold-hint">این تست را سیستم به‌صورت خودکار تایید کرده؛
                        ذخیره‌ی این فرم آن را به تعیین‌تکلیف دستی به نام شما تبدیل می‌کند.</div>`
                    : ""
            }
        </div>

        <form id="form-tensile-disposition" class="tensile-disposition-form">
            <div class="form-group">
                <label for="disposition-date">تاریخ</label>
                <input type="text" id="disposition-date" placeholder="انتخاب تاریخ"
                    value="${d ? escapeHtml(isoToJalaliString(d.dispositionDate)) : ""}" />
            </div>
            <div class="tensile-disposition-fields">
                <div class="form-group">
                    <label for="disposition-quantity">تعداد</label>
                    <input type="number" min="0" id="disposition-quantity" value="${d && d.quantity != null ? d.quantity : ""}" />
                </div>
                <div class="form-group">
                    <label for="disposition-duration">زمان (ساعت)</label>
                    <input type="number" step="any" min="0" id="disposition-duration" value="${d && d.durationHours != null ? d.durationHours : ""}" />
                </div>
                <div class="form-group">
                    <label for="disposition-temperature">دما (°C)</label>
                    <input type="number" step="any" id="disposition-temperature" value="${d && d.temperatureC != null ? d.temperatureC : ""}" />
                </div>
            </div>
            <div class="tensile-disposition-fields">
                <div class="form-group">
                    <label for="disposition-next-stage">مرحله بعدی</label>
                    <select id="disposition-next-stage">${nextStageOptions}</select>
                </div>
                <div class="form-group">
                    <label for="disposition-inspection">نتیجه بازرسی</label>
                    <select id="disposition-inspection">${inspectionOptions}</select>
                </div>
            </div>
            <div class="form-group">
                <label for="disposition-notes">توضیحات (اختیاری)</label>
                <textarea id="disposition-notes" rows="2">${d && d.notes ? escapeHtml(d.notes) : ""}</textarea>
            </div>
            <p class="form-hint">وضعیت نهایی تست از روی «نتیجه بازرسی» تعیین می‌شود. مرحله بعدی قطعه را جابه‌جا می‌کند: تایید/تایید ارفاقی → شات‌بلاست، اسقاط → اسقاط، عملیات مجدد → منتظر عملیات، پیرسازی → مرحله سوم/چهارم/پنجم، کشش مجدد → ماندن در تست کشش.</p>
            <div class="form-error" id="disposition-form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-disposition">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
    `,
        { size: "lg" }
    );

    const dateInput = document.getElementById("disposition-date");
    attachPersianDatePicker(dateInput, {
        onSelect: ({ jy, jm, jd }) => {
            TensileState.dispositionDateIso = jalaliToIsoDateString(jy, jm, jd);
        },
        onClear: () => {
            TensileState.dispositionDateIso = null;
        },
    });

    document.getElementById("btn-cancel-disposition").addEventListener("click", () => {
        openTensileModal(piece);
    });

    document.getElementById("form-tensile-disposition").addEventListener("submit", async (e) => {
        e.preventDefault();
        const errorBox = document.getElementById("disposition-form-error");

        if (!TensileState.dispositionDateIso) {
            errorBox.textContent = "انتخاب تاریخ الزامی است";
            errorBox.style.display = "block";
            return;
        }

        const quantity = parseOptionalNumber(document.getElementById("disposition-quantity").value);
        const durationHours = parseOptionalNumber(document.getElementById("disposition-duration").value);
        const temperatureC = parseOptionalNumber(document.getElementById("disposition-temperature").value);

        if ([quantity, durationHours, temperatureC].includes(undefined)) {
            errorBox.textContent = "مقادیر عددی وارد‌شده معتبر نیستند";
            errorBox.style.display = "block";
            return;
        }

        try {
            const nextStage = document.getElementById("disposition-next-stage").value;
            await TensileTestAPI.saveDisposition(test.id, {
                dispositionDate: TensileState.dispositionDateIso,
                quantity,
                durationHours,
                temperatureC,
                nextStage,
                inspectionResult: document.getElementById("disposition-inspection").value,
                notes: document.getElementById("disposition-notes").value.trim() || null,
            });
            const moveHint = {
                APPROVED: "؛ قطعه به شات‌بلاست منتقل شد",
                CONCESSION_APPROVAL: "؛ قطعه به شات‌بلاست منتقل شد",
                SCRAP: "؛ قطعه اسقاط شد",
                RE_OPERATION: "؛ قطعه به منتظر عملیات منتقل شد",
                AGING: "؛ قطعه به پیرسازی (عملیات حرارتی) منتقل شد",
            }[nextStage] || "";
            showToast("تعیین‌تکلیف ثبت شد" + moveHint);
            if (typeof loadTracking === "function") loadTracking();
            const updatedPiece = await PieceAPI.getById(piece.id);
            await openTensileModal(updatedPiece);
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
}

/** ورود مستقیم از ویجت داشبورد به پاپ‌آپ کشش، بدون عبور از پروفایل قطعه. */
async function openTensileModalByPieceId(pieceId) {
    try {
        const piece = await PieceAPI.getById(pieceId);
        await openTensileModal(piece);
    } catch (error) {
        showToast(error.message, "error");
    }
}

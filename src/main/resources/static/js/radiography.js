/**
 * radiography.js
 * منطق مربوط به «عیوب رادیوگرافی» هر قطعه: نمایش لیست عیوب داخل پروفایل قطعه،
 * افزودن عیب جدید (کاری که اپراتور روی خط تولید انجام می‌دهد)، و حذف عیب.
 */

const DEFECT_TYPE_STANDARD = ["F.M.M", "F.M.L", "F.M", "S.P", "S.C", "CR", "G.H", "G.P", "VT", "C.S", "M.R", "ماسه ریزی", "ابعادی"];
const DEFECT_TYPE_OTHER = "OTHER";
const DEFECT_TYPE_OPTIONS = [...DEFECT_TYPE_STANDARD, DEFECT_TYPE_OTHER];

/** انواع عیب قابل نمایش در گزارش تجمیعی داشبورد */
const DEFECT_REPORT_TYPE_OPTIONS = DEFECT_TYPE_OPTIONS;

function defectTypeOptionLabel(type) {
    if (type === DEFECT_TYPE_OTHER) return "سایر";
    return type || "—";
}

function defectTypeDisplay(type, custom) {
    if (type === DEFECT_TYPE_OTHER) {
        return custom ? `سایر: ${custom}` : "سایر";
    }
    return type || "—";
}

function defectTypeSelectOptionsHtml(selected) {
    return DEFECT_TYPE_OPTIONS.map(
        (t) =>
            `<option value="${escapeHtml(t)}" ${t === selected ? "selected" : ""}>${escapeHtml(defectTypeOptionLabel(t))}</option>`
    ).join("");
}

function defectTypeCustomFieldHtml(id) {
    return `
        <div class="form-group defect-type-custom-wrap" id="${id}-wrap" style="display: none;">
            <label for="${id}">نوع عیب (سایر)</label>
            <input type="text" id="${id}" class="defect-type-custom-input" placeholder="نوع عیب را بنویسید" />
        </div>`;
}

function bindDefectTypeOtherToggle(selectEl, customWrapEl) {
    if (!selectEl || !customWrapEl) return;
    const sync = () => {
        customWrapEl.style.display = selectEl.value === DEFECT_TYPE_OTHER ? "" : "none";
    };
    selectEl.addEventListener("change", sync);
    sync();
}

function readDefectTypePayload(selectEl, customInputEl) {
    const defectType = selectEl.value;
    const custom = customInputEl ? customInputEl.value.trim() : "";
    return {
        defectType,
        defectTypeCustom: defectType === DEFECT_TYPE_OTHER ? custom : undefined,
    };
}

const DEFECT_STATUS_LABELS = {
    APPROVED: "تایید",
    NEEDS_WELDING: "نیاز به جوش",
};

/* ===========================
   زیرمرحله‌ی داخلی رادیوگرافی (شوت / تفسیر / ...)
   =========================== */

// ترتیب نمایش در دراپ‌داون؛ حرکت کاربران عادی خطی است (شوت → تفسیر → VT)
const RADIOGRAPHY_SUBSTAGE_OPTIONS = ["SHOT", "INTERPRETATION", "VT"];

const RADIOGRAPHY_SUBSTAGE_LABELS = {
    SHOT: "شوت",
    VT: "VT",
    INTERPRETATION: "تفسیر",
    // لیبل تاریخچه برای مقادیر قدیمی بعد از مهاجرت
    AWAITING_SHOT: "منتظر شوت",
    AWAITING_INTERPRETATION: "منتظر تفسیر",
    AWAITING_RESHOOT: "منتظر ری‌شوت",
    INTERPRETATION_REJECTED: "تفسیر تایید نشده",
    INTERPRETATION_FINALIZED: "تفسیر نهایی شد",
};

const RADIOGRAPHY_SUBSTAGE_WARNING = new Set(["AWAITING_RESHOOT", "INTERPRETATION_REJECTED"]);
const RADIOGRAPHY_SUBSTAGE_SUCCESS = new Set(["INTERPRETATION_FINALIZED"]);

function radiographySubStageBadgeClass(subStage) {
    if (RADIOGRAPHY_SUBSTAGE_WARNING.has(subStage)) return "substage-badge-warning";
    if (RADIOGRAPHY_SUBSTAGE_SUCCESS.has(subStage)) return "substage-badge-success";
    return "substage-badge-neutral";
}

const RadiographyState = {
    expanded: false,
};

function resetRadiographyState() {
    RadiographyState.expanded = false;
}

/**
 * آیکون دسترسی به عیوب رادیوگرافی؛ کنار وضعیت فعلی در پروفایل قطعه نمایش داده می‌شود.
 */
function renderRadiographyIcon(defects) {
    const count = defects.length;
    const hasOpenNeedsWelding = defects.some((d) => d.status === "NEEDS_WELDING" && !d.weldingConfirmed);
    const badge =
        count > 0
            ? `<span class="radiography-icon-badge${hasOpenNeedsWelding ? " radiography-icon-badge-warning" : ""}">${count}</span>`
            : "";
    const icon = typeof STAGE_ICON_RADIOGRAPHY !== "undefined"
        ? STAGE_ICON_RADIOGRAPHY
        : '<svg class="profile-icon-fallback" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4.9 19.1a9 9 0 0 1 0-12.7"/><path d="M7.8 16.2a5 5 0 0 1 0-7.1"/><circle cx="12" cy="12" r="1"/><path d="M16.2 16.2a5 5 0 0 0 0-7.1"/><path d="M19.1 19.1a9 9 0 0 0 0-12.7"/></svg>';

    return `
        <button type="button"
            class="profile-radiography-icon${RadiographyState.expanded ? " active" : ""}${hasOpenNeedsWelding ? " has-needs-welding" : ""}"
            id="btn-radiography-icon"
            title="عیوب رادیوگرافی"
            aria-label="عیوب رادیوگرافی">
            <span class="radiography-icon-symbol">${icon}</span>
            ${badge}
        </button>
    `;
}

function setDefectsPanelVisible(visible) {
    const container = document.getElementById("profile-defects-container");
    const iconBtn = document.getElementById("btn-radiography-icon");
    RadiographyState.expanded = visible;

    if (container) {
        container.style.display = visible ? "block" : "none";
    }
    if (iconBtn) {
        iconBtn.classList.toggle("active", visible);
    }
}

function formatDefectLocation(defect) {
    const start = defect.startCm != null ? defect.startCm : defect.locationCm;
    const end = defect.endCm != null ? defect.endCm : start;
    if (start == null && end == null) return "";
    const unit = [start, end].filter(value => value != null).every(value => /^\d+(?:\.\d+)?$/.test(String(value))) ? " cm" : "";
    if (start == null || sameDefectPosition(start, end)) return `${start != null ? start : end}${unit}`;
    return `${start}-${end}${unit}`;
}

// Numeric values from old snapshots and text values from the current API are both supported.
function sameDefectPosition(a, b) {
    return String(a ?? "").trim() === String(b ?? "").trim();
}

/** اقدام‌های اصلاحی ثابت (کد = نام enum در بک‌اند) */
const CORRECTIVE_ACTIONS = [
    { code: "IN_PLACE_MACHINING", label: "درجا تراش" },
    { code: "INSIDE_HATCH", label: "داخل دریچه" },
    { code: "OK", label: "OK" },
    { code: "THICKNESS_MEASUREMENT", label: "ضخامت مجاز" },
    { code: "PART_SHAPE", label: "شکل قطعه" },
    { code: "RESOLVED", label: "برطرف شد" },
    { code: "GRINDING", label: "سنگ زنی" },
];

function correctiveActionLabel(code) {
    const found = CORRECTIVE_ACTIONS.find((a) => a.code === code);
    return found ? found.label : "";
}

/** [{startCm,endCm}] → «(12-20)(25)(30-35)» */
function formatPositionsText(positions) {
    return (positions || [])
        .filter((p) => p && p.startCm != null)
        .map((p) => {
            const end = p.endCm != null ? p.endCm : p.startCm;
            return sameDefectPosition(end, p.startCm) ? `(${p.startCm})` : `(${p.startCm}-${end})`;
        })
        .join("");
}

/**
 * متن زنجیره: «(17) G.p T1 → جوش → OK → F.M.L T1 → سنگ زنی».
 * «جوش» فقط وقتی می‌آید که عیب اول جوشکاری شده باشد.
 */
function formatDefectChainText(defect) {
    const join = (a, b) => [a, b].filter(Boolean).join(" ");
    const typeOf = (d) => {
        const t = defectTypeDisplay(d.defectType, d.defectTypeCustom);
        return t && t !== "—" ? t : "";
    };
    const parts = [
        join(formatPositionsText(defect.positions), join(typeOf(defect), defect.defectClass || "")),
    ];
    if (defect.welded) parts.push(defect.reshootOk ? "ریشوت" : "جوش");
    parts.push(correctiveActionLabel(defect.correctiveAction));
    const second = defect.secondDefect;
    if (defect.reshootOk) {
        parts.push(defect.welded ? "جوش" : "ریشوت", "OK");
    } else if (second) {
        parts.push(join(typeOf(second), second.defectClass || ""));
        parts.push(correctiveActionLabel(second.correctiveAction));
    }
    return parts.filter(Boolean).join(" → ");
}

/** نمایش ساخت‌یافته مثل G.h T1(13-17) (منجر به جوش) یا G.h T1(20) */
function formatStructuredDefect(defect) {
    // عیب‌های ثبت‌شده از داخل سطر گزارش (زنجیره) با فرمت زنجیره نمایش داده می‌شوند
    if (defect.reportLineId || defect.correctiveAction) return formatDefectChainText(defect);
    const start = defect.startCm != null ? defect.startCm : defect.locationCm;
    const end = defect.endCm != null ? defect.endCm : start;
    let loc = "";
    if (start != null && (end == null || sameDefectPosition(start, end))) loc = `(${start})`;
    else if (start != null) loc = `(${start}-${end})`;
    const type = defectTypeDisplay(defect.defectType, defect.defectTypeCustom);
    const typePart = type && type !== "—" ? type : "";
    const cls = defect.defectClass || defect.typeCode || "";
    const head = [typePart, cls].filter(Boolean).join(" ");
    const weld = defect.leadsToWelding || defect.status === "NEEDS_WELDING" ? " (منجر به جوش)" : "";
    return `${head}${loc}${weld}`.trim();
}

function formatDefectLine(defect) {
    let statusLabel = DEFECT_STATUS_LABELS[defect.status] || defect.status || "";
    if (defect.status === "NEEDS_WELDING" && defect.weldingConfirmed) {
        statusLabel = "نیاز به جوش(تایید)";
    }
    const course = defect.course ? `${defect.course} — ` : "";
    return `${course}${formatStructuredDefect(defect)}${statusLabel ? " — " + statusLabel : ""}`;
}

/* ===========================
   رندر بخش عیوب داخل پروفایل قطعه
   =========================== */

function renderDefectsSection(pieceId, defects) {
    const canManage = hasPermission("MANAGE_RADIOGRAPHY_DEFECTS");
    return `
        <div class="profile-defects">
            <div class="profile-defects-panel-header">
                <span class="profile-defects-title">عیوب رادیوگرافی (${defects.length})</span>
                <button type="button" class="profile-defects-close" id="btn-close-defects-panel" title="بستن" aria-label="بستن"><span aria-hidden="true">&times;</span></button>
            </div>
            <div class="defects-body" id="defects-body">
                <div class="defects-list" id="defects-list">
                    ${renderDefectsList(defects, canManage)}
                </div>
                <div id="add-defect-form-container"></div>
                ${
                    canManage
                        ? `<button type="button" class="btn btn-secondary btn-sm" id="btn-add-defect" style="margin-top: 8px;">+ افزودن عیب رادیوگرافی</button>`
                        : ""
                }
            </div>
        </div>
    `;
}

function renderDefectsList(defects, canManage = hasPermission("MANAGE_RADIOGRAPHY_DEFECTS")) {
    if (defects.length === 0) {
        return `<div class="history-empty">هنوز عیبی برای این قطعه ثبت نشده است</div>`;
    }

    return defects
        .map((d) => {
            const needsWeldingOpen = d.status === "NEEDS_WELDING" && !d.weldingConfirmed;
            const lineClass =
                d.status === "NEEDS_WELDING"
                    ? d.weldingConfirmed
                        ? "defect-welding-confirmed"
                        : "defect-needs-welding"
                    : "defect-approved";

            const actions = [];
            if (canManage && needsWeldingOpen) {
                actions.push(
                    `<button type="button" class="btn btn-secondary btn-sm btn-confirm-welding" data-id="${d.id}">تایید جوش</button>`
                );
            }
            if (canManage) {
                actions.push(
                    `<button type="button" class="btn btn-danger btn-sm btn-delete-defect" data-id="${d.id}">حذف</button>`
                );
            }

            return `
        <div class="defect-row">
            <div class="defect-info">
                <span class="defect-line ${lineClass}">${formatDefectLine(d)}</span>
                ${d.recordedBy ? `<span class="defect-recorded-by">ثبت‌شده توسط ${escapeHtml(d.recordedBy)}</span>` : ""}
            </div>
            ${actions.length ? `<div class="defect-actions">${actions.join("")}</div>` : ""}
        </div>
    `;
        })
        .join("");
}

function attachDefectsSectionEvents(pieceOrId) {
    const pieceId = typeof pieceOrId === "object" ? pieceOrId.id : pieceOrId;
    if (typeof pieceOrId === "object") {
        attachRadiographyIconEvents(pieceOrId);
    }
    attachDeleteDefectEvents(pieceId);
    attachConfirmWeldingEvents(pieceId);

    const addBtn = document.getElementById("btn-add-defect");
    if (addBtn) {
        addBtn.addEventListener("click", () => showAddDefectForm(pieceId));
    }

    const closeBtn = document.getElementById("btn-close-defects-panel");
    if (closeBtn) {
        closeBtn.addEventListener("click", () => setDefectsPanelVisible(false));
    }
}

function attachRadiographyIconEvents(piece) {
    const iconBtn = document.getElementById("btn-radiography-icon");
    if (!iconBtn) return;

    iconBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        openRadiographyReportModal(piece);
    });
}

function attachDeleteDefectEvents(pieceId) {
    document.querySelectorAll(".btn-delete-defect").forEach((btn) => {
        btn.addEventListener("click", async () => {
            if (!confirm("آیا از حذف این عیب مطمئن هستید؟")) return;

            try {
                await RadiographyDefectAPI.delete(btn.dataset.id);
                showToast("عیب رادیوگرافی با موفقیت حذف شد");
                await refreshDefectsSection(pieceId);
                if (typeof loadTracking === "function") loadTracking();
                if (typeof loadDashboardAlerts === "function") loadDashboardAlerts();
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });
}

function attachConfirmWeldingEvents(pieceId) {
    document.querySelectorAll(".btn-confirm-welding").forEach((btn) => {
        btn.addEventListener("click", async () => {
            if (!confirm("آیا جوشکاری این عیب را تایید می‌کنید؟")) return;

            try {
                await RadiographyDefectAPI.confirmWelding(btn.dataset.id);
                showToast("جوشکاری عیب تایید شد");
                await refreshDefectsSection(pieceId);
                if (typeof loadTracking === "function") loadTracking();
                if (typeof loadDashboardAlerts === "function") loadDashboardAlerts();
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });
}

/* ===========================
   افزودن عیب جدید (فرم درون‌خطی)
   =========================== */

function showAddDefectForm(pieceId) {
    const container = document.getElementById("add-defect-form-container");

    container.innerHTML = `
        <form id="form-add-defect" class="defect-form">
            <div class="form-group">
                <label for="input-defect-course">کورس</label>
                <input type="text" id="input-defect-course" placeholder="مثلاً C1" required />
            </div>
            <div class="form-group">
                <label for="input-defect-type">نوع عیب</label>
                <select id="input-defect-type" required>
                    ${defectTypeSelectOptionsHtml()}
                </select>
            </div>
            ${defectTypeCustomFieldHtml("input-defect-type-custom")}
            <div class="form-group">
                <label for="input-defect-typecode">تایپ عیب</label>
                <input type="text" id="input-defect-typecode" placeholder="مثلاً T1" required />
            </div>
            <div class="form-group">
                <label for="input-defect-location">موقعیت عیب (اختیاری)</label>
                <input type="text" id="input-defect-location" placeholder="مثلاً 25 یا L4" maxlength="255" />
            </div>
            <div class="form-group">
                <label for="input-defect-status">وضعیت</label>
                <select id="input-defect-status" required>
                    <option value="APPROVED">تایید</option>
                    <option value="NEEDS_WELDING">نیاز به جوش</option>
                </select>
            </div>
            <div class="form-error" id="defect-form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary btn-sm" id="btn-cancel-add-defect">انصراف</button>
                <button type="submit" class="btn btn-primary btn-sm">ذخیره</button>
            </div>
        </form>
    `;

    document.getElementById("btn-cancel-add-defect").addEventListener("click", () => {
        container.innerHTML = "";
    });

    bindDefectTypeOtherToggle(
        document.getElementById("input-defect-type"),
        document.getElementById("input-defect-type-custom-wrap")
    );

    document.getElementById("form-add-defect").addEventListener("submit", async (e) => {
        e.preventDefault();

        const course = document.getElementById("input-defect-course").value.trim();
        const typePayload = readDefectTypePayload(
            document.getElementById("input-defect-type"),
            document.getElementById("input-defect-type-custom")
        );
        const typeCode = document.getElementById("input-defect-typecode").value.trim();
        const locationRaw = document.getElementById("input-defect-location").value.trim();
        const locationCm = locationRaw || null;
        const status = document.getElementById("input-defect-status").value;
        const errorBox = document.getElementById("defect-form-error");

        try {
            await RadiographyDefectAPI.create(pieceId, { course, typeCode, locationCm, status, ...typePayload });
            showToast("عیب رادیوگرافی با موفقیت ثبت شد");
            RadiographyState.expanded = true;
            await refreshDefectsSection(pieceId);
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
}

/* ===========================
   بازخوانی بخش عیوب بعد از افزودن/حذف
   =========================== */

async function refreshDefectsSection(pieceId) {
    try {
        const defects = await RadiographyDefectAPI.getByPiece(pieceId);
        const container = document.getElementById("profile-defects-container");
        const iconContainer = document.getElementById("profile-radiography-icon-container");
        if (!container) return;

        container.innerHTML = renderDefectsSection(pieceId, defects);
        container.style.display = RadiographyState.expanded ? "block" : "none";
        attachDefectsSectionEvents(pieceId);

        if (iconContainer) {
            const piece = await PieceAPI.getById(pieceId);
            iconContainer.innerHTML = renderRadiographyIcon(defects);
            attachRadiographyIconEvents(piece);
        }
    } catch (error) {
        showToast(error.message, "error");
    }
}

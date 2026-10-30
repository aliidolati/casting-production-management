/**
 * radiography-report.js
 * قالب گزارش روی ردیف قطعه (پاپ‌آپ «گزارش رادیوگرافی» در تعریف قطعه)،
 * و تکمیل تفسیر/عیوب روی هر قطعه (پروفایل قطعه).
 *
 * هر صفحه‌ی گزارش یک برگه‌ی A4 افقی است (۲۸۵×۱۹۷ میلی‌متر داخل حاشیه‌ی چاپ).
 * ارتفاع هر ردیف ۱۲ میلی‌متر است و اگر ردیف‌ها از ظرفیت صفحه بیشتر شوند،
 * صفحه‌ی بعدی زیر همان گزارش با همان هدر و فوتر ساخته می‌شود.
 * ظرفیت هر صفحه از روی اندازه‌گیری واقعی DOM تعیین می‌شود، نه عدد ثابت.
 *
 * ستون NO فقط در UI از روی ترتیب کورس‌ها ساخته می‌شود.
 * هندسه کورس (courseCode, imageCount, iqiAbs, accCriteria, th) روی قالب است.
 * تفسیر (iqiReal, result, defects) هنگام صدور/تکمیل گزارش وارد می‌شود.
 */

const RADIOGRAPHY_RESULT_OPTIONS = [
    { key: "RS", label: "RS" },
    { key: "RT", label: "RT" },
    { key: "RP", label: "RP" },
    { key: "RJ", label: "RJ" },
    { key: "A.C.C", label: "A.C.C" },
];

const RADIOGRAPHY_REPORT_STATUS_LABELS = {
    DRAFT: "پیش‌نویس",
    IN_INTERPRETATION: "در حال تفسیر",
    COMPLETED: "تکمیل‌شده",
    PENDING_MANAGER_APPROVAL: "در انتظار تأیید مدیر",
    REINTERPRETATION_REQUIRED: "نیازمند تفسیر مجدد",
};

const RADIOGRAPHY_REFERENCE_STANDARD = "MIL-STD-2175A/ASTME155/MIL-STD-453C";

/** ارتفاع ثابت هر ردیف گزارش */
const RT_ROW_MM = 12;
/** بازه‌ی فونت عیوب؛ با زیاد شدن عیوب به‌صورت خودکار از بیشینه به سمت کمینه کم می‌شود */
const RT_MAX_FONT_PX = 14;
const RT_MIN_FONT_PX = 6;

const RadiographyReportState = {
    piece: null,
    templates: [],
    reports: [],
    selectedTemplateId: null,
    reportDateIso: null,
};

/** پیش‌نویس قالب هنگام «افزودن تعریف قطعه جدید» (هنوز تعریفی برای ذخیره‌ی قالب نیست) */
let RadiographyTemplateDraft = null;

const RadiographyBuilderState = {
    templates: [],
    definitionId: null,
};

let RadiographyDesignerState = null;

function canManageRadiographyTemplates() {
    return hasPermission("MANAGE_PIECE_DEFINITIONS");
}

function canFillRadiographyReport() {
    // گزارشِ «در انتظار تأیید مدیر» قفل است (سرور هم همین را اعمال می‌کند)
    const locked = (RadiographyReportState.reports || []).some((r) => r.status === "PENDING_MANAGER_APPROVAL");
    return hasPermission("MANAGE_RADIOGRAPHY_DEFECTS") && !locked;
}

function rtMmToPx(mm) {
    return (mm * 96) / 25.4;
}

/* ===========================
   اسکلت مشترک صفحه‌ی گزارش (هم طراحی قالب، هم گزارش قطعه)
   =========================== */

const RT_COLGROUP = `
    <colgroup>
        <col style="width: 10mm" />
        <col style="width: 22mm" />
        <col style="width: 24mm" />
        <col style="width: 16mm" />
        <col style="width: 16mm" />
        <col style="width: 26mm" />
        <col style="width: 16mm" />
        ${RADIOGRAPHY_RESULT_OPTIONS.map(() => `<col style="width: 11mm" />`).join("")}
        <col />
    </colgroup>`;

function renderSheetThead() {
    return `
        <thead>
            <tr>
                <th rowspan="2">NO</th>
                <th rowspan="2">LINE NO</th>
                <th rowspan="2">Number / image</th>
                <th colspan="2" class="rt-iqi-group">IQI NO.</th>
                <th rowspan="2" class="rt-acc-col">A.C.C Criteria</th>
                <th rowspan="2" class="rt-th-col">TH</th>
                <th colspan="5">RESULT</th>
                <th rowspan="2">Defect type &amp; location</th>
            </tr>
            <tr>
                <th class="rt-iqi-abs">ABS.</th>
                <th class="rt-iqi-real-head">REAL</th>
                ${RADIOGRAPHY_RESULT_OPTIONS.map((opt) => `<th>${opt.label}</th>`).join("")}
            </tr>
        </thead>`;
}

function renderSheetHeader(meta, pageIndex) {
    const first = pageIndex === 0;
    // فقط صفحه‌ی اول قابل ویرایش است؛ صفحه‌های بعد آینه‌ی همان مقادیرند
    const state = meta.editable ? (first ? "" : "readonly") : "disabled";
    const field = (name, value, extra = "") =>
        `<input type="text" data-field="${name}" maxlength="80" value="${escapeHtml(value || "")}" ${extra} ${state} />`;

    return `
        <div class="rt-sheet-top">
            <div class="rt-sheet-brand">
                <div>
                    <div><span>نام قطعه</span> <strong>${escapeHtml(meta.partName || "—")}</strong></div>
                    <div><span>کد پروژه</span> <strong>${escapeHtml(meta.projectCode || "—")}</strong></div>
                    <div><span>شماره قطعه</span> <strong>${escapeHtml(meta.pieceNumber || "—")}</strong></div>
                </div>
            </div>
            <div class="rt-sheet-title">گزارش آزمایش رادیوگرافی</div>
            <div class="rt-sheet-meta">
                <label>تاریخ ${field("date", meta.date, first ? 'id="rt-report-date"' : "")}</label>
                <label>شماره گزارش ${field("reportNumber", meta.reportNumber)}</label>
                <label>شماره درخواست ${field("requestNumber", meta.requestNumber)}</label>
                <div class="rt-fixed-standard"><span>استاندارد مرجع</span> <strong class="rt-en">${RADIOGRAPHY_REFERENCE_STANDARD}</strong></div>
            </div>
        </div>`;
}

/**
 * stamps: مهر تأییدشده‌ی داخل کادرهای امضا — { interpreter: html, manager: html }
 * مهر مفسر داخل کادر «مفسر» و مهر مدیر داخل کادر «کنترل کیفیت» می‌نشیند و جای خانه‌های خالی را می‌گیرد.
 */
function renderRadiographyStaticFooter(stamps = {}) {
    const stampByTitle = { "مفسر": stamps.interpreter, "کنترل کیفیت": stamps.manager };
    const signBlocks = ["کارفرما", "پیمانکار", "کنترل کیفیت", "مفسر", "مجری"]
        .map((title) => {
            const stamp = stampByTitle[title];
            return `
            <div class="rt-sign-block${stamp ? " is-stamped" : ""}">
                <div class="rt-sign-title">${title}</div>
                ${
                    stamp ||
                    `<div class="rt-sign-grid">
                        <span>نام</span>
                        <span>تاریخ</span>
                        <span>امضا</span>
                        <span>نتیجه</span>
                    </div>`
                }
            </div>`;
        })
        .join("");

    return `
        <div class="rt-sheet-footer">
            <table class="rt-tech-table">
                <thead>
                    <tr>
                        <th>نوع دستگاه</th>
                        <th>نوع فیلم</th>
                        <th>دانسیته</th>
                        <th colspan="3">حساسیت</th>
                        <th>اندازه فیلم</th>
                        <th>G.H / G.P<br>S.C: shrinkage cavity<br>S.P: shrinkage sponge</th>
                        <th>CR: crack<br>VT / C.S / M.R<br>ماسه ریزی / ابعادی</th>
                        <th>F.M.M: foreign material more dense<br>F.M.L: foreign material low dense<br>F.M: film mark</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>XXG<br>16055</td>
                        <td>Fuji<br>IX50</td>
                        <td>2-3</td>
                        <td>A<br>1.4%</td>
                        <td>B<br>1.4%</td>
                        <td>C<br>2%</td>
                        <td></td>
                        <td colspan="3"></td>
                    </tr>
                </tbody>
            </table>
            <div class="rt-sign-row">${signBlocks}</div>
        </div>
    `;
}

/** فونت عیوب هر ردیف را تا جایی کم می‌کند که داخل ۱۲ میلی‌متر ارتفاع جا شود */
function fitRowDefectFont(tr) {
    const limit = rtMmToPx(RT_ROW_MM) - 4;
    tr.querySelectorAll(".rt-defect-list").forEach((list) => {
        let size = RT_MAX_FONT_PX;
        list.style.fontSize = `${size}px`;
        while (list.offsetHeight > limit && size > RT_MIN_FONT_PX) {
            size -= 0.5;
            list.style.fontSize = `${size}px`;
        }
    });
}

/**
 * ردیف‌ها را یکی‌یکی داخل صفحه می‌گذارد؛ اگر از ظرفیت صفحه بیرون زد،
 * ردیف به صفحه‌ی جدید (با همان هدر و فوتر) منتقل می‌شود.
 * container باید داخل DOM و قابل‌مشاهده باشد تا اندازه‌گیری درست باشد.
 */
function buildSheetPages(container, { lines, renderHeader, renderRow, footerStamps = {} }) {
    container.innerHTML = "";
    let pageIndex = 0;

    const makePage = () => {
        const page = document.createElement("section");
        page.className = "rt-page";
        page.innerHTML = `
            ${renderHeader(pageIndex)}
            <div class="rt-page-table-wrap">
                <table class="rt-sheet-table">${RT_COLGROUP}${renderSheetThead()}<tbody></tbody></table>
            </div>
            ${renderRadiographyStaticFooter(footerStamps)}`;
        container.appendChild(page);
        pageIndex += 1;
        return {
            page,
            tbody: page.querySelector("tbody"),
            wrap: page.querySelector(".rt-page-table-wrap"),
        };
    };

    let current = makePage();
    const overflows = () => current.wrap.scrollHeight > current.wrap.clientHeight + 1;

    lines.forEach((line, index) => {
        const html = renderRow(line, index);
        current.tbody.insertAdjacentHTML("beforeend", html);
        let tr = current.tbody.lastElementChild;
        fitRowDefectFont(tr);

        if (overflows() && current.tbody.children.length > 1) {
            current.tbody.removeChild(tr);
            current = makePage();
            current.tbody.insertAdjacentHTML("beforeend", html);
            tr = current.tbody.lastElementChild;
            fitRowDefectFont(tr);
        }
    });

    const pages = [...container.querySelectorAll(".rt-page")];
    pages.forEach((page, i) => {
        const label = document.createElement("div");
        label.className = "rt-page-label no-print";
        label.textContent = `صفحه ${i + 1} از ${pages.length}`;
        container.insertBefore(label, page);
    });
}

function syncSheetMetaMirrors(root) {
    ["date", "reportNumber", "requestNumber"].forEach((name) => {
        const inputs = root.querySelectorAll(`input[data-field="${name}"]`);
        const value = inputs[0] ? inputs[0].value : "";
        inputs.forEach((el, i) => {
            if (i > 0) el.value = value;
        });
    });
}

function attachSheetMetaSync(root) {
    root.addEventListener("input", (e) => {
        if (e.target.matches && e.target.matches("input[data-field]")) syncSheetMetaMirrors(root);
    });
}

/** چاپ: صفحه‌های همین گزارش عیناً (با همان اندازه‌ی میلی‌متری) کپی و چاپ می‌شوند */
function printRadiographyPages(container) {
    const pages = container ? [...container.querySelectorAll(".rt-page")] : [];
    if (!pages.length) {
        showToast("گزارش برای پرینت آماده نیست", "error");
        return;
    }

    document.getElementById("rt-print-root")?.remove();
    const root = document.createElement("div");
    root.id = "rt-print-root";

    pages.forEach((page) => {
        const copy = page.cloneNode(true);
        const sourceInputs = page.querySelectorAll("input");
        const copyInputs = copy.querySelectorAll("input");
        sourceInputs.forEach((src, i) => {
            const dst = copyInputs[i];
            if (!dst) return;
            if (src.type === "radio") {
                const mark = document.createElement("span");
                mark.className = "rt-check";
                mark.textContent = src.checked ? "✓" : "";
                dst.replaceWith(mark);
            } else {
                const text = document.createElement("span");
                text.className = "rt-print-value";
                text.textContent = src.value;
                dst.replaceWith(text);
            }
        });
        copy.querySelectorAll(".no-print, .jalali-datepicker-popup").forEach((el) => el.remove());
        root.appendChild(copy);
    });

    document.body.appendChild(root);
    document.body.classList.add("printing-radiography-report");
    const cleanup = () => {
        document.body.classList.remove("printing-radiography-report");
        root.remove();
    };
    window.addEventListener("afterprint", cleanup, { once: true });
    setTimeout(() => window.print(), 50);
}

/* ===========================
   «گزارش رادیوگرافی» در فرم تعریف قطعه (دکمه + پاپ‌آپ طراحی قالب)
   =========================== */

function renderRadiographyTemplateBuilder({ existingTemplates = [], allowAdd = true } = {}) {
    RadiographyBuilderState.templates = existingTemplates;

    const cards = existingTemplates
        .map((t) => {
            const used = t.reportCount > 0;
            return `
        <div class="rt-template-card rt-template-card-locked">
            <div class="rt-template-card-header">
                <strong>${escapeHtml(t.name)}</strong>
                <span class="form-hint">${t.lines.length} کورس${
                    used ? ` — در ${t.reportCount} گزارش استفاده شده؛ فقط افزودن کورس جدید ممکن است` : ""
                }</span>
            </div>
            <div class="form-actions" style="margin-top: 0;">
                <button type="button" class="btn btn-secondary btn-sm btn-rt-open-template" data-id="${t.id}">گزارش رادیوگرافی</button>
                ${
                    canManageRadiographyTemplates() && !used
                        ? `<button type="button" class="btn btn-danger btn-sm btn-delete-rt-template" data-id="${t.id}">حذف</button>`
                        : ""
                }
            </div>
        </div>`;
        })
        .join("");

    const addButton =
        allowAdd && canManageRadiographyTemplates()
            ? `<div class="form-actions" id="rt-open-designer-wrap">
                   <button type="button" class="btn btn-secondary" id="btn-rt-open-designer">${
                       existingTemplates.length ? "+ قالب گزارش جدید" : "گزارش رادیوگرافی"
                   }</button>
               </div>`
            : "";

    return `
        <fieldset class="rt-template-fieldset" id="rt-template-fieldset">
            <legend>گزارش رادیوگرافی</legend>
            <p class="form-hint">با کلیک روی دکمه، صفحهٔ گزارش (A4 افقی) باز می‌شود تا ردیف‌های کورس (LINE NO، Number / image، ABS، A.C.C Criteria، TH) را تعریف کنید. تعریف گزارش اختیاری است.</p>
            <div id="rt-existing-templates">${cards}</div>
            <div id="rt-draft-summary"></div>
            ${addButton}
        </fieldset>
    `;
}

function refreshRadiographyDraftSummary() {
    const box = document.getElementById("rt-draft-summary");
    const openWrap = document.getElementById("rt-open-designer-wrap");
    if (!box) return;

    if (!RadiographyTemplateDraft) {
        box.innerHTML = "";
        if (openWrap) openWrap.style.display = "";
        return;
    }

    box.innerHTML = `
        <div class="rt-template-card">
            <div class="rt-template-card-header">
                <strong>${escapeHtml(RadiographyTemplateDraft.name)}</strong>
                <span class="form-hint">${RadiographyTemplateDraft.lines.length} کورس — با ذخیرهٔ تعریف قطعه ثبت می‌شود</span>
            </div>
            <div class="form-actions" style="margin-top: 0;">
                <button type="button" class="btn btn-secondary btn-sm" id="btn-rt-edit-draft">گزارش رادیوگرافی</button>
                <button type="button" class="btn btn-danger btn-sm" id="btn-rt-clear-draft">حذف</button>
            </div>
        </div>`;
    if (openWrap) openWrap.style.display = "none";

    document.getElementById("btn-rt-edit-draft")?.addEventListener("click", () => {
        openRadiographyTemplateDesigner({
            definitionId: null,
            template: null,
            onSaved: refreshRadiographyDraftSummary,
        });
    });
    document.getElementById("btn-rt-clear-draft")?.addEventListener("click", () => {
        RadiographyTemplateDraft = null;
        refreshRadiographyDraftSummary();
    });
}

async function refreshRadiographyTemplateBuilder(definitionId) {
    const templates = await RadiographyReportAPI.getTemplates(definitionId);
    const holder = document.getElementById("rt-template-fieldset");
    if (!holder) return;
    holder.outerHTML = renderRadiographyTemplateBuilder({ existingTemplates: templates, allowAdd: true });
    attachRadiographyTemplateBuilderEvents({ definitionId });
}

function attachRadiographyTemplateBuilderEvents({ definitionId = null } = {}) {
    RadiographyBuilderState.definitionId = definitionId;
    // پیش‌نویس فقط متعلق به فرم «افزودن تعریف جدید» است؛ پیش‌نویس رهاشده‌ی قبلی نباید جایی ذخیره شود
    RadiographyTemplateDraft = null;
    refreshRadiographyDraftSummary();

    const existingNames = () => RadiographyBuilderState.templates.map((t) => t.name);

    const onSaved = async () => {
        if (definitionId) {
            try {
                await refreshRadiographyTemplateBuilder(definitionId);
            } catch (error) {
                showToast(error.message, "error");
            }
        } else {
            refreshRadiographyDraftSummary();
        }
    };

    document.getElementById("btn-rt-open-designer")?.addEventListener("click", () => {
        openRadiographyTemplateDesigner({
            definitionId,
            template: null,
            existingNames: existingNames(),
            onSaved,
        });
    });

    document.querySelectorAll(".btn-rt-open-template").forEach((btn) => {
        btn.addEventListener("click", () => {
            const template = RadiographyBuilderState.templates.find((t) => t.id === btn.dataset.id);
            if (!template) return;
            openRadiographyTemplateDesigner({
                definitionId,
                template,
                existingNames: existingNames(),
                onSaved,
            });
        });
    });

    document.querySelectorAll(".btn-delete-rt-template").forEach((btn) => {
        btn.addEventListener("click", async () => {
            if (!confirm("آیا از حذف این قالب مطمئن هستید؟")) return;
            try {
                await RadiographyReportAPI.deleteTemplate(btn.dataset.id);
                showToast("قالب گزارش حذف شد");
                await onSaved();
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });
}

/** در فرم افزودن تعریف جدید، پیش‌نویس قالب (اگر ساخته شده) بعد از ساخت تعریف ذخیره می‌شود */
async function saveNewTemplateIfFilled(pieceDefinitionId) {
    if (!RadiographyTemplateDraft) return true;
    const draft = RadiographyTemplateDraft;
    await RadiographyReportAPI.createTemplate(pieceDefinitionId, {
        name: draft.name,
        lines: draft.lines,
    });
    RadiographyTemplateDraft = null;
    return true;
}

/* ===========================
   پاپ‌آپ طراحی قالب (صفحه‌ی گزارش با ردیف‌های قابل ویرایش)
   =========================== */

function blankDesignLine(code = "") {
    return { id: null, courseCode: code, imageCount: "", iqiAbs: "", accCriteria: "", th: "", locked: false };
}

function openRadiographyTemplateDesigner({ definitionId = null, template = null, existingNames = [], onSaved = null } = {}) {
    const inDraft = !definitionId;
    const source = template || (inDraft ? RadiographyTemplateDraft : null);
    const used = !!template && template.reportCount > 0;

    const lines =
        source && source.lines && source.lines.length
            ? source.lines.map((l) => ({
                  id: l.id || null,
                  courseCode: l.courseCode || "",
                  imageCount: l.imageCount || "",
                  iqiAbs: l.iqiAbs || "",
                  accCriteria: l.accCriteria || "",
                  th: l.th || "",
                  locked: used && !!l.id,
              }))
            : [blankDesignLine("C1")]; // کورس خالی پیش‌فرض

    const nameInput = document.getElementById("input-edit-piece-name") || document.getElementById("input-piece-name");
    const defaultName = existingNames.some((n) => n.toLowerCase() === "گزارش اصلی") ? "" : "گزارش اصلی";

    RadiographyDesignerState = {
        definitionId,
        templateId: template ? template.id : null,
        inDraft,
        onSaved,
        name: source ? source.name : defaultName,
        lines,
        meta: {
            editable: false,
            partName: nameInput ? nameInput.value.trim() : "",
            projectCode: AppState.currentProjectCode || "",
            pieceNumber: "",
            date: "",
            reportNumber: "",
            requestNumber: "",
        },
    };

    openNestedModal(
        "گزارش رادیوگرافی — تعریف کورس‌ها",
        `
        <div class="rt-report-toolbar">
            <div class="form-group">
                <label for="rt-design-name">نام قالب</label>
                <input type="text" id="rt-design-name" maxlength="150" value="${escapeHtml(RadiographyDesignerState.name)}" />
            </div>
            <button type="button" class="btn btn-secondary" id="btn-rt-design-add-line">افزودن سطر</button>
        </div>
        ${
            used
                ? `<p class="form-hint">این قالب در گزارش استفاده شده است؛ کورس‌های قبلی قفل‌اند و فقط می‌توانید سطر جدید اضافه کنید.</p>`
                : ""
        }
        <div id="rt-design-pages" class="rt-pages"></div>
        <div class="form-error" id="rt-design-error"></div>
        <div class="form-actions">
            <button type="button" class="btn btn-secondary" id="btn-rt-design-cancel">انصراف</button>
            <button type="button" class="btn btn-primary" id="btn-rt-design-save">ذخیره</button>
        </div>`,
        { size: "sheet" }
    );

    const container = document.getElementById("rt-design-pages");
    renderDesignerPages();

    container.addEventListener("input", (e) => {
        const input = e.target;
        const tr = input.closest && input.closest("tr[data-design-index]");
        const field = input.dataset && input.dataset.designField;
        if (!tr || !field) return;
        const line = RadiographyDesignerState.lines[Number(tr.dataset.designIndex)];
        if (line) line[field] = input.value;
    });

    container.addEventListener("click", (e) => {
        const btn = e.target.closest && e.target.closest(".btn-rt-remove-line");
        if (!btn) return;
        const tr = btn.closest("tr[data-design-index]");
        const st = RadiographyDesignerState;
        st.lines.splice(Number(tr.dataset.designIndex), 1);
        if (!st.lines.length) st.lines.push(blankDesignLine("C1"));
        renderDesignerPages();
    });

    document.getElementById("btn-rt-design-add-line").addEventListener("click", () => {
        const st = RadiographyDesignerState;
        const taken = new Set(st.lines.map((l) => l.courseCode.trim().toUpperCase()));
        let n = st.lines.length + 1;
        while (taken.has(`C${n}`)) n += 1;
        st.lines.push(blankDesignLine(`C${n}`));
        renderDesignerPages(true);
    });

    document.getElementById("rt-design-name").addEventListener("input", (e) => {
        RadiographyDesignerState.name = e.target.value;
    });
    document.getElementById("btn-rt-design-cancel").addEventListener("click", closeNestedModal);
    document.getElementById("btn-rt-design-save").addEventListener("click", saveRadiographyDesigner);
}

function renderDesignRow(line, index) {
    const readonly = line.locked ? "readonly" : "";
    const cell = (field) =>
        `<td><input type="text" class="rt-design-input" data-design-field="${field}" maxlength="50" value="${escapeHtml(line[field] || "")}" ${readonly} /></td>`;
    return `
        <tr data-design-index="${index}">
            <td class="rt-no">${index + 1}</td>
            ${cell("courseCode")}
            ${cell("imageCount")}
            ${cell("iqiAbs")}
            <td class="rt-iqi-real-cell"></td>
            ${cell("accCriteria")}
            ${cell("th")}
            ${RADIOGRAPHY_RESULT_OPTIONS.map(() => `<td class="rt-result-col"></td>`).join("")}
            <td class="rt-defect-cell">${
                line.locked
                    ? ""
                    : `<button type="button" class="btn btn-danger btn-sm btn-rt-remove-line rt-cell-btn no-print">حذف</button>`
            }</td>
        </tr>`;
}

function renderDesignerPages(focusLast = false) {
    const st = RadiographyDesignerState;
    const container = document.getElementById("rt-design-pages");
    if (!st || !container) return;

    buildSheetPages(container, {
        lines: st.lines,
        renderHeader: (pageIndex) => renderSheetHeader(st.meta, pageIndex),
        renderRow: renderDesignRow,
    });

    if (focusLast) {
        const last = container.querySelector(`tr[data-design-index="${st.lines.length - 1}"] input`);
        if (last) {
            last.scrollIntoView({ block: "nearest" });
            last.focus();
            last.select();
        }
    }
}

async function saveRadiographyDesigner() {
    const st = RadiographyDesignerState;
    const errorBox = document.getElementById("rt-design-error");
    const fail = (message) => {
        errorBox.textContent = message;
        errorBox.style.display = "block";
    };

    const name = st.name.trim();
    if (!name) return fail("نام قالب را وارد کنید");

    const lines = [];
    const seen = new Set();
    for (const l of st.lines) {
        const courseCode = l.courseCode.trim();
        const imageCount = l.imageCount.trim();
        const iqiAbs = l.iqiAbs.trim();
        const accCriteria = l.accCriteria.trim();
        const th = l.th.trim();
        if (!courseCode && !imageCount && !iqiAbs && !accCriteria && !th && !l.id) continue;
        if (!courseCode) return fail("LINE NO هر سطر را وارد کنید");
        if (seen.has(courseCode.toUpperCase())) return fail(`LINE NO «${courseCode}» تکراری است`);
        seen.add(courseCode.toUpperCase());
        lines.push({
            id: l.id || null,
            courseCode,
            imageCount: imageCount || null,
            iqiAbs: iqiAbs || null,
            accCriteria: accCriteria || null,
            th: th || null,
        });
    }
    if (!lines.length) return fail("گزارش باید حداقل یک کورس داشته باشد");

    try {
        if (st.inDraft) {
            RadiographyTemplateDraft = { name, lines: lines.map(({ id, ...rest }) => rest) };
        } else if (st.templateId) {
            await RadiographyReportAPI.updateTemplate(st.templateId, { name, lines });
            showToast("قالب گزارش ذخیره شد");
        } else {
            await RadiographyReportAPI.createTemplate(st.definitionId, {
                name,
                lines: lines.map(({ id, ...rest }) => rest),
            });
            showToast("قالب گزارش ساخته شد");
        }
        const callback = st.onSaved;
        closeNestedModal();
        if (callback) await callback();
    } catch (error) {
        fail(error.message);
    }
}

/* ===========================
   تکمیل گزارش در پروفایل قطعه
   =========================== */

async function openRadiographyReportModal(piece) {
    let templates;
    let reports;
    try {
        [templates, reports] = await Promise.all([
            RadiographyReportAPI.getTemplates(piece.pieceDefinitionId),
            RadiographyReportAPI.getByPiece(piece.id),
        ]);
    } catch (error) {
        showToast(error.message, "error");
        return;
    }

    RadiographyReportState.piece = piece;
    RadiographyReportState.templates = templates;
    RadiographyReportState.reports = reports;

    const defaultTemplateId =
        RadiographyReportState.selectedTemplateId &&
        templates.some((t) => t.id === RadiographyReportState.selectedTemplateId)
            ? RadiographyReportState.selectedTemplateId
            : templates[0]?.id || null;
    RadiographyReportState.selectedTemplateId = defaultTemplateId;

    openModal(
        `گزارش رادیوگرافی — قطعه ${piece.pieceNumber}`,
        `<div id="rt-report-modal-body">${renderRadiographyReportPanel()}</div>`,
        { size: "sheet" }
    );
    attachRadiographyReportPanelEvents();
}

function currentReport() {
    return RadiographyReportState.reports.find(
        (r) => r.templateId === RadiographyReportState.selectedTemplateId
    );
}

function currentTemplate() {
    return RadiographyReportState.templates.find(
        (t) => t.id === RadiographyReportState.selectedTemplateId
    );
}

function reportStatusLabel(status) {
    return RADIOGRAPHY_REPORT_STATUS_LABELS[status] || "ساختار اولیه ساخته نشده";
}

function currentReportLines() {
    const report = currentReport();
    const template = currentTemplate();
    if (report) return report.lines;
    return template.lines.map((l) => ({
        ...l,
        templateLineId: l.id,
        id: null,
        iqiReal: "",
        result: null,
        defects: [],
    }));
}

function currentReportMeta() {
    const report = currentReport();
    const piece = RadiographyReportState.piece;
    return {
        editable: canFillRadiographyReport(),
        partName: report?.partName || piece?.pieceName || "",
        projectCode: report?.projectCode || piece?.projectCode || "",
        pieceNumber: String(piece?.pieceNumber ?? ""),
        date: report?.reportDate ? isoToJalaliString(report.reportDate) : "",
        reportNumber: report?.reportNumber || "",
        requestNumber: report?.requestNumber || "",
    };
}

function renderRadiographyReportPanel() {
    const templates = RadiographyReportState.templates;
    if (!templates.length) {
        return `
            <div class="history-empty">برای این ردیف قطعه هنوز قالب گزارش رادیوگرافی ساخته نشده است. قالب را از صفحهٔ تعریف قطعه (دکمهٔ «گزارش رادیوگرافی») اضافه کنید.</div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-rt-back-to-profile">بازگشت به پروفایل</button>
            </div>`;
    }

    const templateOptions = templates
        .map(
            (t) =>
                `<option value="${t.id}" ${t.id === RadiographyReportState.selectedTemplateId ? "selected" : ""}>${escapeHtml(t.name)}</option>`
        )
        .join("");

    const canFill = canFillRadiographyReport();
    const status = currentReport()?.status || "";

    return `
        <div class="rt-report-toolbar">
            <div class="form-group">
                <label for="rt-template-select">قالب گزارش</label>
                <select id="rt-template-select">${templateOptions}</select>
            </div>
            <span class="substage-badge substage-badge-neutral">${escapeHtml(reportStatusLabel(status))}</span>
        </div>
        <form id="form-rt-report" class="rt-report-form">
            <div id="rt-pages" class="rt-pages"></div>
            <div class="form-error" id="rt-report-form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-rt-back-to-profile">بازگشت به پروفایل</button>
                <button type="button" class="btn btn-secondary" id="btn-rt-print">پرینت</button>
                ${
                    canFill
                        ? `<button type="submit" class="btn btn-secondary" id="btn-rt-save-draft">ذخیره موقت</button>
                           <button type="button" class="btn btn-primary" id="btn-rt-complete">تکمیل / صدور گزارش</button>`
                        : ""
                }
            </div>
        </form>
    `;
}

function buildReportPages() {
    const container = document.getElementById("rt-pages");
    if (!container) return;
    const canFill = canFillRadiographyReport();
    const meta = currentReportMeta();
    buildSheetPages(container, {
        lines: currentReportLines(),
        renderHeader: (pageIndex) => renderSheetHeader(meta, pageIndex),
        renderRow: (line, index) => renderRadiographyReportLineRow(line, index, canFill),
    });
}

function renderRadiographyReportLineRow(line, index, canFill) {
    const templateLineId = line.templateLineId || line.id;
    const resultCells = RADIOGRAPHY_RESULT_OPTIONS.map(
        (opt) => `
        <td class="rt-result-col">
            <input type="radio" name="rt-result-${templateLineId}" value="${opt.key}"
                ${line.result === opt.key ? "checked" : ""} ${canFill ? "" : "disabled"} />
        </td>`
    ).join("");

    const defects = line.defects || [];

    return `
        <tr data-template-line-id="${templateLineId}" data-report-line-id="${line.id || ""}" data-course-code="${escapeHtml(line.courseCode || "")}">
            <td class="rt-no">${index + 1}</td>
            <td>${escapeHtml(line.courseCode || "")}</td>
            <td>${escapeHtml(line.imageCount || "")}</td>
            <td class="rt-iqi-abs">${escapeHtml(line.iqiAbs || "")}</td>
            <td class="rt-iqi-real-cell"><input type="text" class="rt-iqi-real" maxlength="50" value="${escapeHtml(line.iqiReal || "")}" ${canFill ? "" : "disabled"} /></td>
            <td class="rt-acc-col">${escapeHtml(line.accCriteria || "")}</td>
            <td class="rt-th-col">${escapeHtml(line.th || "")}</td>
            ${resultCells}
            <td class="rt-defect-cell">
                <div class="rt-defect-list">${defects.map((d) => renderReportDefectChip(d, canFill)).join("")}</div>
                ${
                    canFill
                        ? `<button type="button" class="btn btn-secondary btn-sm btn-rt-add-defect rt-cell-btn no-print">+ عیب</button>`
                        : ""
                }
            </td>
        </tr>`;
}

function renderReportDefectChip(d, canFill) {
    const needsWeldingOpen = (d.leadsToWelding || d.status === "NEEDS_WELDING") && !d.weldingConfirmed;
    const classes = ["rt-defect-chip"];
    if (needsWeldingOpen) classes.push("rt-defect-chip-weld");
    const interactive = canFill ? `role="button" tabindex="0" title="ویرایش / حذف"` : "";
    return `<span class="${classes.join(" ")}" data-id="${d.id}" ${interactive}>${escapeHtml(formatStructuredDefect(d))}</span>`;
}

function findReportDefect(defectId) {
    const report = currentReport();
    if (!report) return null;
    for (const line of report.lines || []) {
        const found = (line.defects || []).find((d) => d.id === defectId);
        if (found) return found;
    }
    return null;
}

function attachRadiographyReportPanelEvents() {
    document.getElementById("btn-rt-back-to-profile")?.addEventListener("click", () => {
        if (RadiographyReportState.piece) openPieceProfile(RadiographyReportState.piece.id);
    });

    document.getElementById("rt-template-select")?.addEventListener("change", async (e) => {
        RadiographyReportState.selectedTemplateId = e.target.value;
        document.getElementById("rt-report-modal-body").innerHTML = renderRadiographyReportPanel();
        attachRadiographyReportPanelEvents();
    });

    const pages = document.getElementById("rt-pages");
    if (!pages) return;

    buildReportPages();
    attachSheetMetaSync(pages);

    const dateInput = document.getElementById("rt-report-date");
    if (dateInput && canFillRadiographyReport()) {
        const report = currentReport();
        RadiographyReportState.reportDateIso = report?.reportDate || null;
        attachPersianDatePicker(dateInput, {
            onSelect: ({ jy, jm, jd }) => {
                RadiographyReportState.reportDateIso = jalaliToIsoDateString(jy, jm, jd);
                setTimeout(() => syncSheetMetaMirrors(pages), 0);
            },
            onClear: () => {
                RadiographyReportState.reportDateIso = null;
                setTimeout(() => syncSheetMetaMirrors(pages), 0);
            },
        });
    }

    pages.querySelectorAll(".rt-result-col input[type=radio]").forEach((radio) => {
        if (radio.checked) radio.dataset.wasChecked = "1";
        radio.addEventListener("click", () => {
            if (radio.disabled) return;
            if (radio.dataset.wasChecked === "1") {
                radio.checked = false;
                radio.dataset.wasChecked = "0";
                return;
            }
            const row = radio.closest("tr");
            row?.querySelectorAll(".rt-result-col input[type=radio]").forEach((other) => {
                other.dataset.wasChecked = "0";
            });
            radio.dataset.wasChecked = "1";
        });
    });

    // ثبت / ویرایش عیب: دکمه‌ی «+ عیب» و کلیک روی متن هر عیب
    pages.addEventListener("click", (e) => {
        const addBtn = e.target.closest(".btn-rt-add-defect");
        if (addBtn) {
            const tr = addBtn.closest("tr");
            showReportLineDefectForm(tr.dataset.templateLineId, tr.dataset.courseCode);
            return;
        }
        openDefectFromChip(e.target.closest(".rt-defect-chip[data-id]"));
    });
    pages.addEventListener("keydown", (e) => {
        if (e.key !== "Enter" && e.key !== " ") return;
        const chip = e.target.closest && e.target.closest(".rt-defect-chip[data-id]");
        if (!chip) return;
        e.preventDefault();
        openDefectFromChip(chip);
    });

    document.getElementById("form-rt-report")?.addEventListener("submit", async (e) => {
        e.preventDefault();
        await submitRadiographyReport(null);
    });
    document.getElementById("btn-rt-complete")?.addEventListener("click", async () => {
        await submitRadiographyReport("COMPLETED");
    });
    document.getElementById("btn-rt-print")?.addEventListener("click", () => {
        printRadiographyPages(document.getElementById("rt-pages"));
    });
}

function openDefectFromChip(chip) {
    if (!chip || !canFillRadiographyReport()) return;
    const defect = findReportDefect(chip.dataset.id);
    const tr = chip.closest("tr");
    if (!defect || !tr) return;
    showReportLineDefectForm(tr.dataset.templateLineId, tr.dataset.courseCode, defect);
}

/**
 * قبل از هر عملیات روی عیب، وضعیت فعلی فرم (IQI REAL، نتیجه‌ها و هدر) ذخیره می‌شود.
 * اگر گزارشی برای این قطعه ساخته نشده باشد همین‌جا ساخته می‌شود، پس کاربر
 * نیازی به «ذخیره موقت» قبل از «+ عیب» ندارد.
 */
async function saveReportQuietly() {
    const piece = RadiographyReportState.piece;
    const saved = await RadiographyReportAPI.save(piece.id, readReportSavePayload(null));
    const index = RadiographyReportState.reports.findIndex((r) => r.id === saved.id);
    if (index >= 0) RadiographyReportState.reports[index] = saved;
    else RadiographyReportState.reports.push(saved);
    return saved;
}

/* ===========================
   فرم زنجیره‌ی عیب:
   موقعیت‌ها → عیب 1 → تایپ 1 → جوشکاری شده/نشده → اقدام اصلاحی 1 → [عیب 2 → تایپ 2 → اقدام اصلاحی 2]
   بررسی مجدد اختیاری است: یا عیب دوم یا «ریشوت → OK»، فقط برای همین زنجیره.
   =========================== */

function correctiveActionOptionsHtml(selected) {
    const placeholder = `<option value="">— بدون اقدام —</option>`;
    return (
        placeholder +
        CORRECTIVE_ACTIONS.map(
            (a) =>
                `<option value="${a.code}" ${a.code === selected ? "selected" : ""}>${escapeHtml(a.label)}</option>`
        ).join("")
    );
}

/** نمودار عمودی [عیب 1] ↓ [اقدام 1] ↓ [عیب 2] ↓ [اقدام 2]؛ اگر جوش شده باشد روی اولین پیکان «جوش» می‌آید */
function renderDefectChainVisual(draft) {
    const join = (a, b) => [a, b].filter(Boolean).join(" ");
    const typeOf = (d) => {
        const t = defectTypeDisplay(d.defectType, d.defectTypeCustom);
        return t && t !== "—" ? t : "";
    };
    const box = (label, text) => `
        <div class="rt-chain-box">
            <span class="rt-chain-box-label">${label}</span>
            <span class="rt-chain-box-text">${escapeHtml(text || "—")}</span>
        </div>`;
    const plainLink = `<div class="rt-chain-link"><span class="rt-chain-arrow" aria-hidden="true">&#8595;</span></div>`;
    const weldLink = `
        <div class="rt-chain-link rt-chain-link-weld">
            <span class="rt-chain-weld-label">جوش</span>
            <span class="rt-chain-arrow rt-chain-arrow-weld" aria-hidden="true">&#8659;</span>
        </div>`;

    let html = box("عیب ۱", join(formatPositionsText(draft.positions), join(typeOf(draft), draft.defectClass || "")));
    // فیلدهای اختیاری که خالی‌اند در نمودار نمی‌آیند؛ فقط «جوش» همیشه یک اتصال لازم دارد
    const action1 = correctiveActionLabel(draft.correctiveAction);
    if (draft.welded && draft.reshootOk) {
        html += plainLink + box("بررسی مجدد", "ریشوت");
        if (action1) html += plainLink + box("اقدام اصلاحی ۱", action1);
        return html + weldLink + box("نتیجه", "OK");
    }
    if (draft.welded || action1) {
        html += draft.welded ? weldLink : plainLink;
        html += box("اقدام اصلاحی ۱", action1);
    }
    const s = draft.secondDefect;
    const secondText = s ? join(typeOf(s), s.defectClass || "") : "";
    if (draft.reshootOk) {
        html += plainLink + box("بررسی مجدد", "ریشوت") + plainLink + box("نتیجه", "OK");
    } else if (s && (secondText || s.correctiveAction)) {
        html += plainLink;
        html += box("عیب ۲", secondText);
        const action2 = correctiveActionLabel(s.correctiveAction);
        if (action2) {
            html += plainLink;
            html += box("اقدام اصلاحی ۲", action2);
        }
    }
    return html;
}

function showReportLineDefectForm(templateLineId, courseCode, defect) {
    // گزارش، پنل عیوب، جوشکاری و فیلتر داشبورد از یک فهرست مرورگر استفاده می‌کنند.
    const typeFieldsHtml = (entry, className, customId) => {
        const raw = entry?.defectType || "";
        const known = DEFECT_TYPE_OPTIONS.includes(raw);
        const selected = known ? raw : raw ? DEFECT_TYPE_OTHER : "";
        const custom = raw === DEFECT_TYPE_OTHER ? entry?.defectTypeCustom || "" : known ? "" : raw;
        return `<select class="${className}"><option value="">— انتخاب عیب —</option>${defectTypeSelectOptionsHtml(selected)}</select>
            <div class="form-group" id="${customId}-wrap" style="display: ${selected === DEFECT_TYPE_OTHER ? "" : "none"};">
                <label for="${customId}">نوع عیب (سایر)</label>
                <input type="text" id="${customId}" maxlength="255" value="${escapeHtml(custom)}" placeholder="نوع عیب را بنویسید" />
            </div>`;
    };
    const title = defect ? "ویرایش عیب رادیوگرافی" : "ثبت عیب رادیوگرافی";
    const needsWeldingOpen =
        defect && (defect.leadsToWelding || defect.status === "NEEDS_WELDING") && !defect.weldingConfirmed;
    const second = defect?.secondDefect || null;

    const state = {
        positions: (defect?.positions || []).map((p) => ({
            startCm: p.startCm,
            endCm: p.endCm != null ? p.endCm : p.startCm,
        })),
        reviewEnabled: !!second || !!defect?.reshootOk,
        reviewMode: defect?.reshootOk ? "RESHOOT_OK" : second ? "SECOND_DEFECT" : "",
        editIndex: null,
    };
    const weldedInitial = defect ? !!defect.welded : false;

    openNestedModal(
        title,
        `
        <form id="form-rt-line-defect" class="defect-form rt-line-defect-form rt-chain-form">
            <div class="form-hint">کورس: ${escapeHtml(courseCode || "")}</div>

            <section class="rt-chain-section">
                <h4 class="rt-chain-title">عیب اول</h4>

                <div class="form-group">
                    <label>موقعیت‌های عیب — اختیاری (مثلاً 25 یا L4)</label>
                    <div class="rt-pos-row">
                        <input type="text" class="rt-pos-start" maxlength="255" placeholder="از" />
                        <input type="text" class="rt-pos-end" maxlength="255" placeholder="تا (خالی = نقطه‌ای)" />
                        <button type="button" class="btn btn-secondary btn-sm" id="btn-rt-pos-add">افزودن موقعیت</button>
                    </div>
                    <div class="rt-pos-list" id="rt-pos-list"></div>
                </div>

                <div class="rt-defect-grid">
                    <div class="form-group">
                        <label>عیب اول <span class="rt-required" title="الزامی">*</span></label>
                        ${typeFieldsHtml(defect, "rt-defect-type", "rt-defect-custom")}
                    </div>
                    <div class="form-group">
                        <label>تایپ عیب اول (اختیاری)</label>
                        <input type="text" class="rt-defect-class" maxlength="255" placeholder="مثلاً T1" value="${escapeHtml(defect?.defectClass || defect?.typeCode || "")}" />
                    </div>
                </div>

                <label class="rt-weld-check">
                    <input type="checkbox" class="rt-welded" ${weldedInitial ? "checked" : ""} />
                    جوشکاری شده
                </label>

                <div class="form-group">
                    <label>اقدام اصلاحی اول (اختیاری)</label>
                    <select class="rt-action-1">${correctiveActionOptionsHtml(defect?.correctiveAction)}</select>
                </div>
            </section>

            <section class="rt-chain-section" id="rt-review-section" style="display: ${state.reviewEnabled ? "" : "none"};">
                <h4 class="rt-chain-title">نتیجه بررسی مجدد</h4>
                <div class="form-group">
                    <label for="rt-review-mode">نتیجه بررسی مجدد همان عیب</label>
                    <select id="rt-review-mode">
                        <option value="">— انتخاب نتیجه —</option>
                        <option value="SECOND_DEFECT" ${state.reviewMode === "SECOND_DEFECT" ? "selected" : ""}>ثبت عیب دوم</option>
                        <option value="RESHOOT_OK" ${state.reviewMode === "RESHOOT_OK" ? "selected" : ""}>ریشوت → OK</option>
                    </select>
                </div>
                <div id="rt-second-section" style="display: ${state.reviewMode === "SECOND_DEFECT" ? "" : "none"};">
                <div class="rt-defect-grid">
                    <div class="form-group">
                        <label>عیب دوم</label>
                        ${typeFieldsHtml(second, "rt-defect2-type", "rt-defect2-custom")}
                    </div>
                    <div class="form-group">
                        <label>تایپ عیب دوم (اختیاری)</label>
                        <input type="text" class="rt-defect2-class" maxlength="255" placeholder="مثلاً T1" value="${escapeHtml(second?.defectClass || "")}" />
                    </div>
                </div>
                <div class="form-group">
                    <label>اقدام اصلاحی دوم (اختیاری)</label>
                    <select class="rt-action-2">${correctiveActionOptionsHtml(second?.correctiveAction)}</select>
                </div>
                </div>
                <button type="button" class="btn btn-secondary btn-sm" id="btn-rt-remove-second">حذف نتیجه بررسی مجدد</button>
            </section>

            <button type="button" class="btn btn-secondary btn-sm" id="btn-rt-add-second" style="display: ${state.reviewEnabled ? "none" : ""};">+ ثبت نتیجه بررسی مجدد</button>

            <div class="rt-chain-preview">
                <div class="rt-chain-visual" id="rt-chain-visual"></div>
                <div class="rt-chain-text" id="rt-chain-text"></div>
            </div>

            <div class="form-group">
                <label>توضیحات</label>
                <input type="text" class="rt-defect-description" maxlength="1000" value="${escapeHtml(defect?.description || "")}" />
            </div>
            <div class="form-error" id="rt-defect-form-error"></div>
            <div class="form-actions">
                ${
                    defect
                        ? `<button type="button" class="btn btn-danger btn-sm" id="btn-rt-delete-defect">حذف عیب</button>`
                        : ""
                }
                ${
                    needsWeldingOpen
                        ? `<button type="button" class="btn btn-secondary btn-sm" id="btn-rt-confirm-welding">تایید جوش</button>`
                        : ""
                }
                <button type="button" class="btn btn-secondary btn-sm" id="btn-rt-cancel-defect">انصراف</button>
                <button type="submit" class="btn btn-primary btn-sm">${defect ? "ذخیره عیب" : "ثبت عیب"}</button>
            </div>
        </form>`
    );

    const formEl = document.getElementById("form-rt-line-defect");
    const q = (selector) => formEl.querySelector(selector);
    const valueOf = (selector) => q(selector).value.trim();

    const readDraft = () => {
        return {
            positions: state.positions,
            defectType: valueOf(".rt-defect-type"),
            defectTypeCustom: valueOf("#rt-defect-custom"),
            defectClass: valueOf(".rt-defect-class"),
            welded: q(".rt-welded").checked,
            correctiveAction: q(".rt-action-1").value,
            reshootOk: state.reviewEnabled && state.reviewMode === "RESHOOT_OK",
            secondDefect: state.reviewEnabled && state.reviewMode === "SECOND_DEFECT"
                ? {
                      defectType: valueOf(".rt-defect2-type"),
                      defectTypeCustom: valueOf("#rt-defect2-custom"),
                      defectClass: valueOf(".rt-defect2-class"),
                      correctiveAction: q(".rt-action-2").value,
                  }
                : null,
        };
    };

    const refreshPreview = () => {
        const draft = readDraft();
        document.getElementById("rt-chain-visual").innerHTML = renderDefectChainVisual(draft);
        document.getElementById("rt-chain-text").textContent = formatDefectChainText(draft);
    };

    const renderPositions = () => {
        const list = document.getElementById("rt-pos-list");
        list.innerHTML = state.positions.length
            ? state.positions
                  .map(
                      (p, i) => `
                <span class="rt-pos-item${state.editIndex === i ? " rt-pos-item-editing" : ""}">
                    <span class="rt-pos-text">${escapeHtml(formatPositionsText([p]))}</span>
                    <button type="button" class="rt-pos-btn" data-act="edit" data-i="${i}">ویرایش</button>
                    <button type="button" class="rt-pos-btn rt-pos-btn-danger" data-act="remove" data-i="${i}">حذف</button>
                </span>`
                  )
                  .join("")
            : `<span class="rt-pos-empty">هنوز موقعیتی اضافه نشده است</span>`;
        document.getElementById("btn-rt-pos-add").textContent = state.editIndex != null ? "ثبت ویرایش" : "افزودن موقعیت";
        refreshPreview();
    };

    const showReview = (enabled) => {
        state.reviewEnabled = enabled;
        document.getElementById("rt-review-section").style.display = enabled ? "" : "none";
        document.getElementById("rt-second-section").style.display = enabled && state.reviewMode === "SECOND_DEFECT" ? "" : "none";
        document.getElementById("btn-rt-add-second").style.display = enabled ? "none" : "";
        refreshPreview();
    };

    document.getElementById("btn-rt-pos-add").addEventListener("click", () => {
        const startRaw = valueOf(".rt-pos-start");
        const endRaw = valueOf(".rt-pos-end");
        const showPosError = (message) => {
            const box = document.getElementById("rt-defect-form-error");
            box.textContent = message;
            box.style.display = "block";
        };
        if (startRaw === "") return showPosError("شروع موقعیت را وارد کنید");
        const start = startRaw;
        const end = endRaw === "" ? start : endRaw;
        const duplicate = state.positions.some(
            (p, i) => i !== state.editIndex && sameDefectPosition(p.startCm, start) && sameDefectPosition(p.endCm, end)
        );
        if (duplicate) return showPosError("این موقعیت قبلاً اضافه شده است");

        document.getElementById("rt-defect-form-error").style.display = "none";
        const item = { startCm: start, endCm: end };
        if (state.editIndex != null) state.positions[state.editIndex] = item;
        else state.positions.push(item);
        state.editIndex = null;
        q(".rt-pos-start").value = "";
        q(".rt-pos-end").value = "";
        renderPositions();
    });

    document.getElementById("rt-pos-list").addEventListener("click", (e) => {
        const btn = e.target.closest(".rt-pos-btn");
        if (!btn) return;
        const index = Number(btn.dataset.i);
        if (btn.dataset.act === "remove") {
            state.positions.splice(index, 1);
            state.editIndex = null;
            q(".rt-pos-start").value = "";
            q(".rt-pos-end").value = "";
        } else {
            const p = state.positions[index];
            state.editIndex = index;
            q(".rt-pos-start").value = p.startCm;
            q(".rt-pos-end").value = sameDefectPosition(p.endCm, p.startCm) ? "" : p.endCm;
            q(".rt-pos-start").focus();
        }
        renderPositions();
    });

    bindDefectTypeOtherToggle(q(".rt-defect-type"), q("#rt-defect-custom-wrap"));
    bindDefectTypeOtherToggle(q(".rt-defect2-type"), q("#rt-defect2-custom-wrap"));
    document.getElementById("rt-review-mode").addEventListener("change", (e) => {
        state.reviewMode = e.target.value;
        showReview(true);
    });
    document.getElementById("btn-rt-add-second").addEventListener("click", () => showReview(true));
    document.getElementById("btn-rt-remove-second").addEventListener("click", () => {
        q(".rt-defect2-type").value = "";
        q("#rt-defect2-custom").value = "";
        q("#rt-defect2-custom-wrap").style.display = "none";
        q(".rt-defect2-class").value = "";
        q(".rt-action-2").value = "";
        state.reviewMode = "";
        document.getElementById("rt-review-mode").value = "";
        showReview(false);
    });
    // Enter داخل فیلدهای موقعیت یعنی «افزودن موقعیت»، نه ثبت کل فرم
    formEl.querySelectorAll(".rt-pos-start, .rt-pos-end").forEach((input) => {
        input.addEventListener("keydown", (e) => {
            if (e.key !== "Enter") return;
            e.preventDefault();
            document.getElementById("btn-rt-pos-add").click();
        });
    });
    formEl.addEventListener("input", refreshPreview);
    formEl.addEventListener("change", refreshPreview);
    renderPositions();

    const errorBox = document.getElementById("rt-defect-form-error");
    const showError = (message) => {
        errorBox.textContent = message;
        errorBox.style.display = "block";
    };
    const afterChange = async () => {
        closeNestedModal();
        await reloadRadiographyReportModal();
        if (typeof loadTracking === "function") loadTracking();
        if (typeof loadDashboardAlerts === "function") loadDashboardAlerts();
    };

    document.getElementById("btn-rt-cancel-defect")?.addEventListener("click", closeNestedModal);

    document.getElementById("btn-rt-delete-defect")?.addEventListener("click", async () => {
        if (!confirm("آیا از حذف این عیب مطمئن هستید؟")) return;
        try {
            await saveReportQuietly();
            await RadiographyDefectAPI.delete(defect.id);
            showToast("عیب رادیوگرافی حذف شد");
            await afterChange();
        } catch (error) {
            showError(error.message);
        }
    });

    document.getElementById("btn-rt-confirm-welding")?.addEventListener("click", async () => {
        if (!confirm("آیا جوشکاری این عیب را تایید می‌کنید؟")) return;
        try {
            await saveReportQuietly();
            await RadiographyDefectAPI.confirmWelding(defect.id);
            showToast("جوشکاری عیب تایید شد");
            await afterChange();
        } catch (error) {
            showError(error.message);
        }
    });

    document.getElementById("form-rt-line-defect")?.addEventListener("submit", async (e) => {
        e.preventDefault();
        e.stopPropagation();
        const draft = readDraft();

        // اعتبارسنجی هم‌راستا با بک‌اند (بک‌اند هم دوباره بررسی می‌کند)
        const stillEditing = state.editIndex != null;
        // بررسی مجدد اختیاری است؛ اگر انتخاب شود باید نتیجه و در حالت عیب دوم، نوع عیب مشخص باشد.
        const second = draft.secondDefect;
        const secondHasData = !!(second && (second.defectType || second.defectClass || second.correctiveAction));
        const problem =
            (stillEditing && "ویرایش موقعیت را با «ثبت ویرایش» تایید کنید") ||
            (!draft.defectType && "عیب اول را وارد کنید") ||
            (draft.defectType === DEFECT_TYPE_OTHER && !draft.defectTypeCustom && "برای گزینهٔ سایر، نوع عیب اول را بنویسید") ||
            (state.reviewEnabled && !state.reviewMode && "نتیجه بررسی مجدد را انتخاب کنید یا آن را حذف کنید") ||
            (second && !second.defectType && "نام عیب دوم را انتخاب کنید") ||
            (second?.defectType === DEFECT_TYPE_OTHER && !second.defectTypeCustom && "برای گزینهٔ سایر، نوع عیب دوم را بنویسید") ||
            "";
        if (problem) {
            showError(problem);
            return;
        }

        const payload = {
            positions: draft.positions.map((p) => ({ startCm: p.startCm, endCm: p.endCm })),
            defectType: draft.defectType,
            defectTypeCustom: draft.defectType === DEFECT_TYPE_OTHER ? draft.defectTypeCustom : null,
            defectClass: draft.defectClass || null,
            welded: draft.welded,
            correctiveAction: draft.correctiveAction || null,
            reshootOk: draft.reshootOk,
            secondDefect: secondHasData
                ? {
                      defectType: second.defectType,
                      defectTypeCustom: second.defectType === DEFECT_TYPE_OTHER ? second.defectTypeCustom : null,
                      defectClass: second.defectClass || null,
                      correctiveAction: second.correctiveAction || null,
                  }
                : null,
            description: valueOf(".rt-defect-description") || null,
        };
        try {
            const saved = await saveReportQuietly();
            if (defect) {
                await RadiographyDefectAPI.update(defect.id, payload);
                showToast("عیب رادیوگرافی ویرایش شد");
            } else {
                const reportLine = saved.lines.find((l) => l.templateLineId === templateLineId);
                if (!reportLine || !reportLine.id) {
                    throw new Error("سطر گزارش این کورس پیدا نشد");
                }
                await RadiographyDefectAPI.create(RadiographyReportState.piece.id, {
                    reportLineId: reportLine.id,
                    ...payload,
                });
                showToast("عیب رادیوگرافی ثبت شد");
            }
            await afterChange();
        } catch (error) {
            showError(error.message);
        }
    });
}

function metaInputValue(name) {
    const el = document.querySelector(`#rt-pages input[data-field="${name}"]`);
    return el && typeof el.value === "string" ? el.value.trim() || null : null;
}

function readReportSavePayload(status) {
    const lines = [...document.querySelectorAll("#rt-pages tr[data-template-line-id]")]
        .filter((row) => row.querySelector(".rt-iqi-real"))
        .map((row) => {
            const templateLineId = row.dataset.templateLineId;
            const checked = row.querySelector(`input[name="rt-result-${templateLineId}"]:checked`);
            return {
                templateLineId,
                iqiReal: row.querySelector(".rt-iqi-real").value.trim() || null,
                result: checked ? checked.value : null,
            };
        });
    return {
        templateId: RadiographyReportState.selectedTemplateId,
        reportDate: RadiographyReportState.reportDateIso,
        reportNumber: metaInputValue("reportNumber"),
        requestNumber: metaInputValue("requestNumber"),
        referenceStandard: RADIOGRAPHY_REFERENCE_STANDARD,
        status,
        lines,
    };
}

async function submitRadiographyReport(status) {
    const errorBox = document.getElementById("rt-report-form-error");
    const piece = RadiographyReportState.piece;
    try {
        await RadiographyReportAPI.save(piece.id, readReportSavePayload(status));
        showToast(status === "COMPLETED" ? "گزارش رادیوگرافی تکمیل شد" : "گزارش رادیوگرافی ذخیره شد");
        await reloadRadiographyReportModal();
        if (typeof loadTracking === "function") loadTracking();
    } catch (error) {
        errorBox.textContent = error.message;
        errorBox.style.display = "block";
    }
}

async function reloadRadiographyReportModal() {
    const piece = RadiographyReportState.piece;
    if (!piece) return;
    const selected = RadiographyReportState.selectedTemplateId;
    const box = document.getElementById("modal-box");
    const scrollTop = box ? box.scrollTop : 0;
    await openRadiographyReportModal(piece);
    RadiographyReportState.selectedTemplateId = selected;
    const body = document.getElementById("rt-report-modal-body");
    if (body) {
        body.innerHTML = renderRadiographyReportPanel();
        attachRadiographyReportPanelEvents();
    }
    if (box) box.scrollTop = scrollTop;
}

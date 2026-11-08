/**
 * app.js
 * مدیریت ناوبری بین بخش‌ها (Sections)، مودال، و نوتیفیکیشن (Toast).
 * این فایل "هسته" اپلیکیشن است و بقیه فایل‌های JS از توابع آن استفاده می‌کنند.
 */

// وضعیت فعلی اپلیکیشن (کدام پروژه/تعریف قطعه در حال مشاهده است)
const AppState = {
    currentProjectId: null,
    currentProjectCode: "",
    currentDefinitionId: null,
};

/* ===========================
   مدیریت بخش‌ها (Sections)
   =========================== */

function showSection(sectionId) {
    document.querySelectorAll(".content-section").forEach((el) => el.classList.remove("active"));
    document.getElementById(sectionId).classList.add("active");
}

function setActiveNavButton(sectionName) {
    document.querySelectorAll(".nav-btn").forEach((btn) => {
        btn.classList.toggle("active", btn.dataset.section === sectionName);
    });
}

/* ===========================
   مدیریت مودال
   =========================== */

/**
 * @param {string} title
 * @param {string} bodyHtml
 * @param {{ size?: "default" | "lg" | "xl" }} [options]
 */
const ModalFocus = {
    mainTrigger: null,
    nestedTrigger: null,
};

const MODAL_FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function isModalFocusable(el) {
    if (!el || el.disabled) return false;
    if (el.closest("[hidden]")) return false;
    const style = getComputedStyle(el);
    return style.display !== "none" && style.visibility !== "hidden";
}

function modalFocusableItems(container) {
    return [...container.querySelectorAll(MODAL_FOCUSABLE)].filter(isModalFocusable);
}

function setBackgroundInert(on) {
    ["login-screen", "app-shell"].forEach((id) => {
        const el = document.getElementById(id);
        if (!el) return;
        if (on) el.setAttribute("inert", "");
        else el.removeAttribute("inert");
    });
}

function rememberTrigger(slot, overlay) {
    const active = document.activeElement;
    const alreadyOpen = overlay.classList.contains("active");
    if (!alreadyOpen || (active && !overlay.contains(active))) {
        ModalFocus[slot] = active instanceof HTMLElement ? active : null;
    }
}

function restoreTrigger(slot) {
    const trigger = ModalFocus[slot];
    ModalFocus[slot] = null;
    if (trigger && document.contains(trigger)) trigger.focus();
}

function focusDialog(box, closeButtonId) {
    const closeBtn = document.getElementById(closeButtonId);
    if (closeBtn) closeBtn.focus();
    else box.focus();
}

function openModal(title, bodyHtml, options = {}) {
    const overlay = document.getElementById("modal-overlay");
    rememberTrigger("mainTrigger", overlay);
    document.getElementById("modal-title").textContent = title;
    document.getElementById("modal-body").innerHTML = bodyHtml;
    const box = document.getElementById("modal-box");
    box.classList.toggle("modal-box-lg", options.size === "lg");
    box.classList.toggle("modal-box-xl", options.size === "xl");
    box.classList.toggle("modal-box-sheet", options.size === "sheet");
    overlay.classList.add("active");
    setBackgroundInert(true);
    focusDialog(box, "modal-close");
}

function closeModal() {
    if(typeof revokeDrawingRevisionPreview === "function")revokeDrawingRevisionPreview();
    if (typeof resetWeldingMapState === "function") {
        resetWeldingMapState();
    }
    if (typeof resetAggregateWeldingMapState === "function") {
        resetAggregateWeldingMapState();
    }
    closeNestedModal();
    document.getElementById("modal-overlay").classList.remove("active");
    document.getElementById("modal-body").innerHTML = "";
    const box = document.getElementById("modal-box");
    box.classList.remove("modal-box-lg");
    box.classList.remove("modal-box-xl");
    box.classList.remove("modal-box-sheet");
    setBackgroundInert(false);
    restoreTrigger("mainTrigger");
}

function openNestedModal(title, bodyHtml, options = {}) {
    const overlay = document.getElementById("nested-modal-overlay");
    rememberTrigger("nestedTrigger", overlay);
    document.getElementById("nested-modal-title").textContent = title;
    document.getElementById("nested-modal-body").innerHTML = bodyHtml;
    document.getElementById("nested-modal-box").classList.toggle("modal-box-sheet", options.size === "sheet");
    overlay.classList.add("active");
    document.getElementById("modal-overlay")?.setAttribute("inert", "");
    focusDialog(document.getElementById("nested-modal-box"), "nested-modal-close");
}

function closeNestedModal() {
    const overlay = document.getElementById("nested-modal-overlay");
    if (!overlay || !overlay.classList.contains("active")) return;
    overlay.classList.remove("active");
    const body = document.getElementById("nested-modal-body");
    if (body) body.innerHTML = "";
    document.getElementById("nested-modal-box")?.classList.remove("modal-box-sheet");
    // Standalone confirmations also mark the main overlay inert; clear it before its next opening.
    document.getElementById("modal-overlay")?.removeAttribute("inert");
    restoreTrigger("nestedTrigger");
}

function trapDialogTab(event, container) {
    const items = modalFocusableItems(container);
    if (!items.length) {
        event.preventDefault();
        return;
    }
    const first = items[0];
    const last = items[items.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || !container.contains(active))) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && (active === last || !container.contains(active))) {
        event.preventDefault();
        first.focus();
    }
}

/* ===========================
   نوتیفیکیشن (Toast)
   =========================== */

const ToastState = { timer: null };

function showToast(message, type = "success", options = {}) {
    const toast = document.getElementById("toast");
    clearTimeout(ToastState.timer);
    toast.setAttribute("role", type === "error" || type === "warning" ? "alert" : "status");
    toast.setAttribute("aria-live", type === "error" || type === "warning" ? "assertive" : "polite");
    toast.textContent = message;
    toast.className = `toast show ${type}${options.position === "top" ? " toast-top" : ""}`;

    ToastState.timer = setTimeout(() => {
        toast.classList.remove("show");
    }, options.durationMs ?? 3000);
}

/* ===========================
   فرمت‌کننده‌های کمکی
   =========================== */

function formatDate(isoString) {
    const date = new Date(isoString);
    return date.toLocaleDateString("fa-IR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    });
}

function formatDateTime(isoString) {
    const date = new Date(isoString);
    return date.toLocaleString("fa-IR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
}

/**
 * جلوگیری از XSS: قبل از قرار دادن متن ورودی کاربر داخل innerHTML یا
 * value یک attribute، همیشه از این تابع برای escape کردن استفاده شود.
 */
function escapeHtml(value) {
    if (value === null || value === undefined) return "";
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

const STAGE_LABELS = {
    NOT_STARTED: "شروع نشده",
    CASTING: "ذوب‌ریزی",
    ROUGH_MACHINING: "خشن‌تراشی",
    RADIOGRAPHY: "رادیوگرافی",
    WELDING: "جوشکاری",
    HEAT_TREATMENT: "عملیات حرارتی",
    TENSILE: "تست کشش",
    SHOT_BLASTING: "شات‌بلاست",
    READY: "آماده تحویل",
    DELIVERED: "تحویل داده شد",
    SCRAPPED: "اسقاط",
};

function stageLabel(stage) {
    return STAGE_LABELS[stage] || stage;
}

const ALL_STAGES = [
    "NOT_STARTED",
    "CASTING",
    "ROUGH_MACHINING",
    "RADIOGRAPHY",
    "WELDING",
    "HEAT_TREATMENT",
    "TENSILE",
    "SHOT_BLASTING",
    "READY",
    "DELIVERED",
    "SCRAPPED",
];

const TRANSFERABLE_STAGES = ALL_STAGES.filter((stage) => stage !== "WELDING");

function selectableStages(piece) {
    const allowed = piece && Array.isArray(piece.allowedNextStages) ? piece.allowedNextStages : [];
    const current = piece ? piece.currentStage : null;
    return TRANSFERABLE_STAGES.filter((s) => s === current
        || (s === "SCRAPPED" && allowed.includes(s))
        || (piece?.forwardMove?.kind === "STAGE" && piece.forwardMove.stage === s));
}

function selectableSubstages(allOptions, current, allowedNext) {
    const allowed = Array.isArray(allowedNext) ? allowedNext : [];
    return allOptions.filter((s) => s === current || allowed.includes(s));
}

function intersectAllowedValues(pieces, field) {
    if (!pieces.length) return [];
    let set = new Set(pieces[0][field] || []);
    for (let i = 1; i < pieces.length; i++) {
        const next = new Set(pieces[i][field] || []);
        set = new Set([...set].filter((value) => next.has(value)));
    }
    return [...set];
}

function loaderMarkup(sizeClass = "") {
    const cls = sizeClass ? `loader ${sizeClass}` : "loader";
    return `<div class="${cls}" role="status" aria-label="در حال بارگذاری"></div>`;
}

function showTableLoader(tbody, colspan) {
    if (!tbody) return;
    tbody.innerHTML = `<tr class="loader-row"><td colspan="${colspan}"><div class="loader-wrap">${loaderMarkup()}</div></td></tr>`;
}

function setElementLoading(el, loading) {
    if (!el) return;
    el.classList.add("loading-host");
    let overlay = Array.from(el.children).find((child) => child.classList.contains("loading-overlay"));
    if (loading) {
        if (!overlay) {
            overlay = document.createElement("div");
            overlay.className = "loading-overlay";
            overlay.innerHTML = loaderMarkup();
            el.appendChild(overlay);
        }
    } else if (overlay) {
        overlay.remove();
    }
}

async function withButtonLoader(btn, asyncFn) {
    if (!btn) return asyncFn();
    const originalHtml = btn.innerHTML;
    btn.disabled = true;
    btn.classList.add("is-loading");
    btn.insertAdjacentHTML("beforeend", loaderMarkup("loader-sm"));
    try {
        return await asyncFn();
    } finally {
        btn.disabled = false;
        btn.classList.remove("is-loading");
        btn.innerHTML = originalHtml;
    }
}

/* ===========================
   راه‌اندازی اولیه اپلیکیشن
   =========================== */

document.addEventListener("DOMContentLoaded", () => {

    // کلیک روی دکمه‌های منوی اصلی
    document.querySelectorAll(".nav-btn").forEach((btn) => {
        btn.addEventListener("click", () => {
            const section = btn.dataset.section;
            setActiveNavButton(section);

            if (section === "tasks-my" || section === "tasks-all") {
                taskNavigate(section === "tasks-all" ? "#/tasks" : "#/tasks/my");
                return;
            }
            // از مسیرهای وظایف/پروفایل خارج می‌شویم؛ hash قدیمی نباید بماند
            if (typeof clearTaskHash === "function") clearTaskHash();

            if (section === "dashboard") {
                if (!hasPermission("VIEW_DASHBOARD")) {
                    showToast("شما اجازه‌ی مشاهده داشبورد را ندارید", "error");
                    return;
                }
                showSection("dashboard-section");
                loadDashboard();
            } else if (section === "projects") {
                showSection("projects-section");
                loadProjects();
            } else if (section === "tracking") {
                showSection("tracking-section");
                loadTracking();
            } else if (section === "users") {
                showSection("users-section");
                loadUsers();
            }
        });
    });

    // بستن مودال
    document.getElementById("modal-close").addEventListener("click", closeModal);
    document.getElementById("modal-overlay").addEventListener("click", (e) => {
        if (e.target.id === "modal-overlay") closeModal();
    });
    document.getElementById("nested-modal-close")?.addEventListener("click", closeNestedModal);
    document.getElementById("nested-modal-overlay")?.addEventListener("click", (e) => {
        if (e.target.id === "nested-modal-overlay") closeNestedModal();
    });

    document.addEventListener("keydown", (event) => {
        const nestedOpen = document.getElementById("nested-modal-overlay")?.classList.contains("active");
        const mainOpen = document.getElementById("modal-overlay")?.classList.contains("active");
        if (!nestedOpen && !mainOpen) return;
        if (event.key === "Escape") {
            event.preventDefault();
            if (nestedOpen) closeNestedModal();
            else closeModal();
            return;
        }
        if (event.key !== "Tab") return;
        const container = document.getElementById(nestedOpen ? "nested-modal-box" : "modal-box");
        if (container) trapDialogTab(event, container);
    });

    // بازگشت از جزئیات پروژه به لیست پروژه‌ها
    document.getElementById("btn-back-to-projects").addEventListener("click", () => {
        showSection("projects-section");
        setActiveNavButton("projects");
        loadProjects();
    });

    // بازگشت از جزئیات تعریف قطعه به جزئیات پروژه
    document.getElementById("btn-back-to-project-detail").addEventListener("click", () => {
        showSection("project-detail-section");
        openProjectDetail(AppState.currentProjectId);
    });

    // توجه: نمایش اولیه‌ی داشبورد دیگر اینجا انجام نمی‌شود؛ چون باید فقط بعد از
    // احراز هویت موفق کاربر رخ بدهد، این کار در auth.js (تابع showApp) انجام می‌شود.
});

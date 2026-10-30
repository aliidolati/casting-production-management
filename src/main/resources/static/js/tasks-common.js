/**
 * tasks-common.js
 * برچسب‌ها، نشان‌ها (badge)، قالب‌بندی مهلت، تأییدیه‌ی عملیات خطرناک و ابزارهای مشترک
 * صفحات وظایف، جزئیات وظیفه، پروفایل و ویجت داشبورد.
 */

const TASK_OPEN_STATUSES = ["TODO", "IN_PROGRESS", "WAITING"];

const TASK_STATUS_LABELS = {
    TODO: "برای انجام",
    IN_PROGRESS: "در حال انجام",
    WAITING: "در انتظار",
    COMPLETED: "تکمیل‌شده",
    CANCELLED: "لغو‌شده",
};

/** نماد کنار متن وضعیت؛ تا معنی فقط به رنگ وابسته نباشد */
const TASK_STATUS_ICONS = {
    TODO: "○",
    IN_PROGRESS: "◐",
    WAITING: "⏸",
    COMPLETED: "✓",
    CANCELLED: "✕",
};

const TASK_PRIORITY_LABELS = {
    LOW: "کم",
    MEDIUM: "متوسط",
    HIGH: "زیاد",
    CRITICAL: "بحرانی",
};

const TASK_PRIORITY_ICONS = {
    LOW: "↓",
    MEDIUM: "–",
    HIGH: "↑",
    CRITICAL: "‼",
};

function taskStatusLabel(status) {
    return TASK_STATUS_LABELS[status] || status;
}

function taskPriorityLabel(priority) {
    return TASK_PRIORITY_LABELS[priority] || priority;
}

function taskIsClosed(task) {
    return task.status === "COMPLETED" || task.status === "CANCELLED";
}

function canManageTasks() {
    return hasPermission("MANAGE_TASKS");
}

function taskStatusBadge(status) {
    return `<span class="task-badge task-status-${String(status).toLowerCase()}">
        <span class="task-badge-icon" aria-hidden="true">${TASK_STATUS_ICONS[status] || ""}</span>${escapeHtml(taskStatusLabel(status))}
    </span>`;
}

function taskPriorityBadge(priority) {
    return `<span class="task-badge task-priority-${String(priority).toLowerCase()}">
        <span class="task-badge-icon" aria-hidden="true">${TASK_PRIORITY_ICONS[priority] || ""}</span>${escapeHtml(taskPriorityLabel(priority))}
    </span>`;
}

/** نشان نوع وظیفه: وظایف گردش‌کار رادیوگرافی «RADIOGRAPHY» دارند؛ وظیفه‌ی عمومی نشانی ندارد */
const TASK_TYPE_LABELS = {
    MELT_PASSPORT_ENTRY: 'تکمیل شناسنامه ذوب',
    MELT_PASSPORT_APPROVAL: 'تأیید شناسنامه ذوب',
    RADIOGRAPHY_INTERPRETATION: "تفسیر رادیوگرافی",
    RADIOGRAPHY_MANAGER_APPROVAL: "تأیید مدیر رادیوگرافی",
};

function taskTypeBadge(task) {
    const type = task && task.taskType;
    if (!type || type === "GENERAL") return "";
    const radiography = type.startsWith("RADIOGRAPHY");
    return `<span class="task-type-badge ${radiography ? "task-type-badge-radiography" : ""}" title="${escapeHtml(TASK_TYPE_LABELS[type] || type)}">${radiography ? "RADIOGRAPHY" : escapeHtml(TASK_TYPE_LABELS[type] || type)}</span>`;
}

/* ===========================
   تاریخ و مهلت
   =========================== */

/** سرور میکروثانیه می‌فرستد (۷ رقم کسر)؛ برای سازگاری با همه‌ی مرورگرها به ۳ رقم کوتاه می‌شود */
function taskParseDate(iso) {
    if (!iso) return null;
    const normalized = String(iso).replace(/(\.\d{3})\d+/, "$1");
    const date = new Date(normalized);
    return Number.isNaN(date.getTime()) ? null : date;
}

function taskFormatDateTime(iso) {
    const date = taskParseDate(iso);
    return date ? formatDateTime(date.toISOString()) : "";
}

function taskFormatDate(iso) {
    const date = taskParseDate(iso);
    return date ? formatDate(date.toISOString()) : "";
}

function taskNumber(n) {
    return Number(n).toLocaleString("fa-IR");
}

function taskDurationText(totalMinutes) {
    const minutes = Math.max(1, Math.round(totalMinutes));
    if (minutes < 60) return `${taskNumber(minutes)} دقیقه`;
    if (minutes < 60 * 24) return `${taskNumber(Math.round(minutes / 60))} ساعت`;
    return `${taskNumber(Math.round(minutes / (60 * 24)))} روز`;
}

function taskStartOfDay(date) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

/**
 * متن و کلاس نمایش مهلت یک وظیفه. وضعیت «دارای تأخیر» از خود سرور می‌آید (task.overdue) و اینجا
 * هرگز محاسبه‌ی جداگانه‌ای از آن انجام نمی‌شود؛ فقط متن نسبی («فردا»، «۳ روز دیگر») ساخته می‌شود.
 */
function taskDueInfo(task) {
    if (!task.dueDate) {
        return { cls: "task-due-none", rel: "بدون مهلت", abs: "", icon: "" };
    }
    const abs = taskFormatDateTime(task.dueDate);
    if (taskIsClosed(task)) {
        return { cls: "task-due-closed", rel: "", abs, icon: "" };
    }
    if (task.overdue) {
        return {
            cls: "task-due-overdue",
            rel: `${taskDurationText(-(task.dueInMinutes ?? 0))} دیرکرد`,
            abs,
            icon: "⚠",
        };
    }
    const due = taskParseDate(task.dueDate);
    const dayDiff = due ? Math.round((taskStartOfDay(due) - taskStartOfDay(new Date())) / 86400000) : 99;
    if (dayDiff <= 0) return { cls: "task-due-soon", rel: "امروز", abs, icon: "⏰" };
    if (dayDiff === 1) return { cls: "task-due-soon", rel: "فردا", abs, icon: "⏰" };
    return { cls: "task-due-normal", rel: `${taskNumber(dayDiff)} روز دیگر`, abs, icon: "" };
}

function taskDueHtml(task) {
    const info = taskDueInfo(task);
    const icon = info.icon ? `<span aria-hidden="true">${info.icon}</span>` : "";
    return `<span class="task-due ${info.cls}">
        ${info.rel ? `<span class="task-due-rel">${icon}${escapeHtml(info.rel)}</span>` : ""}
        ${info.abs ? `<span class="task-due-abs">${escapeHtml(info.abs)}</span>` : ""}
    </span>`;
}

/** رشته‌ی ISO بدون منطقه‌ی زمانی (yyyy-MM-ddTHH:mm) از یک Date محلی */
function taskLocalIso(date) {
    const p = (n) => String(n).padStart(2, "0");
    return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}`;
}

function taskTodayIsoDate() {
    return taskLocalIso(new Date()).slice(0, 10);
}

/** آخرین فعالیت کاربر به‌صورت متن نسبی */
function taskRelativePast(iso) {
    const date = taskParseDate(iso);
    if (!date) return "ثبت نشده";
    const minutes = Math.round((Date.now() - date.getTime()) / 60000);
    if (minutes < 2) return "هم‌اکنون";
    if (minutes < 60 * 24 * 7) return `${taskDurationText(minutes)} پیش`;
    return taskFormatDate(iso);
}

/* ===========================
   لینک‌های داخلی
   =========================== */

function taskPersonHtml(user, { link = false } = {}) {
    if (!user) return "-";
    const name = escapeHtml(user.fullName || user.username);
    return link && user.id
        ? `<a class="task-person-link" href="#/profile/${user.id}">${name}</a>`
        : name;
}

function taskPartLabelHtml(task) {
    const parts = [];
    if (task.project) parts.push(`<span>پروژه: ${escapeHtml(task.project.code)}</span>`);
    if (task.part) parts.push(`<span>قطعه: ${escapeHtml(task.part.label)}</span>`);
    return parts.join("");
}

/* ===========================
   تأییدیه‌ی عملیات خطرناک
   =========================== */

/**
 * پنجره‌ی تأیید (روی مودال تودرتو). بستن با ×، Esc یا کلیک بیرون = انصراف.
 * @returns {Promise<{confirmed: boolean, reason: string}>}
 */
function taskConfirm({ title, message, confirmText = "تأیید", danger = false, reasonLabel = null }) {
    return new Promise((resolve) => {
        const overlay = document.getElementById("nested-modal-overlay");
        let settled = false;
        let observer = null;

        const finish = (result) => {
            if (settled) return;
            settled = true;
            if (observer) observer.disconnect();
            closeNestedModal();
            resolve(result);
        };

        openNestedModal(
            title,
            `<div class="task-confirm">
                <p class="task-confirm-message">${escapeHtml(message)}</p>
                ${
                    reasonLabel
                        ? `<div class="form-group">
                            <label for="task-confirm-reason">${escapeHtml(reasonLabel)}</label>
                            <textarea id="task-confirm-reason" rows="3" maxlength="500"></textarea>
                           </div>`
                        : ""
                }
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" id="task-confirm-cancel">انصراف</button>
                    <button type="button" class="btn ${danger ? "btn-danger" : "btn-primary"}" id="task-confirm-ok">${escapeHtml(confirmText)}</button>
                </div>
            </div>`
        );

        document.getElementById("task-confirm-cancel").addEventListener("click", () => finish({ confirmed: false, reason: "" }));
        document.getElementById("task-confirm-ok").addEventListener("click", () => {
            const reason = (document.getElementById("task-confirm-reason")?.value || "").trim();
            finish({ confirmed: true, reason });
        });

        // بسته‌شدن از بیرون (×، Esc، کلیک روی پس‌زمینه)
        observer = new MutationObserver(() => {
            if (!overlay.classList.contains("active")) {
                if (!settled) {
                    settled = true;
                    observer.disconnect();
                    resolve({ confirmed: false, reason: "" });
                }
            }
        });
        observer.observe(overlay, { attributes: true, attributeFilter: ["class"] });
    });
}

/* ===========================
   ناوبری و نمایش حالت‌ها
   =========================== */

function taskNavigate(hash) {
    if (location.hash === hash) {
        // مسیر یکسان است؛ hashchange رخ نمی‌دهد، پس دستی بازآفرینی می‌کنیم
        if (typeof handleTaskRoute === "function") handleTaskRoute();
    } else {
        location.hash = hash;
    }
}

function taskStateHtml({ icon = "🗒", title, message = "", actionHtml = "", error = false }) {
    return `<div class="task-state${error ? " task-state-error" : ""}" ${error ? 'role="alert"' : ""}>
        <div class="task-state-icon" aria-hidden="true">${icon}</div>
        <h3>${escapeHtml(title)}</h3>
        ${message ? `<p>${escapeHtml(message)}</p>` : ""}
        ${actionHtml}
    </div>`;
}

function taskProgressHtml(progress) {
    if (!progress || progress.total === 0) return "";
    return `<span class="task-mini-progress" title="چک‌لیست">
        <span class="task-progress" style="width:64px" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${progress.percent}" aria-label="پیشرفت چک‌لیست">
            <span class="task-progress-bar" style="display:block;width:${progress.percent}%"></span>
        </span>
        <span>${taskNumber(progress.completed)}/${taskNumber(progress.total)}</span>
    </span>`;
}

function debounceTask(fn, wait = 350) {
    let timer = null;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), wait);
    };
}

/** لیست مشترک کاربرانِ قابل‌دریافت وظیفه (کش کوتاه‌مدت) */
const TaskLookupCache = { assignees: null, projects: null };

async function taskLoadAssignees(force = false) {
    if (!TaskLookupCache.assignees || force) {
        TaskLookupCache.assignees = await TaskAPI.assignees();
    }
    return TaskLookupCache.assignees;
}

async function taskLoadProjects() {
    if (!TaskLookupCache.projects) {
        TaskLookupCache.projects = await ProjectAPI.getAll();
    }
    return TaskLookupCache.projects;
}

function taskResetLookupCache() {
    TaskLookupCache.assignees = null;
    TaskLookupCache.projects = null;
}

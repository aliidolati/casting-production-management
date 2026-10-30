/**
 * task-detail.js
 * صفحه‌ی جزئیات وظیفه: اطلاعات، تغییر وضعیت (با دلیل برای بازگشایی/لغو)، چک‌لیست،
 * نظرها و تاریخچه‌ی فعالیت. مجوز هر دکمه از `task.permissions` سرور می‌آید؛
 * UI فقط نمایش می‌دهد و سرور تصمیم‌گیر نهایی است.
 */

const TaskDetailState = {
    task: null,
    activities: [],
    activityPage: 0,
    activityTotalPages: 0,
    seq: 0,
};

async function openTaskDetail(taskId) {
    showSection("task-detail-section");
    setActiveNavButton(canManageTasks() ? "tasks-all" : "tasks-my");
    const root = document.getElementById("task-detail-root");
    const seq = ++TaskDetailState.seq;
    root.innerHTML = `<div class="task-state"><div class="loader-wrap">${loaderMarkup()}</div></div>`;

    try {
        const task = await TaskAPI.get(taskId);
        if (seq !== TaskDetailState.seq) return;
        TaskDetailState.task = task;
        TaskDetailState.activities = [];
        TaskDetailState.activityPage = 0;
        renderTaskDetailShell(task);
        await Promise.all([loadTaskChecklist(), loadTaskComments(), loadTaskActivities(true)]);
    } catch (error) {
        if (seq !== TaskDetailState.seq) return;
        root.innerHTML = taskStateHtml({
            icon: "⚠",
            title: "این وظیفه پیدا نشد",
            message: error.message,
            error: true,
            actionHtml: `<a class="btn btn-secondary" href="#/tasks/my">بازگشت به وظایف من</a>`,
        });
    }
}

/** پس از هر تغییر: وظیفه را دوباره از سرور می‌گیریم تا وضعیت/مجوزها همیشه تازه باشند */
async function refreshTaskDetail(updated) {
    const task = updated || (await TaskAPI.get(TaskDetailState.task.id));
    TaskDetailState.task = task;
    renderTaskDetailShell(task);
    await Promise.all([loadTaskChecklist(), loadTaskComments(), loadTaskActivities(true)]);
}

/* ===========================
   قاب اصلی
   =========================== */

function statusActionLabel(status, task) {
    if (task.status === "COMPLETED" || task.status === "CANCELLED") return "بازگشایی وظیفه";
    return {
        IN_PROGRESS: "شروع / در حال انجام",
        WAITING: "در انتظار",
        COMPLETED: "تکمیل وظیفه",
        TODO: "بازگشت به «برای انجام»",
        CANCELLED: "لغو وظیفه",
    }[status];
}

function renderTaskDetailShell(task) {
    const root = document.getElementById("task-detail-root");
    const perms = task.permissions;
    const closed = taskIsClosed(task);
    const info = taskDueInfo(task);

    // برای وظیفه‌ی بسته‌شده فقط یک دکمه‌ی «بازگشایی» (به «برای انجام» یا اولین وضعیت مجاز)
    const statusButtons = [];
    if (closed) {
        const target = perms.allowedStatuses.includes("TODO") ? "TODO" : perms.allowedStatuses[0];
        if (target) statusButtons.push({ status: target, reopen: true });
    } else {
        perms.allowedStatuses
            .filter((s) => s !== "CANCELLED")
            .forEach((s) => statusButtons.push({ status: s, reopen: false }));
    }
    const canCancelTask = perms.canCancel && perms.allowedStatuses.includes("CANCELLED");

    root.innerHTML = `
        <div class="task-detail-head">
            ${
                task.overdue
                    ? `<div class="task-overdue-banner" role="status"><span aria-hidden="true">⚠</span>
                         این وظیفه ${escapeHtml(info.rel)} دارد (مهلت: ${escapeHtml(info.abs)})</div>`
                    : ""
            }
            <h2 class="task-detail-title">${escapeHtml(task.title)}</h2>
            <div class="task-detail-badges">
                ${taskStatusBadge(task.status)}
                ${taskPriorityBadge(task.priority)}
                ${taskTypeBadge(task)}
            </div>
            <div class="task-detail-actions" role="group" aria-label="عملیات وظیفه">
                ${statusButtons
                    .map(
                        (b) =>
                            `<button type="button" class="btn ${b.status === "COMPLETED" ? "btn-primary" : "btn-secondary"}"
                                data-status="${b.status}" data-reopen="${b.reopen}">${escapeHtml(statusActionLabel(b.status, task))}</button>`
                    )
                    .join("")}
                ${perms.canEdit ? `<button type="button" class="btn btn-secondary" id="btn-task-edit">ویرایش</button>` : ""}
                ${canCancelTask ? `<button type="button" class="btn btn-danger" id="btn-task-cancel">لغو وظیفه</button>` : ""}
            </div>
        </div>

        <div class="task-detail-layout">
            <div class="task-detail-main">
                ${task.workflowId ? `<div id="task-workflow-region" aria-live="polite"></div>` : ""}
                <section class="task-card" aria-labelledby="task-desc-title">
                    <h3 class="task-card-title" id="task-desc-title">توضیحات</h3>
                    ${
                        task.description
                            ? `<div class="task-description">${escapeHtml(task.description)}</div>`
                            : `<p class="task-muted">توضیحاتی ثبت نشده است.</p>`
                    }
                </section>

                <section class="task-card" aria-labelledby="task-checklist-title">
                    <h3 class="task-card-title" id="task-checklist-title">چک‌لیست</h3>
                    <div id="task-checklist-region"></div>
                </section>

                <section class="task-card" aria-labelledby="task-comments-title">
                    <h3 class="task-card-title" id="task-comments-title">نظرها <span class="task-muted" id="task-comments-count"></span></h3>
                    <div id="task-comments-region"></div>
                </section>

                <section class="task-card" aria-labelledby="task-activity-title">
                    <h3 class="task-card-title" id="task-activity-title">تاریخچه‌ی فعالیت</h3>
                    <div id="task-activity-region"></div>
                </section>
            </div>

            <aside class="task-detail-aside">
                <section class="task-card" aria-labelledby="task-info-title">
                    <h3 class="task-card-title" id="task-info-title">مشخصات</h3>
                    <dl class="task-meta-list">
                        <div><dt>مسئول</dt><dd>${taskPersonHtml(task.assignee, { link: true })}${task.assignee?.department ? ` <span class="task-muted">(${escapeHtml(task.assignee.department)})</span>` : ""}</dd></div>
                        <div><dt>سازنده</dt><dd>${taskPersonHtml(task.creator, { link: canManageTasks() })}</dd></div>
                        <div><dt>مهلت</dt><dd>${taskDueHtml(task)}</dd></div>
                        ${
                            task.completedAt
                                ? `<div><dt>زمان تکمیل</dt><dd>${escapeHtml(taskFormatDateTime(task.completedAt))}</dd></div>`
                                : ""
                        }
                        <div><dt>پروژه</dt><dd>${
                            task.project
                                ? `<button type="button" class="task-link-btn" id="btn-task-open-project">${escapeHtml(task.project.code)} — ${escapeHtml(task.project.name)}</button>`
                                : `<span class="task-muted">-</span>`
                        }</dd></div>
                        <div><dt>قطعه</dt><dd>${
                            task.part
                                ? `<button type="button" class="task-link-btn" id="btn-task-open-part">${escapeHtml(task.part.label)}</button>`
                                : `<span class="task-muted">-</span>`
                        }</dd></div>
                        <div><dt>ایجاد</dt><dd>${escapeHtml(taskFormatDateTime(task.createdAt))}</dd></div>
                        <div><dt>آخرین تغییر</dt><dd>${escapeHtml(taskFormatDateTime(task.updatedAt))}</dd></div>
                    </dl>
                </section>
            </aside>
        </div>`;

    root.querySelectorAll("button[data-status]").forEach((btn) =>
        btn.addEventListener("click", () => onTaskStatusAction(btn))
    );
    document.getElementById("btn-task-edit")?.addEventListener("click", () => openTaskForm(task, (saved) => refreshTaskDetail(saved)));
    document.getElementById("btn-task-cancel")?.addEventListener("click", onTaskCancel);
    document.getElementById("btn-task-open-project")?.addEventListener("click", () => {
        history.replaceState(null, "", location.pathname);
        setActiveNavButton("projects");
        openProjectDetail(task.project.id);
    });
    document.getElementById("btn-task-open-part")?.addEventListener("click", () => {
        if (typeof openPieceProfile === "function") openPieceProfile(task.part.id);
    });
    if (task.workflowId && task.taskType?.startsWith('MELT_PASSPORT')) loadMeltTaskPanel(task);
    else if (task.workflowId && typeof loadTaskWorkflowPanel === "function") loadTaskWorkflowPanel(task);
}

/* ===========================
   تغییر وضعیت / لغو
   =========================== */

async function onTaskStatusAction(btn) {
    const task = TaskDetailState.task;
    const status = btn.dataset.status;
    const reopen = btn.dataset.reopen === "true";
    let reason = "";

    if (reopen) {
        const answer = await taskConfirm({
            title: "بازگشایی وظیفه",
            message: `این وظیفه ${taskStatusLabel(task.status)} است. با بازگشایی دوباره باز می‌شود و به مسئول و سازنده اعلان می‌رسد.`,
            reasonLabel: "دلیل بازگشایی (اختیاری)",
            confirmText: "بازگشایی",
        });
        if (!answer.confirmed) return;
        reason = answer.reason;
    } else if (status === "COMPLETED" && task.checklist && task.checklist.completed < task.checklist.total) {
        const left = task.checklist.total - task.checklist.completed;
        const answer = await taskConfirm({
            title: "تکمیل وظیفه",
            message: `${taskNumber(left)} آیتم چک‌لیست هنوز انجام نشده است. با این حال وظیفه تکمیل شود؟`,
            confirmText: "بله، تکمیل شود",
        });
        if (!answer.confirmed) return;
    }

    await withButtonLoader(btn, async () => {
        try {
            const updated = await TaskAPI.changeStatus(task.id, status, reason);
            showToast("وضعیت وظیفه تغییر کرد");
            await refreshTaskDetail(updated);
        } catch (error) {
            showToast(error.message, "error");
        }
    });
}

async function onTaskCancel() {
    const task = TaskDetailState.task;
    const answer = await taskConfirm({
        title: "لغو وظیفه",
        message: `وظیفه‌ی «${task.title}» لغو می‌شود. وظیفه و تاریخچه‌اش حذف نمی‌شوند و بعداً می‌توان آن را بازگشایی کرد.`,
        reasonLabel: "دلیل لغو (اختیاری)",
        confirmText: "لغو وظیفه",
        danger: true,
    });
    if (!answer.confirmed) return;
    try {
        const updated = await TaskAPI.cancel(task.id, answer.reason);
        showToast("وظیفه لغو شد");
        await refreshTaskDetail(updated);
    } catch (error) {
        showToast(error.message, "error");
    }
}

/* ===========================
   چک‌لیست
   =========================== */

async function loadTaskChecklist() {
    const region = document.getElementById("task-checklist-region");
    if (!region) return;
    try {
        renderTaskChecklist(await TaskAPI.checklist(TaskDetailState.task.id));
    } catch (error) {
        region.innerHTML = `<p class="task-muted" role="alert">${escapeHtml(error.message)}</p>`;
    }
}

function renderTaskChecklist(data) {
    const task = TaskDetailState.task;
    const perms = task.permissions;
    const region = document.getElementById("task-checklist-region");
    if (!region) return;
    const { items, progress } = data;

    region.innerHTML = `
        ${
            items.length
                ? `<div class="task-mini-progress" style="display:flex">
                    <div class="task-progress" style="flex:1" role="progressbar" aria-valuemin="0" aria-valuemax="100"
                         aria-valuenow="${progress.percent}" aria-label="پیشرفت چک‌لیست">
                        <div class="task-progress-bar" style="width:${progress.percent}%"></div>
                    </div>
                    <span>${taskNumber(progress.completed)} از ${taskNumber(progress.total)} (${taskNumber(progress.percent)}٪)</span>
                   </div>
                   <ul class="task-checklist">
                    ${items
                        .map(
                            (it) => `<li class="${it.completed ? "is-done" : ""}">
                                <input type="checkbox" id="chk-${it.id}" data-id="${it.id}" ${it.completed ? "checked" : ""}
                                    ${perms.canToggleChecklist ? "" : "disabled"} />
                                <label for="chk-${it.id}">${escapeHtml(it.title)}${
                                    it.completed && it.completedBy
                                        ? ` <span class="task-muted">— ${escapeHtml(it.completedBy.fullName || it.completedBy.username)}</span>`
                                        : ""
                                }</label>
                                ${
                                    perms.canManageChecklist
                                        ? `<button type="button" class="task-icon-btn" data-remove="${it.id}" aria-label="حذف «${escapeHtml(it.title)}»">×</button>`
                                        : ""
                                }
                            </li>`
                        )
                        .join("")}
                   </ul>`
                : `<p class="task-muted">چک‌لیستی تعریف نشده است.</p>`
        }
        ${
            perms.canManageChecklist
                ? `<form class="task-inline-add" id="form-task-checklist-add">
                    <label for="task-checklist-new" class="sr-only" style="position:absolute;left:-9999px">آیتم جدید</label>
                    <input type="text" id="task-checklist-new" maxlength="255" placeholder="آیتم جدید چک‌لیست" />
                    <button type="submit" class="btn btn-secondary">افزودن</button>
                   </form>`
                : ""
        }`;

    region.querySelectorAll("input[type=checkbox][data-id]").forEach((box) =>
        box.addEventListener("change", async () => {
            box.disabled = true;
            try {
                const updated = await TaskAPI.updateChecklistItem(task.id, box.dataset.id, { completed: box.checked });
                renderTaskChecklist(updated);
                refreshTaskProgressOnly(updated.progress);
                loadTaskActivities(true);
            } catch (error) {
                box.checked = !box.checked;
                box.disabled = false;
                showToast(error.message, "error");
            }
        })
    );
    region.querySelectorAll("button[data-remove]").forEach((btn) =>
        btn.addEventListener("click", async () => {
            const answer = await taskConfirm({
                title: "حذف آیتم چک‌لیست",
                message: "این آیتم از چک‌لیست حذف شود؟",
                confirmText: "حذف",
                danger: true,
            });
            if (!answer.confirmed) return;
            try {
                const updated = await TaskAPI.deleteChecklistItem(task.id, btn.dataset.remove);
                renderTaskChecklist(updated);
                refreshTaskProgressOnly(updated.progress);
                loadTaskActivities(true);
            } catch (error) {
                showToast(error.message, "error");
            }
        })
    );
    document.getElementById("form-task-checklist-add")?.addEventListener("submit", async (e) => {
        e.preventDefault();
        const input = document.getElementById("task-checklist-new");
        const title = input.value.trim();
        if (!title) return;
        try {
            const updated = await TaskAPI.addChecklistItem(task.id, title);
            renderTaskChecklist(updated);
            refreshTaskProgressOnly(updated.progress);
            loadTaskActivities(true);
            document.getElementById("task-checklist-new")?.focus();
        } catch (error) {
            showToast(error.message, "error");
        }
    });
}

/** پیشرفت چک‌لیست در حافظه به‌روز می‌شود تا هشدار «آیتم انجام‌نشده» هنگام تکمیل دقیق باشد */
function refreshTaskProgressOnly(progress) {
    if (TaskDetailState.task) TaskDetailState.task.checklist = progress;
}

/* ===========================
   نظرها
   =========================== */

async function loadTaskComments() {
    const region = document.getElementById("task-comments-region");
    if (!region) return;
    try {
        renderTaskComments(await TaskAPI.comments(TaskDetailState.task.id));
    } catch (error) {
        region.innerHTML = `<p class="task-muted" role="alert">${escapeHtml(error.message)}</p>`;
    }
}

function renderTaskComments(comments) {
    const task = TaskDetailState.task;
    const region = document.getElementById("task-comments-region");
    if (!region) return;
    document.getElementById("task-comments-count").textContent = comments.length ? `(${taskNumber(comments.length)})` : "";

    region.innerHTML = `
        ${
            comments.length
                ? comments
                      .map(
                          (c) => `<article class="task-comment" data-id="${c.id}">
                            <div class="task-comment-head">
                                <span><strong>${taskPersonHtml(c.author)}</strong>
                                    <span class="task-muted">${escapeHtml(taskFormatDateTime(c.createdAt))}${c.edited ? " · ویرایش‌شده" : ""}</span></span>
                                <span class="task-comment-actions">
                                    ${c.canEdit ? `<button type="button" class="task-icon-btn" data-edit="${c.id}" aria-label="ویرایش نظر">✎</button>` : ""}
                                    ${c.canDelete ? `<button type="button" class="task-icon-btn" data-delete="${c.id}" aria-label="حذف نظر">🗑</button>` : ""}
                                </span>
                            </div>
                            <div class="task-comment-body">${escapeHtml(c.content)}</div>
                          </article>`
                      )
                      .join("")
                : `<p class="task-muted">هنوز نظری ثبت نشده است.</p>`
        }
        <form class="task-comment-form" id="form-task-comment" style="margin-top:12px">
            <label for="task-comment-new" class="task-muted">نظر جدید</label>
            <textarea id="task-comment-new" maxlength="4000" placeholder="نظر یا گزارش پیشرفت را بنویسید… (Ctrl+Enter برای ارسال)"></textarea>
            <div class="form-actions"><button type="submit" class="btn btn-primary btn-sm">ثبت نظر</button></div>
        </form>`;

    const form = document.getElementById("form-task-comment");
    const textarea = document.getElementById("task-comment-new");
    textarea.addEventListener("keydown", (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
            e.preventDefault();
            form.requestSubmit();
        }
    });
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        const content = textarea.value.trim();
        if (!content) return;
        await withButtonLoader(form.querySelector("button[type=submit]"), async () => {
            try {
                await TaskAPI.addComment(task.id, content);
                await loadTaskComments();
                loadTaskActivities(true);
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });

    region.querySelectorAll("button[data-delete]").forEach((btn) =>
        btn.addEventListener("click", async () => {
            const answer = await taskConfirm({
                title: "حذف نظر",
                message: "این نظر حذف شود؟",
                confirmText: "حذف",
                danger: true,
            });
            if (!answer.confirmed) return;
            try {
                await TaskAPI.deleteComment(task.id, btn.dataset.delete);
                await loadTaskComments();
                loadTaskActivities(true);
            } catch (error) {
                showToast(error.message, "error");
            }
        })
    );
    region.querySelectorAll("button[data-edit]").forEach((btn) =>
        btn.addEventListener("click", () => {
            const comment = comments.find((c) => c.id === btn.dataset.edit);
            const article = region.querySelector(`.task-comment[data-id="${comment.id}"]`);
            const body = article.querySelector(".task-comment-body");
            body.innerHTML = `<textarea class="task-edit-area" maxlength="4000" style="width:100%;min-height:70px" aria-label="ویرایش نظر">${escapeHtml(comment.content)}</textarea>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary btn-sm" data-act="cancel">انصراف</button>
                    <button type="button" class="btn btn-primary btn-sm" data-act="save">ذخیره</button>
                </div>`;
            const area = body.querySelector("textarea");
            area.focus();
            body.querySelector("[data-act=cancel]").addEventListener("click", () => renderTaskComments(comments));
            body.querySelector("[data-act=save]").addEventListener("click", async () => {
                const content = area.value.trim();
                if (!content) return;
                try {
                    await TaskAPI.editComment(task.id, comment.id, content);
                    await loadTaskComments();
                } catch (error) {
                    showToast(error.message, "error");
                }
            });
        })
    );
}

/* ===========================
   تاریخچه‌ی فعالیت
   =========================== */

function taskActivityValue(action, value) {
    if (value === null || value === undefined || value === "") return "—";
    switch (action) {
        case "PRIORITY_CHANGED":
            return taskPriorityLabel(value);
        case "STATUS_CHANGED":
        case "REOPENED":
        case "COMPLETED":
        case "CANCELLED":
            return taskStatusLabel(value);
        case "DEADLINE_CHANGED":
            return taskFormatDateTime(value) || value;
        default:
            return value;
    }
}

function describeTaskActivity(a) {
    const q = (v) => `«${escapeHtml(taskActivityValue(a.action, v))}»`;
    switch (a.action) {
        case "CREATED":
            return "وظیفه را ایجاد کرد";
        case "ASSIGNED":
            return `وظیفه را به ${q(a.newValue)} محول کرد`;
        case "ASSIGNEE_CHANGED":
            return `مسئول را از ${q(a.oldValue)} به ${q(a.newValue)} تغییر داد`;
        case "TITLE_CHANGED":
            return `عنوان را از ${q(a.oldValue)} به ${q(a.newValue)} تغییر داد`;
        case "DESCRIPTION_CHANGED":
            return "توضیحات را ویرایش کرد";
        case "PRIORITY_CHANGED":
            return `اولویت را از ${q(a.oldValue)} به ${q(a.newValue)} تغییر داد`;
        case "DEADLINE_CHANGED":
            return `مهلت را از ${q(a.oldValue)} به ${q(a.newValue)} تغییر داد`;
        case "PROJECT_CHANGED":
            return `پروژه را از ${q(a.oldValue)} به ${q(a.newValue)} تغییر داد`;
        case "PART_CHANGED":
            return `قطعه را از ${q(a.oldValue)} به ${q(a.newValue)} تغییر داد`;
        case "STATUS_CHANGED":
            return `وضعیت را از ${q(a.oldValue)} به ${q(a.newValue)} تغییر داد`;
        case "COMPLETED":
            return "وظیفه را تکمیل کرد";
        case "REOPENED":
            return `وظیفه را بازگشایی کرد (از ${q(a.oldValue)} به ${q(a.newValue)})`;
        case "CANCELLED":
            return "وظیفه را لغو کرد";
        case "COMMENT_ADDED":
            return `نظر گذاشت: ${q(a.newValue)}`;
        case "COMMENT_DELETED":
            return `نظری را حذف کرد: ${q(a.oldValue)}`;
        case "CHECKLIST_ITEM_ADDED":
            return `آیتم ${q(a.newValue)} را به چک‌لیست افزود`;
        case "CHECKLIST_ITEM_COMPLETED":
            return `آیتم ${q(a.newValue)} را انجام‌شده علامت زد`;
        case "CHECKLIST_ITEM_REOPENED":
            return `آیتم ${q(a.newValue)} را از حالت انجام‌شده خارج کرد`;
        case "CHECKLIST_ITEM_REMOVED":
            return `آیتم ${q(a.oldValue)} را از چک‌لیست حذف کرد`;
        default:
            return escapeHtml(a.action);
    }
}

async function loadTaskActivities(reset = false) {
    const region = document.getElementById("task-activity-region");
    if (!region) return;
    const state = TaskDetailState;
    if (reset) {
        state.activities = [];
        state.activityPage = 0;
    }
    try {
        const page = await TaskAPI.activities(state.task.id, state.activityPage, 30);
        state.activities = state.activities.concat(page.items);
        state.activityTotalPages = page.totalPages;
        renderTaskActivities();
    } catch (error) {
        region.innerHTML = `<p class="task-muted" role="alert">${escapeHtml(error.message)}</p>`;
    }
}

function renderTaskActivities() {
    const region = document.getElementById("task-activity-region");
    if (!region) return;
    const state = TaskDetailState;
    if (!state.activities.length) {
        region.innerHTML = `<p class="task-muted">فعالیتی ثبت نشده است.</p>`;
        return;
    }
    const hasMore = state.activityPage + 1 < state.activityTotalPages;
    region.innerHTML = `
        <ol class="task-timeline">
            ${state.activities
                .map(
                    (a) => `<li>
                        <div><strong>${taskPersonHtml(a.actor)}</strong> ${describeTaskActivity(a)}</div>
                        ${a.note ? `<div class="task-note">دلیل/توضیح: ${escapeHtml(a.note)}</div>` : ""}
                        <time datetime="${escapeHtml(a.createdAt)}">${escapeHtml(taskFormatDateTime(a.createdAt))}</time>
                    </li>`
                )
                .join("")}
        </ol>
        ${hasMore ? `<button type="button" class="btn btn-secondary btn-sm" id="btn-task-activity-more">نمایش موارد قدیمی‌تر</button>` : ""}`;
    document.getElementById("btn-task-activity-more")?.addEventListener("click", () => {
        state.activityPage += 1;
        loadTaskActivities(false);
    });
}

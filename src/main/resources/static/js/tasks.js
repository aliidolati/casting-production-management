/**
 * tasks.js
 * صفحه‌ی «وظایف من» و «همه وظایف»: فیلتر/جستجو/مرتب‌سازی/صفحه‌بندی سمت سرور،
 * جدول (که روی موبایل به کارت تبدیل می‌شود)، فرم ساخت/ویرایش وظیفه و ویجت داشبورد.
 */

const TaskListState = {
    mode: "my", // "my" = وظایف محول‌شده به من | "all" = هر چه حق دیدنش را دارم
    quick: "open", // open | overdue | today | done | all
    q: "",
    status: "",
    priority: "",
    assigneeId: "",
    dueFrom: "",
    dueTo: "",
    sort: "smart",
    projectId: "",
    partId: "",
    contextLabels: {},
    page: 0,
    size: 20,
    requestSeq: 0,
    wired: false,
    pickers: {},
};

/* ===========================
   پارامترهای کوئری
   =========================== */

function taskListParams() {
    const s = TaskListState;
    const params = { page: s.page, size: s.size };

    if (s.mode === "my") params.mine = true;

    if (s.status) {
        params.status = [s.status];
    } else if (["open", "overdue", "today"].includes(s.quick)) {
        params.status = TASK_OPEN_STATUSES;
    } else if (s.quick === "done") {
        params.status = ["COMPLETED"];
    }
    if (s.quick === "overdue") params.overdue = true;
    if (s.quick === "today") params.dueDate = taskTodayIsoDate();

    if (s.priority) params.priority = [s.priority];
    if (s.assigneeId) params.assigneeId = s.assigneeId;
    if (s.projectId) params.projectId = s.projectId;
    if (s.partId) params.partId = s.partId;
    if (s.dueFrom) params.dueFrom = s.dueFrom;
    if (s.dueTo) params.dueTo = s.dueTo;
    if (s.q.trim()) params.q = s.q.trim();

    const [sort, dir] = s.sort.split(":");
    if (sort !== "smart") {
        params.sort = sort;
        params.dir = dir;
    }
    return params;
}

/* ===========================
   ورود به صفحه (توسط router)
   =========================== */

async function openTasksList(mode, query = {}) {
    const s = TaskListState;
    const modeChanged = s.mode !== mode;
    s.mode = mode === "all" && canManageTasks() ? "all" : "my";

    if (modeChanged || query._reset) {
        resetTaskFilters(false);
    }
    // فیلترهای ورودی از لینک (مثلاً «وظایف این قطعه»)
    s.partId = query.partId || "";
    s.projectId = query.projectId || "";
    s.assigneeId = query.assigneeId || s.assigneeId;
    s.contextLabels = {};

    showSection("tasks-section");
    setActiveNavButton(s.mode === "all" ? "tasks-all" : "tasks-my");
    document.getElementById("tasks-title").textContent = s.mode === "all" ? "همه وظایف" : "وظایف من";
    document.getElementById("btn-add-task").classList.toggle("hidden", !canManageTasks());
    document.getElementById("task-filter-assignee-group").classList.toggle("hidden", s.mode !== "all");

    wireTaskListOnce();
    syncTaskFilterControls();

    if (s.mode === "all" && !TaskLookupCache.assignees) {
        try {
            await populateTaskAssigneeFilter();
        } catch (_) {
            /* فیلتر مسئول اختیاری است */
        }
    }
    renderTaskContextChips();
    await loadTaskList();
}

async function populateTaskAssigneeFilter() {
    const select = document.getElementById("task-filter-assignee");
    const users = await taskLoadAssignees();
    const current = TaskListState.assigneeId;
    select.innerHTML =
        `<option value="">همه</option>` +
        users
            .map((u) => `<option value="${u.id}">${escapeHtml(u.fullName || u.username)}</option>`)
            .join("");
    select.value = current;
}

function resetTaskFilters(reload = true) {
    const s = TaskListState;
    Object.assign(s, {
        quick: "open",
        q: "",
        status: "",
        priority: "",
        assigneeId: "",
        dueFrom: "",
        dueTo: "",
        sort: "smart",
        page: 0,
    });
    s.pickers.dueFrom?.clear();
    s.pickers.dueTo?.clear();
    syncTaskFilterControls();
    if (reload) loadTaskList();
}

function syncTaskFilterControls() {
    const s = TaskListState;
    document.querySelectorAll("#task-quick-tabs .task-chip").forEach((btn) => {
        btn.setAttribute("aria-pressed", String(btn.dataset.quick === s.quick));
    });
    document.getElementById("task-filter-q").value = s.q;
    document.getElementById("task-filter-status").value = s.status;
    document.getElementById("task-filter-priority").value = s.priority;
    document.getElementById("task-filter-sort").value = s.sort;
    const assignee = document.getElementById("task-filter-assignee");
    if (assignee) assignee.value = s.assigneeId;
}

function renderTaskContextChips() {
    const s = TaskListState;
    const box = document.getElementById("task-context-chips");
    const chips = [];
    if (s.partId) chips.push({ key: "partId", label: `فقط وظایف یک قطعه‌ی مشخص` });
    if (s.projectId) chips.push({ key: "projectId", label: `فقط وظایف یک پروژه‌ی مشخص` });
    box.innerHTML = chips
        .map(
            (c) => `<span class="task-context-chip">${escapeHtml(c.label)}
                <button type="button" data-clear="${c.key}" aria-label="حذف این فیلتر">×</button></span>`
        )
        .join("");
    box.querySelectorAll("button[data-clear]").forEach((btn) => {
        btn.addEventListener("click", () => {
            s[btn.dataset.clear] = "";
            history.replaceState(null, "", "#/tasks" + (s.mode === "my" ? "/my" : ""));
            renderTaskContextChips();
            s.page = 0;
            loadTaskList();
        });
    });
}

/* ===========================
   بارگذاری و رندر لیست
   =========================== */

async function loadTaskList() {
    const s = TaskListState;
    const region = document.getElementById("task-list-region");
    const seq = ++s.requestSeq;

    region.setAttribute("aria-busy", "true");
    region.innerHTML = `<div class="task-state"><div class="loader-wrap">${loaderMarkup()}</div></div>`;
    document.getElementById("task-pagination").innerHTML = "";

    try {
        const result = await TaskAPI.list(taskListParams());
        if (seq !== s.requestSeq) return; // پاسخ کهنه (کاربر فیلتر را عوض کرده)
        if (result.totalPages > 0 && s.page >= result.totalPages) {
            s.page = result.totalPages - 1;
            return loadTaskList();
        }
        renderTaskList(result);
    } catch (error) {
        if (seq !== s.requestSeq) return;
        region.innerHTML = taskStateHtml({
            icon: "⚠",
            title: "بارگذاری وظایف ناموفق بود",
            message: error.message,
            error: true,
            actionHtml: `<button type="button" class="btn btn-secondary" id="btn-task-retry">تلاش مجدد</button>`,
        });
        document.getElementById("btn-task-retry")?.addEventListener("click", loadTaskList);
    } finally {
        if (seq === s.requestSeq) region.removeAttribute("aria-busy");
    }
}

function taskFiltersActive() {
    const s = TaskListState;
    return !!(s.q.trim() || s.status || s.priority || s.assigneeId || s.dueFrom || s.dueTo || s.partId || s.projectId || s.quick !== "open");
}

function renderTaskList(result) {
    const s = TaskListState;
    const region = document.getElementById("task-list-region");

    if (!result.items.length) {
        const filtered = taskFiltersActive();
        region.innerHTML = taskStateHtml(
            filtered
                ? {
                      icon: "🔍",
                      title: "وظیفه‌ای با این فیلترها پیدا نشد",
                      message: "فیلترها را تغییر دهید یا پاک کنید.",
                      actionHtml: `<button type="button" class="btn btn-secondary" id="btn-task-empty-clear">پاک کردن فیلترها</button>`,
                  }
                : {
                      icon: "✅",
                      title: s.mode === "my" ? "وظیفه‌ی بازی ندارید" : "هنوز وظیفه‌ای ثبت نشده است",
                      message: canManageTasks()
                          ? "برای شروع یک وظیفه‌ی جدید بسازید."
                          : "وقتی وظیفه‌ای به شما محول شود اینجا نمایش داده می‌شود.",
                      actionHtml: canManageTasks()
                          ? `<button type="button" class="btn btn-primary" id="btn-task-empty-add">+ وظیفه‌ی جدید</button>`
                          : "",
                  }
        );
        document.getElementById("btn-task-empty-clear")?.addEventListener("click", () => {
            s.partId = "";
            s.projectId = "";
            renderTaskContextChips();
            resetTaskFilters();
        });
        document.getElementById("btn-task-empty-add")?.addEventListener("click", () => openTaskForm(null));
        return;
    }

    const showAssignee = s.mode === "all";
    region.innerHTML = `
        <div class="table-wrap">
            <table class="data-table task-table ${showAssignee ? "" : "task-table-my"}">
                <thead>
                    <tr>
                        <th scope="col">عنوان</th>
                        <th scope="col">وضعیت</th>
                        <th scope="col">اولویت</th>
                        <th scope="col" class="task-col-assignee">مسئول</th>
                        <th scope="col">مهلت</th>
                    </tr>
                </thead>
                <tbody>
                    ${result.items.map(taskRowHtml).join("")}
                </tbody>
            </table>
        </div>`;

    region.querySelectorAll("tr.task-row").forEach((row) => {
        row.addEventListener("click", (e) => {
            if (e.target.closest("a, button")) return;
            taskNavigate(`#/tasks/${row.dataset.taskId}`);
        });
    });

    renderTaskPagination(result);
}

function taskRowHtml(task) {
    const progress = taskProgressHtml(task.checklist);
    const sub = taskPartLabelHtml(task);
    return `
        <tr class="task-row" data-task-id="${task.id}">
            <td class="task-title-cell">
                <a class="task-title-link" href="#/tasks/${task.id}">${escapeHtml(task.title)}</a>
                ${taskTypeBadge(task)}
                ${sub || progress ? `<div class="task-sub">${sub}${progress}</div>` : ""}
            </td>
            <td data-label="وضعیت">${taskStatusBadge(task.status)}</td>
            <td data-label="اولویت">${taskPriorityBadge(task.priority)}</td>
            <td data-label="مسئول" class="task-col-assignee">${taskPersonHtml(task.assignee, { link: canManageTasks() })}</td>
            <td data-label="مهلت">${taskDueHtml(task)}</td>
        </tr>`;
}

function renderTaskPagination(result) {
    const s = TaskListState;
    const box = document.getElementById("task-pagination");
    const first = result.page * result.size + 1;
    const last = Math.min(result.totalElements, (result.page + 1) * result.size);

    box.innerHTML = `
        <span aria-live="polite">نمایش ${taskNumber(first)} تا ${taskNumber(last)} از ${taskNumber(result.totalElements)} وظیفه</span>
        <div class="task-pagination-controls">
            <label for="task-page-size" class="task-muted">تعداد در صفحه</label>
            <select id="task-page-size">
                ${[10, 20, 50, 100].map((n) => `<option value="${n}" ${n === s.size ? "selected" : ""}>${taskNumber(n)}</option>`).join("")}
            </select>
            <button type="button" class="btn btn-secondary btn-sm" id="btn-task-prev" ${result.page <= 0 ? "disabled" : ""}>قبلی</button>
            <span>صفحه ${taskNumber(result.page + 1)} از ${taskNumber(Math.max(result.totalPages, 1))}</span>
            <button type="button" class="btn btn-secondary btn-sm" id="btn-task-next" ${result.page + 1 >= result.totalPages ? "disabled" : ""}>بعدی</button>
        </div>`;

    document.getElementById("btn-task-prev").addEventListener("click", () => {
        s.page = Math.max(0, s.page - 1);
        loadTaskList();
    });
    document.getElementById("btn-task-next").addEventListener("click", () => {
        s.page += 1;
        loadTaskList();
    });
    document.getElementById("task-page-size").addEventListener("change", (e) => {
        s.size = Number(e.target.value);
        s.page = 0;
        loadTaskList();
    });
}

/* ===========================
   اتصال رویدادهای نوار فیلتر (یک بار)
   =========================== */

function wireTaskListOnce() {
    const s = TaskListState;
    if (s.wired) return;
    s.wired = true;

    const statusSel = document.getElementById("task-filter-status");
    statusSel.innerHTML =
        `<option value="">همه</option>` +
        Object.entries(TASK_STATUS_LABELS).map(([k, v]) => `<option value="${k}">${v}</option>`).join("");
    const prioritySel = document.getElementById("task-filter-priority");
    prioritySel.innerHTML =
        `<option value="">همه</option>` +
        Object.entries(TASK_PRIORITY_LABELS).map(([k, v]) => `<option value="${k}">${v}</option>`).join("");

    const apply = () => {
        s.page = 0;
        loadTaskList();
    };

    document.querySelectorAll("#task-quick-tabs .task-chip").forEach((btn) => {
        btn.addEventListener("click", () => {
            s.quick = btn.dataset.quick;
            s.status = "";
            statusSel.value = "";
            syncTaskFilterControls();
            apply();
        });
    });

    document.getElementById("task-filter-q").addEventListener(
        "input",
        debounceTask((e) => {
            s.q = e.target.value;
            apply();
        })
    );

    statusSel.addEventListener("change", (e) => {
        s.status = e.target.value;
        if (s.status) s.quick = "all";
        syncTaskFilterControls();
        apply();
    });
    prioritySel.addEventListener("change", (e) => {
        s.priority = e.target.value;
        apply();
    });
    document.getElementById("task-filter-assignee").addEventListener("change", (e) => {
        s.assigneeId = e.target.value;
        apply();
    });
    document.getElementById("task-filter-sort").addEventListener("change", (e) => {
        s.sort = e.target.value;
        apply();
    });

    s.pickers.dueFrom = attachPersianDatePicker(document.getElementById("task-filter-due-from"), {
        onSelect: (d) => {
            s.dueFrom = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            apply();
        },
        onClear: () => {
            s.dueFrom = "";
            apply();
        },
    });
    s.pickers.dueTo = attachPersianDatePicker(document.getElementById("task-filter-due-to"), {
        onSelect: (d) => {
            s.dueTo = jalaliToIsoDateString(d.jy, d.jm, d.jd);
            apply();
        },
        onClear: () => {
            s.dueTo = "";
            apply();
        },
    });

    document.getElementById("btn-task-filters-clear").addEventListener("click", () => {
        s.partId = "";
        s.projectId = "";
        renderTaskContextChips();
        resetTaskFilters();
    });
    document.getElementById("task-filters").addEventListener("submit", (e) => e.preventDefault());
    document.getElementById("btn-add-task").addEventListener("click", () => openTaskForm(null));
}

/* ===========================
   فرم ساخت / ویرایش وظیفه
   =========================== */

/**
 * @param {object|null} task  null = وظیفه‌ی جدید
 * @param {(task:object)=>void} [onSaved]
 */
async function openTaskForm(task, onSaved) {
    const editing = !!task;
    openModal(editing ? "ویرایش وظیفه" : "وظیفه‌ی جدید", `<div class="loader-wrap">${loaderMarkup()}</div>`, { size: "lg" });

    let assignees;
    let projects = [];
    try {
        assignees = await taskLoadAssignees(true);
        try {
            projects = await taskLoadProjects();
        } catch (_) {
            projects = []; // انتخاب پروژه اختیاری است
        }
    } catch (error) {
        closeModal();
        showToast(error.message, "error");
        return;
    }

    // مسئول فعلی ممکن است دیگر «قابل دریافت وظیفه» نباشد؛ باز هم در لیست دیده شود تا بی‌صدا عوض نشود
    const assigneeOptions = [...assignees];
    if (editing && task.assignee && !assigneeOptions.some((u) => u.id === task.assignee.id)) {
        assigneeOptions.unshift(task.assignee);
    }

    const due = task?.dueDate ? taskParseDate(task.dueDate) : null;
    const dueIso = due ? taskLocalIso(due) : "";
    const state = {
        part: task?.part || null,
        checklist: [],
    };

    document.getElementById("modal-body").innerHTML = `
        <form id="form-task" class="task-form" novalidate>
            <div class="form-group">
                <label for="task-form-title">عنوان <span aria-hidden="true">*</span></label>
                <input type="text" id="task-form-title" maxlength="200" required value="${escapeHtml(task?.title || "")}" />
            </div>
            <div class="form-group">
                <label for="task-form-description">توضیحات</label>
                <textarea id="task-form-description" maxlength="5000">${escapeHtml(task?.description || "")}</textarea>
            </div>
            <div class="task-form-row">
                <div class="form-group">
                    <label for="task-form-assignee">مسئول <span aria-hidden="true">*</span></label>
                    <select id="task-form-assignee" required>
                        <option value="">انتخاب کنید</option>
                        ${assigneeOptions
                            .map(
                                (u) =>
                                    `<option value="${u.id}" ${task?.assignee?.id === u.id ? "selected" : ""}>${escapeHtml(u.fullName || u.username)}${u.department ? ` — ${escapeHtml(u.department)}` : ""}</option>`
                            )
                            .join("")}
                    </select>
                </div>
                <div class="form-group">
                    <label for="task-form-priority">اولویت <span aria-hidden="true">*</span></label>
                    <select id="task-form-priority">
                        ${Object.entries(TASK_PRIORITY_LABELS)
                            .map(([k, v]) => `<option value="${k}" ${(task?.priority || "MEDIUM") === k ? "selected" : ""}>${v}</option>`)
                            .join("")}
                    </select>
                </div>
            </div>
            <div class="task-form-row">
                <div class="form-group">
                    <label for="task-form-due-date">تاریخ مهلت</label>
                    <input type="text" id="task-form-due-date" placeholder="انتخاب تاریخ" />
                </div>
                <div class="form-group">
                    <label for="task-form-due-time">ساعت مهلت</label>
                    <input type="time" id="task-form-due-time" value="${dueIso ? dueIso.slice(11, 16) : ""}" />
                    <div class="task-form-hint">اگر ساعت خالی بماند، پایان همان روز (۲۳:۵۹) ثبت می‌شود.
                        <button type="button" class="task-link-btn" id="btn-task-clear-due">حذف مهلت</button></div>
                </div>
            </div>
            <div class="task-form-row">
                <div class="form-group">
                    <label for="task-form-project">پروژه (اختیاری)</label>
                    <select id="task-form-project">
                        <option value="">بدون پروژه</option>
                        ${projects
                            .map(
                                (p) =>
                                    `<option value="${p.id}" ${(task?.project?.id || "") === p.id ? "selected" : ""}>${escapeHtml(p.code)} — ${escapeHtml(p.name)}</option>`
                            )
                            .join("")}
                    </select>
                </div>
                <div class="form-group">
                    <label for="task-form-part-search">قطعه (اختیاری)</label>
                    <input type="search" id="task-form-part-search" placeholder="جستجوی نام یا شماره‌ی قطعه" autocomplete="off" />
                    <div id="task-form-part-chip" class="task-context-chips" style="margin-top:6px"></div>
                    <div id="task-form-part-results" class="task-part-results hidden" role="listbox" aria-label="نتایج جستجوی قطعه"></div>
                </div>
            </div>
            ${
                editing
                    ? ""
                    : `<div class="form-group">
                        <label for="task-form-checklist-input">چک‌لیست (اختیاری)</label>
                        <div class="task-inline-add">
                            <input type="text" id="task-form-checklist-input" maxlength="255" placeholder="یک مرحله بنویسید و افزودن را بزنید" />
                            <button type="button" class="btn btn-secondary" id="btn-task-checklist-add">افزودن</button>
                        </div>
                        <ul class="task-checklist-draft" id="task-form-checklist"></ul>
                    </div>`
            }
            <div class="form-error" id="task-form-error" role="alert"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-task-form-cancel">انصراف</button>
                <button type="submit" class="btn btn-primary" id="btn-task-form-save">${editing ? "ذخیره‌ی تغییرات" : "ایجاد وظیفه"}</button>
            </div>
        </form>`;

    const duePicker = attachPersianDatePicker(document.getElementById("task-form-due-date"));
    if (dueIso) duePicker.setIsoDate(dueIso.slice(0, 10));

    document.getElementById("btn-task-clear-due").addEventListener("click", () => {
        duePicker.clear();
        document.getElementById("task-form-due-time").value = "";
    });
    document.getElementById("btn-task-form-cancel").addEventListener("click", closeModal);

    wireTaskPartPicker(state, projects);
    if (!editing) wireTaskDraftChecklist(state);
    document.getElementById("task-form-title").focus();

    document.getElementById("form-task").addEventListener("submit", async (e) => {
        e.preventDefault();
        const errorBox = document.getElementById("task-form-error");
        errorBox.style.display = "none";

        const title = document.getElementById("task-form-title").value.trim();
        const assigneeId = document.getElementById("task-form-assignee").value;
        const fail = (message, focusId) => {
            errorBox.textContent = message;
            errorBox.style.display = "block";
            document.getElementById(focusId)?.focus();
        };
        if (!title) return fail("عنوان وظیفه الزامی است", "task-form-title");
        if (!assigneeId) return fail("مسئول وظیفه را انتخاب کنید", "task-form-assignee");

        const priority = document.getElementById("task-form-priority").value;
        const selectedDate = duePicker.getSelected();
        const time = document.getElementById("task-form-due-time").value || "23:59";
        const dueDate = selectedDate
            ? `${jalaliToIsoDateString(selectedDate.jy, selectedDate.jm, selectedDate.jd)}T${time}`
            : null;
        const projectValue = document.getElementById("task-form-project").value;

        const body = {
            title,
            description: document.getElementById("task-form-description").value.trim() || null,
            assigneeId,
            priority,
            dueDate,
            projectId: state.part ? null : projectValue || null,
            partId: state.part ? state.part.id : null,
        };
        if (!editing) body.checklist = state.checklist;

        // واگذاری وظیفه‌ی بحرانی به نفر دیگر: تأیید صریح
        if (priority === "CRITICAL" && (!editing || task.assignee?.id !== assigneeId)) {
            const who = assigneeOptions.find((u) => u.id === assigneeId);
            const { confirmed } = await taskConfirm({
                title: "تأیید وظیفه‌ی بحرانی",
                message: `وظیفه‌ی «بحرانی» به «${who?.fullName || who?.username || ""}» ${editing ? "واگذار" : "محول"} می‌شود و برای او اعلان فوری ارسال خواهد شد. ادامه می‌دهید؟`,
                confirmText: "بله، ادامه",
            });
            if (!confirmed) return;
        }

        const saveBtn = document.getElementById("btn-task-form-save");
        await withButtonLoader(saveBtn, async () => {
            try {
                const saved = editing ? await TaskAPI.update(task.id, body) : await TaskAPI.create(body);
                closeModal();
                showToast(editing ? "وظیفه به‌روزرسانی شد" : "وظیفه ایجاد شد");
                if (onSaved) onSaved(saved);
                else if (!editing) taskNavigate(`#/tasks/${saved.id}`);
            } catch (error) {
                fail(error.message, "task-form-title");
            }
        });
    });
}

function wireTaskPartPicker(state, projects) {
    const search = document.getElementById("task-form-part-search");
    const results = document.getElementById("task-form-part-results");
    const chip = document.getElementById("task-form-part-chip");
    const projectSel = document.getElementById("task-form-project");
    let seq = 0;

    const renderChip = () => {
        if (!state.part) {
            chip.innerHTML = "";
            projectSel.disabled = false;
            return;
        }
        chip.innerHTML = `<span class="task-context-chip">${escapeHtml(state.part.label)} (${escapeHtml(state.part.projectCode)})
            <button type="button" id="btn-task-part-remove" aria-label="حذف قطعه‌ی انتخاب‌شده">×</button></span>`;
        projectSel.value = state.part.projectId;
        projectSel.disabled = true; // پروژه از روی قطعه تعیین می‌شود
        document.getElementById("btn-task-part-remove").addEventListener("click", () => {
            state.part = null;
            renderChip();
        });
    };
    renderChip();

    const hideResults = () => {
        results.classList.add("hidden");
        results.innerHTML = "";
    };

    const runSearch = debounceTask(async () => {
        const term = search.value.trim();
        if (term.length < 1) return hideResults();
        const mine = ++seq;
        try {
            const parts = await TaskAPI.lookupParts(term, projectSel.disabled ? "" : projectSel.value);
            if (mine !== seq) return;
            results.classList.remove("hidden");
            results.innerHTML = parts.length
                ? parts
                      .map(
                          (p, i) => `<button type="button" class="task-part-option" role="option" data-i="${i}">
                            ${escapeHtml(p.label)}<small>پروژه ${escapeHtml(p.projectCode)} — ردیف ${taskNumber(p.rowNum)}</small></button>`
                      )
                      .join("")
                : `<div class="task-part-empty">قطعه‌ای پیدا نشد</div>`;
            results.querySelectorAll(".task-part-option").forEach((btn) => {
                btn.addEventListener("click", () => {
                    state.part = parts[Number(btn.dataset.i)];
                    search.value = "";
                    hideResults();
                    renderChip();
                });
            });
        } catch (error) {
            results.classList.remove("hidden");
            results.innerHTML = `<div class="task-part-empty">${escapeHtml(error.message)}</div>`;
        }
    }, 300);

    search.addEventListener("input", runSearch);
    search.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && !results.classList.contains("hidden")) {
            e.stopPropagation(); // فقط لیست نتایج بسته شود نه کل مودال
            hideResults();
        }
    });
}

function wireTaskDraftChecklist(state) {
    const input = document.getElementById("task-form-checklist-input");
    const list = document.getElementById("task-form-checklist");

    const render = () => {
        list.innerHTML = state.checklist
            .map(
                (item, i) => `<li><span>${escapeHtml(item)}</span>
                <button type="button" class="task-icon-btn" data-i="${i}" aria-label="حذف «${escapeHtml(item)}»">×</button></li>`
            )
            .join("");
        list.querySelectorAll("button[data-i]").forEach((btn) =>
            btn.addEventListener("click", () => {
                state.checklist.splice(Number(btn.dataset.i), 1);
                render();
            })
        );
    };
    const add = () => {
        const value = input.value.trim();
        if (!value) return;
        if (state.checklist.length >= 50) {
            showToast("حداکثر ۵۰ آیتم چک‌لیست مجاز است", "error");
            return;
        }
        state.checklist.push(value);
        input.value = "";
        render();
        input.focus();
    };
    document.getElementById("btn-task-checklist-add").addEventListener("click", add);
    input.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            e.preventDefault(); // Enter نباید کل فرم را ارسال کند
            add();
        }
    });
}

/* ===========================
   ویجت «وظایف من» در داشبورد
   =========================== */

async function loadMyTasksWidget() {
    const root = document.getElementById("my-tasks-widget");
    const body = document.getElementById("my-tasks-widget-body");
    if (!root || !body || !AuthState.user || !AuthState.user.id) return;

    body.innerHTML = `<div class="loader-wrap">${loaderMarkup()}</div>`;
    try {
        const [profile, page] = await Promise.all([
            UserAPI.getProfile(AuthState.user.id),
            TaskAPI.list({ mine: true, status: TASK_OPEN_STATUSES, size: 5 }),
        ]);
        const stats = profile.stats;
        body.innerHTML = `
            <div class="task-widget-stats">
                <div class="task-widget-stat"><strong>${taskNumber(stats.openTasks)}</strong><span>باز</span></div>
                <div class="task-widget-stat"><strong>${taskNumber(stats.dueToday)}</strong><span>مهلت امروز</span></div>
                <div class="task-widget-stat ${stats.overdue > 0 ? "is-alert" : ""}"><strong>${taskNumber(stats.overdue)}</strong><span>${stats.overdue > 0 ? "⚠ " : ""}دارای تأخیر</span></div>
            </div>
            ${
                page.items.length
                    ? `<ul class="task-widget-list">${page.items
                          .map(
                              (t) => `<li><a class="task-widget-item" href="#/tasks/${t.id}">
                                <span class="task-widget-item-title">${escapeHtml(t.title)}</span>
                                <span style="display:flex;gap:8px;align-items:center">${taskPriorityBadge(t.priority)}${taskDueHtml(t)}</span>
                              </a></li>`
                          )
                          .join("")}</ul>`
                    : `<p class="task-muted">وظیفه‌ی بازی ندارید.</p>`
            }`;
    } catch (error) {
        body.innerHTML = `<p class="task-muted" role="alert">بارگذاری وظایف ناموفق بود: ${escapeHtml(error.message)}
            <button type="button" class="task-link-btn" id="btn-my-tasks-retry">تلاش مجدد</button></p>`;
        document.getElementById("btn-my-tasks-retry")?.addEventListener("click", loadMyTasksWidget);
    }
}

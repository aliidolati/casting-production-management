/**
 * profile.js
 * صفحه‌ی پروفایل کاربر (#/profile/{id}): مشخصات، آخرین فعالیت، آمار وظایف و لیست وظایف.
 * دسترسی را سرور تعیین می‌کند: خودِ کاربر یا مدیر وظایف/ادمین؛ برای بقیه «پیدا نشد».
 * ارزیابی عملکرد مستقل از آمار وظایف است و فقط برای خود فرد و ادمین بارگذاری می‌شود.
 */

/** آیکون «شخص» (person) برای آواتار پروفایل و لینک هدر */
const PERSON_ICON_SVG =
    `<svg class="icon-person" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`;

const ProfileState = {
    userId: null,
    profile: null,
    quick: "open",
    page: 0,
    size: 10,
    seq: 0,
};

async function openProfile(userId) {
    const s = ProfileState;
    if (s.userId !== userId) {
        s.quick = "open";
        s.page = 0;
    }
    s.userId = userId;
    showSection("profile-section");
    setActiveNavButton(userId === AuthState.user?.id ? "" : canManageTasks() ? "users" : "");

    const root = document.getElementById("profile-root");
    const seq = ++s.seq;
    root.innerHTML = `<div class="task-state"><div class="loader-wrap">${loaderMarkup()}</div></div>`;

    try {
        const profile = await UserAPI.getProfile(userId);
        if (seq !== s.seq) return;
        s.profile = profile;
        renderProfile(profile);
        await loadProfileTasks();
    } catch (error) {
        if (seq !== s.seq) return;
        root.innerHTML = taskStateHtml({
            icon: "⚠",
            title: "پروفایل پیدا نشد",
            message: error.message,
            error: true,
            actionHtml: `<a class="btn btn-secondary" href="#/tasks/my">بازگشت به وظایف من</a>`,
        });
    }
}

function renderProfile(p) {
    const root = document.getElementById("profile-root");
    const stats = p.stats;
    const isSelf = AuthState.user && AuthState.user.id === p.id;

    const statCard = (value, label, { alert = false, note = "" } = {}) => `
        <div class="profile-stat ${alert ? "is-alert" : ""}">
            <strong>${value}</strong><span>${alert ? "⚠ " : ""}${label}</span>${note ? `<small>${note}</small>` : ""}
        </div>`;

    root.innerHTML = `
        <div class="profile-head">
            <div class="profile-avatar" aria-hidden="true">${PERSON_ICON_SVG}</div>
            <div class="profile-info">
                <h2 class="profile-name">${escapeHtml(p.fullName || p.username)}</h2>
                <div class="profile-meta">
                    <span><bdi dir="ltr">@${escapeHtml(p.username)}</bdi></span>
                    <span>نقش: ${escapeHtml(roleLabel(p.role))}</span>
                    <span>بخش: ${p.department ? escapeHtml(p.department) : "ثبت نشده"}</span>
                    <span>عضویت: ${escapeHtml(taskFormatDate(p.createdAt))}</span>
                    <span title="${escapeHtml(taskFormatDateTime(p.lastActivityAt) || "")}">آخرین فعالیت: ${escapeHtml(taskRelativePast(p.lastActivityAt))}</span>
                </div>
            </div>
            <div style="display:flex;flex-direction:column;gap:8px;align-items:flex-start">
                <span class="task-badge ${p.active ? "profile-active-on" : "profile-active-off"}">
                    <span aria-hidden="true">${p.active ? "●" : "○"}</span>${p.active ? "فعال" : "غیرفعال"}
                </span>
                ${isAdmin() ? `<button type="button" class="btn btn-secondary btn-sm" id="btn-profile-edit">ویرایش بخش و وضعیت</button>` : ""}
            </div>
        </div>

        ${
            !p.active
                ? `<div class="task-overdue-banner" role="status"><span aria-hidden="true">⚠</span>
                     این حساب غیرفعال است: نمی‌تواند وارد شود و وظیفه‌ی جدیدی دریافت نمی‌کند.</div>`
                : ""
        }

        <div class="profile-stats" aria-label="آمار وظایف">
            ${statCard(taskNumber(stats.openTasks), "وظیفه‌ی باز", {
                note: `${taskNumber(stats.inProgress)} در حال انجام · ${taskNumber(stats.waiting)} در انتظار`,
            })}
            ${statCard(taskNumber(stats.dueToday), "مهلت امروز")}
            ${statCard(taskNumber(stats.overdue), "دارای تأخیر", { alert: stats.overdue > 0 })}
            ${statCard(taskNumber(stats.completed), "تکمیل‌شده")}
            ${statCard(
                stats.completionRate === null || stats.completionRate === undefined ? "—" : `${taskNumber(stats.completionRate)}٪`,
                "نرخ تکمیل",
                {
                    note:
                        stats.completionRate === null || stats.completionRate === undefined
                            ? "هنوز وظیفه‌ای ثبت نشده"
                            : "تکمیل‌شده ÷ (تکمیل‌شده + باز)",
                }
            )}
        </div>

        ${isSelf ? `<div id="profile-signature-region"></div>` : ""}

        ${isSelf || isAdmin() ? `<section id="profile-performance" class="task-card" aria-label="ارزیابی عملکرد" aria-live="polite"></section>` : ""}

        <section class="task-card" aria-labelledby="profile-tasks-title">
            <h3 class="task-card-title" id="profile-tasks-title">${isSelf ? "وظایف من" : "وظایف این کاربر"}</h3>
            <div class="task-quick-tabs" id="profile-quick-tabs" role="group" aria-label="فیلتر سریع وظایف">
                <button type="button" class="task-chip" data-quick="open" aria-pressed="${ProfileState.quick === "open"}">باز</button>
                <button type="button" class="task-chip" data-quick="overdue" aria-pressed="${ProfileState.quick === "overdue"}">دارای تأخیر</button>
                <button type="button" class="task-chip" data-quick="done" aria-pressed="${ProfileState.quick === "done"}">تکمیل‌شده</button>
                <button type="button" class="task-chip" data-quick="all" aria-pressed="${ProfileState.quick === "all"}">همه</button>
            </div>
            <div id="profile-tasks-region" aria-live="polite"></div>
            <div class="task-pagination" id="profile-tasks-pagination"></div>
        </section>`;

    document.querySelectorAll("#profile-quick-tabs .task-chip").forEach((btn) =>
        btn.addEventListener("click", () => {
            ProfileState.quick = btn.dataset.quick;
            ProfileState.page = 0;
            document.querySelectorAll("#profile-quick-tabs .task-chip").forEach((b) =>
                b.setAttribute("aria-pressed", String(b === btn))
            );
            loadProfileTasks();
        })
    );
    document.getElementById("btn-profile-edit")?.addEventListener("click", () => openProfileEditModal(p));
    if (isSelf && typeof renderProfileSignatureCard === "function") renderProfileSignatureCard();
    if ((isSelf || isAdmin()) && typeof mountPerformance === "function") mountPerformance(p.id);
}

async function loadProfileTasks() {
    const s = ProfileState;
    const region = document.getElementById("profile-tasks-region");
    const pagination = document.getElementById("profile-tasks-pagination");
    if (!region) return;
    const seq = ++s.seq;

    region.innerHTML = `<div class="loader-wrap">${loaderMarkup()}</div>`;
    pagination.innerHTML = "";

    const params = { page: s.page, size: s.size };
    if (["open", "overdue"].includes(s.quick)) params.status = TASK_OPEN_STATUSES;
    if (s.quick === "overdue") params.overdue = true;
    if (s.quick === "done") params.status = ["COMPLETED"];

    try {
        const result = await UserAPI.getTasks(s.userId, params);
        if (seq !== s.seq) return;
        if (!result.items.length) {
            region.innerHTML = `<p class="task-muted" style="padding:16px 0">وظیفه‌ای برای نمایش وجود ندارد.</p>`;
            return;
        }
        region.innerHTML = `
            <div class="table-wrap">
                <table class="data-table task-table task-table-my">
                    <thead><tr>
                        <th scope="col">عنوان</th><th scope="col">وضعیت</th><th scope="col">اولویت</th>
                        <th scope="col" class="task-col-assignee">مسئول</th><th scope="col">مهلت</th>
                    </tr></thead>
                    <tbody>${result.items.map(taskRowHtml).join("")}</tbody>
                </table>
            </div>`;
        region.querySelectorAll("tr.task-row").forEach((row) =>
            row.addEventListener("click", (e) => {
                if (e.target.closest("a, button")) return;
                taskNavigate(`#/tasks/${row.dataset.taskId}`);
            })
        );
        pagination.innerHTML = `
            <span>${taskNumber(result.totalElements)} وظیفه</span>
            <div class="task-pagination-controls">
                <button type="button" class="btn btn-secondary btn-sm" id="btn-profile-prev" ${result.page <= 0 ? "disabled" : ""}>قبلی</button>
                <span>صفحه ${taskNumber(result.page + 1)} از ${taskNumber(Math.max(result.totalPages, 1))}</span>
                <button type="button" class="btn btn-secondary btn-sm" id="btn-profile-next" ${result.page + 1 >= result.totalPages ? "disabled" : ""}>بعدی</button>
            </div>`;
        document.getElementById("btn-profile-prev").addEventListener("click", () => {
            s.page = Math.max(0, s.page - 1);
            loadProfileTasks();
        });
        document.getElementById("btn-profile-next").addEventListener("click", () => {
            s.page += 1;
            loadProfileTasks();
        });
    } catch (error) {
        if (seq !== s.seq) return;
        region.innerHTML = `<p class="task-muted" role="alert">${escapeHtml(error.message)}</p>`;
    }
}

function openProfileEditModal(profile) {
    openModal(
        "ویرایش پروفایل",
        `<form id="form-profile-edit" novalidate>
            <div class="form-group">
                <label for="profile-edit-department">بخش / واحد</label>
                <input type="text" id="profile-edit-department" maxlength="100" value="${escapeHtml(profile.department || "")}" />
            </div>
            <label class="permission-checkbox">
                <input type="checkbox" id="profile-edit-active" ${profile.active ? "checked" : ""} />
                <span>حساب فعال است</span>
            </label>
            <div class="task-form-hint">با غیرفعال‌کردن، کاربر نمی‌تواند وارد شود و وظیفه‌ی جدیدی به او محول نمی‌شود؛ وظایف و تاریخچه‌اش حفظ می‌شود.</div>
            <div class="form-error" id="profile-edit-error" role="alert"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-profile-edit-cancel">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>`
    );
    document.getElementById("btn-profile-edit-cancel").addEventListener("click", closeModal);
    document.getElementById("form-profile-edit").addEventListener("submit", async (e) => {
        e.preventDefault();
        const active = document.getElementById("profile-edit-active").checked;
        const department = document.getElementById("profile-edit-department").value;
        const errorBox = document.getElementById("profile-edit-error");

        if (profile.active && !active) {
            const answer = await taskConfirm({
                title: "غیرفعال‌کردن حساب",
                message: `حساب «${profile.fullName || profile.username}» غیرفعال می‌شود و دیگر نمی‌تواند وارد سیستم شود. ادامه می‌دهید؟`,
                confirmText: "غیرفعال شود",
                danger: true,
            });
            if (!answer.confirmed) return;
        }
        try {
            await UserAPI.updateProfile(profile.id, { department, active });
            closeModal();
            showToast("پروفایل به‌روزرسانی شد");
            openProfile(profile.id);
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
}

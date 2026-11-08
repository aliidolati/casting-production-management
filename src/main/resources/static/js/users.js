/**

 * users.js

 * فهرست کاربران فقط‌خواندنی است و مدیریت حساب‌ها به مدیر اختصاص دارد.

 * (نمایش/مخفی‌سازی دکمه‌ی ناوبری در auth.js انجام می‌شود).

 */



const ROLE_LABELS = {

    MANAGER: "مدیر",
    ADMIN: "ادمین",

    OPERATOR: "اپراتور",

};



/** دسترسی‌های پیش‌فرض اپراتور (هم‌راستا با بک‌اند) */

const DEFAULT_OPERATOR_PERMISSIONS = [

    "ADVANCE_PIECE_STAGE",
    "MOVE_PIECE_STAGE",

    "UPDATE_RADIOGRAPHY_SUBSTAGE",

    "UPDATE_HEAT_TREATMENT_SUBSTAGE",

    "MANAGE_RADIOGRAPHY_DEFECTS",

    "MANAGE_PIECE_NOTES",

    "MANAGE_WELDING_MAP",

    "MANAGE_PIECE_ASSIGNMENT",

    "RECEIVE_TASKS",

];



let cachedPermissionCatalog = null;



function roleLabel(role) {

    return ROLE_LABELS[role] || role;

}



async function loadPermissionCatalog() {

    if (cachedPermissionCatalog) return cachedPermissionCatalog;

    cachedPermissionCatalog = await PermissionAPI.getAll();

    return cachedPermissionCatalog;

}



function renderPermissionCheckboxes(selectedCodes, inputName = "user-permission") {

    const selected = new Set(selectedCodes || []);

    return cachedPermissionCatalog

        .map(

            (p) => `

        <label class="permission-checkbox">

            <input type="checkbox" name="${inputName}" value="${p.code}" ${selected.has(p.code) ? "checked" : ""} />

            <span>${p.label}</span>

        </label>

    `

        )

        .join("");

}



function readSelectedPermissions(form, inputName = "user-permission") {

    return [...form.querySelectorAll(`input[name="${inputName}"]:checked`)].map((el) => el.value);

}



function togglePermissionsSection(roleSelect, sectionEl) {

    const isOperator = roleSelect.value !== "MANAGER";

    sectionEl.style.display = isOperator ? "block" : "none";
    const correction = sectionEl.querySelector?.('input[value="CORRECT_PIECE_STAGE"]');
    if (correction) {
        correction.disabled = roleSelect.value === "OPERATOR";
        if (correction.disabled) correction.checked = false;
    }

}



async function loadUsers() {

    const tbody = document.getElementById("users-table-body");
    const manage = isManager();
    document.getElementById("users-title").textContent = manage ? "مدیریت کاربران" : "فهرست کاربران";
    document.getElementById("users-actions-heading").classList.toggle("hidden", !manage);
    showTableLoader(tbody, manage ? 5 : 4);

    try {

        const users = await (manage ? UserAPI.getAll() : UserAPI.directory());

        renderUsersTable(users);

    } catch (error) {

        showToast(error.message, "error");

    }

}



function renderUsersTable(users) {

    const tbody = document.getElementById("users-table-body");
    const manage = isManager();
    const canOpenOtherUser = canManageTasks() || hasPermission("EDIT_PERFORMANCE_SCORES");



    if (users.length === 0) {

        tbody.innerHTML = `<tr class="empty-row"><td colspan="${manage ? 5 : 4}">هنوز هیچ کاربری ثبت نشده است</td></tr>`;

        return;

    }



    tbody.innerHTML = users

        .map(

            (u) => `

        <tr data-user-id="${u.id}">

            <td>${canOpenOtherUser || u.id === AuthState.user?.id
                ? `<a href="#/profile/${u.id}">${escapeHtml(u.username)}</a>`
                : escapeHtml(u.username)}${u.active === false ? ` <span class="task-muted">(غیرفعال)</span>` : ""}</td>

            <td>${escapeHtml(u.fullName || "-")}</td>

            <td>${roleLabel(u.role)}</td>

            <td>${formatDate(u.createdAt)}</td>

            ${manage ? `<td class="row-actions">

                ${

                    u.role !== "MANAGER"

                        ? `<button class="btn btn-secondary btn-sm btn-edit-permissions" data-id="${u.id}" data-username="${escapeHtml(u.username)}">دسترسی‌ها</button>`

                        : ""

                }

                <button class="btn btn-secondary btn-sm btn-edit-account" data-id="${u.id}">نام و نقش</button>
                <button class="btn btn-secondary btn-sm btn-access-history" data-id="${u.id}">سابقه تغییرات</button>
                <button class="btn btn-secondary btn-sm btn-reset-password" data-id="${u.id}" data-username="${escapeHtml(u.username)}">تغییر رمز</button>

                <button class="btn btn-danger btn-delete-user" data-id="${u.id}">حذف</button>

            </td>` : ""}

        </tr>

    `

        )

        .join("");



    tbody.querySelectorAll(".btn-delete-user").forEach((btn) => {

        btn.addEventListener("click", async () => {

            if (!confirm("آیا از حذف این کاربر مطمئن هستید؟")) return;



            try {

                await UserAPI.delete(btn.dataset.id);

                showToast("کاربر با موفقیت حذف شد");

                loadUsers();

            } catch (error) {

                showToast(error.message, "error");

            }

        });

    });



    tbody.querySelectorAll(".btn-edit-permissions").forEach((btn) => {

        btn.addEventListener("click", () => openEditPermissionsModal(btn.dataset.id, btn.dataset.username));

    });

    tbody.querySelectorAll(".btn-edit-account").forEach((btn) => {
        btn.addEventListener("click", () => openEditUserAccount(users.find(u => u.id === btn.dataset.id)));
    });
    tbody.querySelectorAll(".btn-access-history").forEach((btn) => {
        btn.addEventListener("click", () => openUserAccessHistory(btn.dataset.id));
    });

    tbody.querySelectorAll(".btn-reset-password").forEach((btn) => {

        btn.addEventListener("click", () => openAdminResetPasswordModal(btn.dataset.id, btn.dataset.username));

    });

}



/* ===========================

   تغییر رمز عبور کاربر توسط ادمین (مودال)

   =========================== */

function openAdminResetPasswordModal(userId, username) {
    openModal(
        `تغییر رمز عبور — ${username}`,
        `
        <form id="form-admin-reset-password">
            <p class="form-hint">رمز عبور جدید برای این کاربر تنظیم می‌شود؛ نیازی به دانستن رمز فعلی نیست.</p>
            <div class="form-group">
                <label for="input-admin-new-password">رمز عبور جدید</label>
                <input type="password" id="input-admin-new-password" placeholder="حداقل ۶ کاراکتر" required autocomplete="new-password" />
            </div>
            <div class="form-group">
                <label for="input-admin-confirm-password">تکرار رمز عبور جدید</label>
                <input type="password" id="input-admin-confirm-password" required autocomplete="new-password" />
            </div>
            <div class="form-error" id="form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-admin-reset-password">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
        `
    );

    document.getElementById("btn-cancel-admin-reset-password").addEventListener("click", closeModal);

    document.getElementById("form-admin-reset-password").addEventListener("submit", async (e) => {
        e.preventDefault();

        const newPassword = document.getElementById("input-admin-new-password").value;
        const confirmPassword = document.getElementById("input-admin-confirm-password").value;
        const errorBox = document.getElementById("form-error");

        if (newPassword !== confirmPassword) {
            errorBox.textContent = "رمز عبور جدید و تکرار آن یکسان نیستند";
            errorBox.style.display = "block";
            return;
        }

        try {
            await UserAPI.resetPassword(userId, newPassword);
            closeModal();
            showToast("رمز عبور کاربر با موفقیت تغییر کرد");
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
}

/* ===========================

   ویرایش دسترسی‌های اپراتور (مودال)

   =========================== */



async function openEditPermissionsModal(userId, username) {

    try {

        await loadPermissionCatalog();

        const users = await UserAPI.getAll();

        const user = users.find((u) => u.id === userId);

        if (!user) {

            showToast("کاربر پیدا نشد", "error");

            return;

        }



        openModal(

            `دسترسی‌های ${username}`,

            `

            <form id="form-edit-permissions">

                <p class="form-hint">مجوزهای ادمین و اپراتور توسط مدیر تعیین می‌شوند.</p>

                <div class="permissions-grid" id="permissions-grid-edit">

                    ${renderPermissionCheckboxes(user.permissions)}

                </div>

                <div class="form-error" id="form-error"></div>

                <div class="form-actions">

                    <button type="button" class="btn btn-secondary" id="btn-cancel-edit-permissions">انصراف</button>

                    <button type="submit" class="btn btn-primary">ذخیره</button>

                </div>

            </form>

        `

        );



        togglePermissionsSection({ value: user.role }, document.getElementById("permissions-grid-edit"));
        document.getElementById("btn-cancel-edit-permissions").addEventListener("click", closeModal);



        document.getElementById("form-edit-permissions").addEventListener("submit", async (e) => {

            e.preventDefault();

            const errorBox = document.getElementById("form-error");

            const permissions = readSelectedPermissions(e.target);



            try {

                await UserAPI.updatePermissions(userId, permissions);

                closeModal();

                showToast("دسترسی‌ها با موفقیت به‌روزرسانی شد");

                loadUsers();

            } catch (error) {

                errorBox.textContent = error.message;

                errorBox.style.display = "block";

            }

        });

    } catch (error) {

        showToast(error.message, "error");

    }

}



/* ===========================

   ساخت کاربر جدید (مودال)

   =========================== */



document.getElementById("btn-add-user").addEventListener("click", async () => {
    if (!isManager()) return;

    try {

        await loadPermissionCatalog();



        openModal(

            "افزودن کاربر جدید",

            `

            <form id="form-add-user">

                <div class="form-group">

                    <label for="input-user-username">نام کاربری</label>

                    <input type="text" id="input-user-username" placeholder="مثلاً ali.rezaei" required autocomplete="off" />

                </div>

                <div class="form-group">

                    <label for="input-user-fullname">نام و نام خانوادگی</label>

                    <input type="text" id="input-user-fullname" placeholder="مثلاً علی رضایی" required />

                </div>

                <div class="form-group">

                    <label for="input-user-password">رمز عبور</label>

                    <input type="password" id="input-user-password" placeholder="حداقل ۶ کاراکتر" required autocomplete="new-password" />

                </div>

                <div class="form-group">

                    <label for="input-user-role">نقش</label>

                    <select id="input-user-role" required>

                        <option value="OPERATOR">اپراتور</option>

                        <option value="ADMIN">ادمین</option>
                        <option value="MANAGER">مدیر</option>

                    </select>

                </div>

                <div class="form-group" id="permissions-section">

                    <label>دسترسی‌های ادمین یا اپراتور</label>

                    <div class="permissions-grid" id="permissions-grid-create">

                        ${renderPermissionCheckboxes(DEFAULT_OPERATOR_PERMISSIONS)}

                    </div>

                </div>

                <div class="form-error" id="form-error"></div>

                <div class="form-actions">

                    <button type="button" class="btn btn-secondary" id="btn-cancel-add-user">انصراف</button>

                    <button type="submit" class="btn btn-primary">ذخیره</button>

                </div>

            </form>

        `

        );



        const roleSelect = document.getElementById("input-user-role");

        const permissionsSection = document.getElementById("permissions-section");

        togglePermissionsSection(roleSelect, permissionsSection);

        roleSelect.addEventListener("change", () => togglePermissionsSection(roleSelect, permissionsSection));



        document.getElementById("btn-cancel-add-user").addEventListener("click", closeModal);



        document.getElementById("form-add-user").addEventListener("submit", async (e) => {

            e.preventDefault();



            const username = document.getElementById("input-user-username").value.trim();

            const fullName = document.getElementById("input-user-fullname").value.trim();

            const password = document.getElementById("input-user-password").value;

            const role = document.getElementById("input-user-role").value;

            const errorBox = document.getElementById("form-error");



            const payload = { username, fullName, password, role };

            if (role !== "MANAGER") {

                payload.permissions = readSelectedPermissions(e.target);

            }



            try {

                await UserAPI.create(payload);

                closeModal();

                showToast("کاربر با موفقیت ساخته شد");

                loadUsers();

            } catch (error) {

                errorBox.textContent = error.message;

                errorBox.style.display = "block";

            }

        });

    } catch (error) {

        showToast(error.message, "error");

    }

});



async function openEditUserAccount(user) {
    if (!user || !isManager()) return;
    try {
        await loadPermissionCatalog();
        openModal("ویرایش نام و نقش کاربر", `
            <form id="form-edit-account">
                <p>نام کاربری: ${escapeHtml(user.username)}</p>
                <div class="form-group"><label for="account-full-name">نام و نام خانوادگی</label>
                    <input id="account-full-name" value="${escapeHtml(user.fullName || "")}" maxlength="255" required /></div>
                <div class="form-group"><label for="account-role">نقش</label>
                    <select id="account-role">${["MANAGER", "ADMIN", "OPERATOR"].map(role =>
                        `<option value="${role}" ${role === user.role ? "selected" : ""}>${roleLabel(role)}</option>`).join("")}</select></div>
                <p class="form-hint">مدیر همه دسترسی‌ها را دارد. برای ادمین و اپراتور، مجوزها را انتخاب کنید.</p>
                <div id="account-permissions" class="permissions-grid">${renderPermissionCheckboxes(user.role === "MANAGER" ? [] : user.permissions)}</div>
                <div id="account-error" class="form-error"></div>
                <div class="form-actions"><button type="button" class="btn btn-secondary" id="account-cancel">انصراف</button>
                    <button type="submit" class="btn btn-primary">ذخیره</button></div>
            </form>`);
        const role = document.getElementById("account-role");
        const permissions = document.getElementById("account-permissions");
        togglePermissionsSection(role, permissions);
        role.addEventListener("change", () => togglePermissionsSection(role, permissions));
        document.getElementById("account-cancel").addEventListener("click", closeModal);
        document.getElementById("form-edit-account").addEventListener("submit", async e => {
            e.preventDefault();
            const button = e.target.querySelector('[type="submit"]');
            button.disabled = true;
            try {
                const saved = await UserAPI.updateAccount(user.id, {
                    fullName: document.getElementById("account-full-name").value.trim(),
                    role: role.value, permissions: role.value === "MANAGER" ? [] : readSelectedPermissions(e.target),
                });
                closeModal();
                showToast("نام و نقش کاربر به‌روزرسانی شد");
                if (AuthState.user && AuthState.user.id === saved.id) {
                    // Role/grant changes revoke this session. A name-only edit keeps it valid.
                    if (saved.role !== AuthState.user.role) {
                        clearSession(); showLoginScreen("نقش شما تغییر کرد؛ دوباره وارد شوید."); return;
                    }
                    AuthState.user.fullName = saved.fullName;
                    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(AuthState.user)); renderCurrentUser();
                }
                await loadUsers();
            } catch (error) {
                const box = document.getElementById("account-error");
                if (box) { box.textContent = error.message; box.style.display = "block"; }
            } finally { button.disabled = false; }
        });
    } catch (error) { showToast(error.message, "error"); }
}

function accessSnapshotText(value) {
    const data = JSON.parse(value);
    if (!data.username) return "—";
    const labels = new Map((cachedPermissionCatalog || []).map(p => [p.code, p.label]));
    const permissions = (data.permissions || []).map(code => code === "ALL" ? "همه دسترسی‌ها" : labels.get(code) || code);
    return `${data.fullName || data.username} | ${roleLabel(data.role)} | ${data.active ? "فعال" : "غیرفعال"} | ${data.department || "بدون بخش"} | ${permissions.join("، ") || "بدون مجوز"}`;
}

async function openUserAccessHistory(id, page = 0) {
    try {
        await loadPermissionCatalog();
        const data = await UserAPI.accessHistory(id, page);
        const actions = { CREATED: "ساخت کاربر", ACCOUNT_UPDATED: "تغییر نام یا نقش", PERMISSIONS_CHANGED: "تغییر مجوزها", PROFILE_UPDATED: "تغییر بخش یا وضعیت", DELETED: "حذف حساب", INITIAL_MANAGER_SELECTED: "تعیین مدیر اولیه" };
        openModal("سابقه تغییرات کاربر", `<div class="task-list">${data.items.map(event => `
            <article class="task-card"><p>${escapeHtml(actions[event.action] || event.action)} — ${escapeHtml(event.actorUsername)} — ${escapeHtml(formatDate(event.occurredAt))}</p>
                <p>قبل: ${escapeHtml(accessSnapshotText(event.beforeState))}</p>
                <p>بعد: ${escapeHtml(accessSnapshotText(event.afterState))}</p></article>`).join("") || "هنوز تغییری ثبت نشده است."}</div>
            <div class="form-actions"><button type="button" class="btn btn-secondary" id="access-history-prev" ${page === 0 ? "disabled" : ""}>قبلی</button>
                <span>${page + 1} / ${Math.max(1, data.totalPages)}</span>
                <button type="button" class="btn btn-secondary" id="access-history-next" ${page + 1 >= data.totalPages ? "disabled" : ""}>بعدی</button></div>`);
        document.getElementById("access-history-prev").addEventListener("click", () => openUserAccessHistory(id, page - 1));
        document.getElementById("access-history-next").addEventListener("click", () => openUserAccessHistory(id, page + 1));
    } catch (error) { showToast(error.message, "error"); }
}

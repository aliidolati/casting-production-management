/**

 * users.js

 * منطق مربوط به بخش «مدیریت کاربران»: فقط برای کاربران با نقش ADMIN قابل مشاهده است

 * (نمایش/مخفی‌سازی دکمه‌ی ناوبری در auth.js انجام می‌شود).

 */



const ROLE_LABELS = {

    ADMIN: "مدیر",

    OPERATOR: "اپراتور",

};



/** دسترسی‌های پیش‌فرض اپراتور (هم‌راستا با بک‌اند) */

const DEFAULT_OPERATOR_PERMISSIONS = [

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

    const isOperator = roleSelect.value === "OPERATOR";

    sectionEl.style.display = isOperator ? "block" : "none";

}



async function loadUsers() {

    const tbody = document.getElementById("users-table-body");
    showTableLoader(tbody, 5);

    try {

        const users = await UserAPI.getAll();

        renderUsersTable(users);

    } catch (error) {

        showToast(error.message, "error");

    }

}



function renderUsersTable(users) {

    const tbody = document.getElementById("users-table-body");



    if (users.length === 0) {

        tbody.innerHTML = `<tr class="empty-row"><td colspan="5">هنوز هیچ کاربری ثبت نشده است</td></tr>`;

        return;

    }



    tbody.innerHTML = users

        .map(

            (u) => `

        <tr data-user-id="${u.id}">

            <td><a href="#/profile/${u.id}" title="مشاهده پروفایل و وظایف">${escapeHtml(u.username)}</a>${u.active === false ? ` <span class="task-muted">(غیرفعال)</span>` : ""}</td>

            <td>${u.fullName || "-"}</td>

            <td>${roleLabel(u.role)}</td>

            <td>${formatDate(u.createdAt)}</td>

            <td class="row-actions">

                ${

                    u.role === "OPERATOR"

                        ? `<button class="btn btn-secondary btn-sm btn-edit-permissions" data-id="${u.id}" data-username="${escapeHtml(u.username)}">دسترسی‌ها</button>`

                        : ""

                }

                <button class="btn btn-secondary btn-sm btn-reset-password" data-id="${u.id}" data-username="${escapeHtml(u.username)}">تغییر رمز</button>

                <button class="btn btn-danger btn-delete-user" data-id="${u.id}">حذف</button>

            </td>

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

                <p class="form-hint">ادمین همیشه همه دسترسی‌ها را دارد؛ این تنظیمات فقط برای اپراتور معنا دارد.</p>

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

                        <option value="ADMIN">مدیر</option>

                    </select>

                </div>

                <div class="form-group" id="permissions-section">

                    <label>دسترسی‌های اپراتور</label>

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

            if (role === "OPERATOR") {

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


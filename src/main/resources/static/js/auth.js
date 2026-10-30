/**
 * auth.js
 * مدیریت صفحه‌ی ورود، ذخیره/پاک‌سازی نشست کاربر در localStorage،
 * بازیابی خودکار نشست هنگام بارگذاری صفحه، و خروج از حساب.
 */

const AUTH_USER_KEY = "pm_auth_user";

const AuthState = {
    user: null, // { username, fullName, role, permissions: string[] }
};

function hasPermission(code) {
    if (!AuthState.user) return false;
    if (AuthState.user.role === "ADMIN") return true;
    const perms = AuthState.user.permissions || [];
    return perms.includes(code);
}

function isAdmin() {
    return !!AuthState.user && AuthState.user.role === "ADMIN";
}

/** آیا کاربر حداقل یکی از دسترسی‌های عملیات دسته‌جمعی را دارد؟ */
function canUseBulkSelection() {
    return (
        hasPermission("MOVE_PIECE_STAGE") ||
        hasPermission("UPDATE_RADIOGRAPHY_SUBSTAGE") ||
        hasPermission("UPDATE_HEAT_TREATMENT_SUBSTAGE") ||
        hasPermission("UPDATE_ROUGH_MACHINING_SUBSTAGE")
    );
}

function saveSession(token, user) {
    localStorage.setItem(AUTH_TOKEN_KEY, token);
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
    AuthState.user = user;
}

function clearSession() {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(AUTH_USER_KEY);
    AuthState.user = null;
}

function showLoginScreen(message) {
    document.getElementById("app-shell").classList.add("hidden");
    document.getElementById("login-screen").classList.remove("hidden");
    if (typeof teardownNotificationCenter === "function") {
        teardownNotificationCenter();
    }

    const errorBox = document.getElementById("login-error");
    const usernameInput = document.getElementById("input-login-username");
    const passwordInput = document.getElementById("input-login-password");
    if (message) {
        errorBox.textContent = message;
        usernameInput.setAttribute("aria-invalid", "true");
        passwordInput.setAttribute("aria-invalid", "true");
        usernameInput.setAttribute("aria-describedby", "login-error");
        passwordInput.setAttribute("aria-describedby", "login-error");
    } else {
        errorBox.textContent = "";
        usernameInput.removeAttribute("aria-invalid");
        passwordInput.removeAttribute("aria-invalid");
        usernameInput.removeAttribute("aria-describedby");
        passwordInput.removeAttribute("aria-describedby");
    }

    document.getElementById("form-login").reset();
    if (message) usernameInput.focus();
}

// شناسه‌ی دکمه‌هایی که بر اساس دسترسی نمایش داده می‌شوند
const PERMISSION_BUTTONS = [
    { id: "nav-btn-dashboard", permission: "VIEW_DASHBOARD" },
    { id: "nav-btn-users", permission: null, adminOnly: true },
    { id: "nav-btn-tasks-all", permission: "MANAGE_TASKS" },
    { id: "btn-add-project", permission: "MANAGE_PROJECTS" },
    { id: "btn-add-definition", permission: "MANAGE_PIECE_DEFINITIONS" },
    { id: "btn-add-piece", permission: "MANAGE_PIECES" },
    { id: "btn-bulk-add-piece", permission: "MANAGE_PIECES" },
    { id: "btn-archive-all-definition-pieces", permission: "MANAGE_PIECES" },
    { id: "btn-archive-all-project-pieces", permission: "MANAGE_PIECES" },
];

function shouldShowButton({ permission, adminOnly }) {
    if (adminOnly) return isAdmin();
    return hasPermission(permission);
}

function renderCurrentUser() {
    const box = document.getElementById("current-user-info");
    if (!box || !AuthState.user) return;

    const roleText = AuthState.user.role === "ADMIN" ? "مدیر" : "اپراتور";
    const displayName = AuthState.user.fullName || AuthState.user.username;
    if (AuthState.user.id) {
        // نام کاربر به پروفایل خودش لینک می‌شود
        box.innerHTML = `<a class="current-user-link" href="#/profile/${encodeURIComponent(AuthState.user.id)}" title="پروفایل من">${typeof PERSON_ICON_SVG === "string" ? PERSON_ICON_SVG : ""}${escapeHtml(displayName)}</a> (${roleText})`;
    } else {
        box.textContent = `${displayName} (${roleText})`;
    }

    PERMISSION_BUTTONS.forEach(({ id, ...rule }) => {
        const el = document.getElementById(id);
        if (el) el.classList.toggle("hidden", !shouldShowButton(rule));
    });

    // ستون چک‌باکس و نوار عملیات دسته‌جمعی در ردیابی
    const selectAllTh = document.querySelector("#tracking-table thead .checkbox-cell");
    if (selectAllTh) selectAllTh.style.display = canUseBulkSelection() ? "" : "none";
    const bulkBar = document.getElementById("bulk-actions-bar");
    if (bulkBar && !canUseBulkSelection()) bulkBar.style.display = "none";

    const piecesSelectAllTh = document.getElementById("pieces-select-all-cell");
    if (piecesSelectAllTh) piecesSelectAllTh.style.display = hasPermission("MANAGE_PIECES") ? "" : "none";
    const piecesBulkBar = document.getElementById("pieces-bulk-actions-bar");
    if (piecesBulkBar && !hasPermission("MANAGE_PIECES")) piecesBulkBar.style.display = "none";

    if (typeof syncNotificationCenterVisibility === "function") {
        syncNotificationCenterVisibility();
    }
}

function showApp() {
    document.getElementById("login-screen").classList.add("hidden");
    document.getElementById("app-shell").classList.remove("hidden");

    renderCurrentUser();
    if (typeof taskResetLookupCache === "function") taskResetLookupCache();

    // لینک مستقیم به وظیفه/پروفایل (مثلاً از اعلان یا رفرش صفحه)
    if (typeof handleTaskRoute === "function" && handleTaskRoute()) return;

    // داشبورد فقط برای کسانی که VIEW_DASHBOARD دارند؛ بقیه مستقیم به ردیابی قطعات می‌روند
    if (hasPermission("VIEW_DASHBOARD")) {
        setActiveNavButton("dashboard");
        showSection("dashboard-section");
        loadDashboard();
    } else {
        setActiveNavButton("tracking");
        showSection("tracking-section");
        loadTracking();
    }
}

/**
 * هنگام بارگذاری صفحه، اگر توکنی ذخیره شده باشد اعتبارش را از سرور می‌پرسیم
 * (چون ممکن است منقضی شده باشد) و بر اساس نتیجه صفحه‌ی ورود یا اپ را نشان می‌دهیم.
 */
async function bootstrapAuth() {
    const token = localStorage.getItem(AUTH_TOKEN_KEY);

    if (!token) {
        showLoginScreen();
        return;
    }

    try {
        const user = await AuthAPI.me();
        AuthState.user = user;
        localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
        showApp();
    } catch (error) {
        clearSession();
        showLoginScreen();
    }
}

/* ===========================
   تغییر رمز عبور کاربر جاری (مودال)
   =========================== */

function openChangePasswordModal() {
    openModal(
        "تغییر رمز عبور",
        `
        <form id="form-change-password">
            <div class="form-group">
                <label for="input-current-password">رمز عبور فعلی</label>
                <input type="password" id="input-current-password" required autocomplete="current-password" />
            </div>
            <div class="form-group">
                <label for="input-new-password">رمز عبور جدید</label>
                <input type="password" id="input-new-password" placeholder="حداقل ۶ کاراکتر" required autocomplete="new-password" />
            </div>
            <div class="form-group">
                <label for="input-confirm-password">تکرار رمز عبور جدید</label>
                <input type="password" id="input-confirm-password" required autocomplete="new-password" />
            </div>
            <div class="form-error" id="form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-change-password">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
    `
    );

    document.getElementById("btn-cancel-change-password").addEventListener("click", closeModal);

    document.getElementById("form-change-password").addEventListener("submit", async (e) => {
        e.preventDefault();

        const currentPassword = document.getElementById("input-current-password").value;
        const newPassword = document.getElementById("input-new-password").value;
        const confirmPassword = document.getElementById("input-confirm-password").value;
        const errorBox = document.getElementById("form-error");

        if (newPassword !== confirmPassword) {
            errorBox.textContent = "رمز عبور جدید و تکرار آن یکسان نیستند";
            errorBox.style.display = "block";
            return;
        }

        try {
            await UserAPI.changeMyPassword({ currentPassword, newPassword });
            closeModal();
            clearSession();
            showLoginScreen("رمز عبور تغییر کرد؛ برای ادامه دوباره وارد حساب شوید");
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
}

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("btn-change-password").addEventListener("click", openChangePasswordModal);

    document.getElementById("form-login").addEventListener("submit", async (e) => {
        e.preventDefault();

        const username = document.getElementById("input-login-username").value.trim();
        const password = document.getElementById("input-login-password").value;
        const errorBox = document.getElementById("login-error");
        const submitBtn = document.getElementById("btn-login-submit");
        const usernameInput = document.getElementById("input-login-username");
        const passwordInput = document.getElementById("input-login-password");

        errorBox.textContent = "";
        usernameInput.removeAttribute("aria-invalid");
        passwordInput.removeAttribute("aria-invalid");
        usernameInput.removeAttribute("aria-describedby");
        passwordInput.removeAttribute("aria-describedby");

        await withButtonLoader(submitBtn, async () => {
            try {
                const result = await AuthAPI.login({ username, password });
                saveSession(result.token, {
                    id: result.id,
                    username: result.username,
                    fullName: result.fullName,
                    role: result.role,
                    permissions: result.permissions || [],
                });
                showApp();
            } catch (error) {
                errorBox.textContent = error.message;
                usernameInput.setAttribute("aria-invalid", "true");
                passwordInput.setAttribute("aria-invalid", "true");
                usernameInput.setAttribute("aria-describedby", "login-error");
                passwordInput.setAttribute("aria-describedby", "login-error");
                usernameInput.focus();
            }
        });
    });

    document.getElementById("btn-logout").addEventListener("click", () => {
        clearSession();
        showLoginScreen();
    });

    // وقتی api.js با پاسخ 401 مواجه شود این رویداد را پخش می‌کند
    window.addEventListener("auth:unauthorized", () => {
        clearSession();
        showLoginScreen("نشست شما منقضی شده است؛ دوباره وارد شوید");
    });

    bootstrapAuth();
});

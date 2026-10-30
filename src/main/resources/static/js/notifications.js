/**
 * notifications.js
 * زنگوله و صندوق اعلان (تاییدهای خودکار تست کشش و اعلان‌های وظایف).
 * برای همه‌ی کاربران نمایش داده می‌شود؛ سرور فقط اعلان‌های خودِ کاربر را برمی‌گرداند.
 */

const NOTIFICATION_POLL_MS = 45000;

const NotificationCenterState = {
    items: [],
    unreadCount: 0,
    panelOpen: false,
    pollTimer: null,
    loading: false,
};

function canSeeNotifications() {
    return Boolean(AuthState.user);
}

function syncNotificationCenterVisibility() {
    const root = document.getElementById("notification-center");
    if (!root) return;

    if (canSeeNotifications()) {
        root.classList.remove("hidden");
        startNotificationPolling();
        refreshNotificationCenter();
    } else {
        teardownNotificationCenter();
        root.classList.add("hidden");
    }
}

function startNotificationPolling() {
    if (NotificationCenterState.pollTimer) return;
    NotificationCenterState.pollTimer = setInterval(() => {
        if (!canSeeNotifications()) return;
        refreshNotificationCenter();
    }, NOTIFICATION_POLL_MS);
}

function teardownNotificationCenter() {
    if (NotificationCenterState.pollTimer) {
        clearInterval(NotificationCenterState.pollTimer);
        NotificationCenterState.pollTimer = null;
    }
    closeNotificationPanel();
    NotificationCenterState.items = [];
    NotificationCenterState.unreadCount = 0;
    renderNotificationBadge();
}

function renderNotificationBadge() {
    const badge = document.getElementById("notification-badge");
    if (!badge) return;
    const count = NotificationCenterState.unreadCount || 0;
    if (count <= 0) {
        badge.classList.add("hidden");
        badge.textContent = "0";
        return;
    }
    badge.classList.remove("hidden");
    badge.textContent = count > 99 ? "۹۹+" : count.toLocaleString("fa-IR");
}

function renderNotificationList() {
    const list = document.getElementById("notification-list");
    const readAllBtn = document.getElementById("btn-notifications-read-all");
    if (!list) return;

    if (readAllBtn) {
        readAllBtn.disabled = NotificationCenterState.unreadCount <= 0;
    }

    if (NotificationCenterState.loading && NotificationCenterState.items.length === 0) {
        list.innerHTML = `<div class="notification-empty">در حال بارگذاری…</div>`;
        return;
    }

    const items = NotificationCenterState.items;
    if (!items.length) {
        list.innerHTML = `<div class="notification-empty">اعلانی ثبت نشده است</div>`;
        return;
    }

    list.innerHTML = items.map((item) => {
        const unread = !item.read;
        return `
            <div class="notification-item${unread ? " unread" : ""}">
                <button type="button" class="notification-item-body" data-notification-id="${item.id}" data-piece-id="${item.pieceId || ""}" data-task-id="${item.taskId || ""}">
                    <div class="notification-item-text">${escapeHtml(item.message)}</div>
                    <div class="notification-item-meta">
                        <span>${formatDateTime(item.createdAt)}</span>
                        ${unread ? `<span class="notification-item-dot" aria-hidden="true"></span>` : ""}
                    </div>
                </button>
                <button type="button" class="notification-dismiss" data-notification-id="${item.id}" aria-label="پاک کردن اعلان">×</button>
            </div>
        `;
    }).join("");

    list.querySelectorAll(".notification-item-body").forEach((btn) => {
        btn.addEventListener("click", () => onNotificationItemClick(btn));
    });
    list.querySelectorAll(".notification-dismiss").forEach((btn) => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            dismissNotification(btn.dataset.notificationId);
        });
    });
}

async function refreshNotificationCenter() {
    if (!canSeeNotifications()) return;
    if (NotificationCenterState.loading) return;

    NotificationCenterState.loading = true;
    if (NotificationCenterState.panelOpen) {
        renderNotificationList();
    }

    try {
        const feed = await NotificationAPI.list(40);
        NotificationCenterState.items = feed.items || [];
        NotificationCenterState.unreadCount = feed.unreadCount || 0;
        renderNotificationBadge();
        if (NotificationCenterState.panelOpen) {
            renderNotificationList();
        }
    } catch (_) {
        if (NotificationCenterState.panelOpen) {
            const list = document.getElementById("notification-list");
            if (list && NotificationCenterState.items.length === 0) {
                list.innerHTML = `<div class="notification-error">بارگذاری اعلان‌ها ناموفق بود</div>`;
            }
        }
    } finally {
        NotificationCenterState.loading = false;
    }
}

function openNotificationPanel() {
    const panel = document.getElementById("notification-panel");
    const bell = document.getElementById("btn-notifications");
    if (!panel || !bell) return;
    panel.classList.remove("hidden");
    bell.setAttribute("aria-expanded", "true");
    NotificationCenterState.panelOpen = true;
    renderNotificationList();
    refreshNotificationCenter();
}

function closeNotificationPanel() {
    const panel = document.getElementById("notification-panel");
    const bell = document.getElementById("btn-notifications");
    if (panel) panel.classList.add("hidden");
    if (bell) bell.setAttribute("aria-expanded", "false");
    NotificationCenterState.panelOpen = false;
}

function toggleNotificationPanel() {
    if (NotificationCenterState.panelOpen) {
        closeNotificationPanel();
    } else {
        openNotificationPanel();
    }
}

async function onNotificationItemClick(btn) {
    const id = btn.dataset.notificationId;
    const pieceId = btn.dataset.pieceId;
    closeNotificationPanel();

    if (id) {
        try {
            await NotificationAPI.markRead(id);
            const item = NotificationCenterState.items.find((n) => n.id === id);
            if (item && !item.read) {
                item.read = true;
                NotificationCenterState.unreadCount = Math.max(0, NotificationCenterState.unreadCount - 1);
                renderNotificationBadge();
            }
        } catch (_) {
            /* باز کردن پروفایل مهم‌تر از علامت‌گذاری است */
        }
    }

    if (btn.dataset.taskId && typeof taskNavigate === "function") {
        taskNavigate(`#/tasks/${btn.dataset.taskId}`);
        return;
    }
    if (pieceId && typeof openPieceProfile === "function") {
        await openPieceProfile(pieceId);
        return;
    }
    showToast("مورد مربوط به این اعلان دیگر در دسترس نیست", "error");
}

async function dismissNotification(id) {
    if (!id) return;
    try {
        await NotificationAPI.dismiss(id);
        const item = NotificationCenterState.items.find((n) => n.id === id);
        NotificationCenterState.items = NotificationCenterState.items.filter((n) => n.id !== id);
        if (item && !item.read) {
            NotificationCenterState.unreadCount = Math.max(0, NotificationCenterState.unreadCount - 1);
        }
        renderNotificationBadge();
        renderNotificationList();
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function markAllNotificationsRead() {
    if (NotificationCenterState.unreadCount <= 0) return;
    try {
        await NotificationAPI.markAllRead();
        NotificationCenterState.items.forEach((item) => {
            item.read = true;
        });
        NotificationCenterState.unreadCount = 0;
        renderNotificationBadge();
        renderNotificationList();
    } catch (error) {
        showToast(error.message, "error");
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const bell = document.getElementById("btn-notifications");
    const readAllBtn = document.getElementById("btn-notifications-read-all");
    const center = document.getElementById("notification-center");

    if (bell) {
        bell.addEventListener("click", (e) => {
            e.stopPropagation();
            toggleNotificationPanel();
        });
    }
    if (readAllBtn) {
        readAllBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            markAllNotificationsRead();
        });
    }

    document.addEventListener("click", (e) => {
        if (!NotificationCenterState.panelOpen || !center) return;
        if (!center.contains(e.target)) {
            closeNotificationPanel();
        }
    });

    document.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && NotificationCenterState.panelOpen) {
            closeNotificationPanel();
        }
    });
});

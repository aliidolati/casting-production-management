/**
 * task-router.js
 * مسیریابی سبک مبتنی بر hash برای صفحات وظایف و پروفایل (برنامه‌ی اصلی router ندارد):
 *   #/tasks/my          وظایف من
 *   #/tasks             همه وظایف (برای مدیر وظایف/ادمین؛ برای بقیه همان «وظایف من»)
 *   #/tasks/{id}        جزئیات وظیفه
 *   #/profile/{userId}  پروفایل کاربر
 * query اختیاری: #/tasks?partId=...&projectId=...&assigneeId=...
 * لینک مستقیم، دکمه‌ی بازگشت مرورگر و رفرش صفحه همگی کار می‌کنند.
 */

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function parseTaskHash() {
    const raw = location.hash || "";
    if (!raw.startsWith("#/")) return null;
    const [path, queryString] = raw.slice(2).split("?");
    return {
        segs: path.split("/").filter(Boolean),
        query: Object.fromEntries(new URLSearchParams(queryString || "")),
    };
}

/** @returns {boolean} true اگر hash فعلی یکی از مسیرهای وظایف/پروفایل بود و رندر شد */
function handleTaskRoute() {
    if (!AuthState.user) return false;
    const route = parseTaskHash();
    if (!route || !route.segs.length) return false;
    const { segs, query } = route;

    if (segs[0] === "tasks") {
        if (segs.length === 1) {
            openTasksList(canManageTasks() ? "all" : "my", query);
        } else if (segs[1] === "my") {
            openTasksList("my", query);
        } else if (UUID_PATTERN.test(segs[1])) {
            openTaskDetail(segs[1]);
        } else {
            openTasksList("my", {});
        }
        return true;
    }
    if (segs[0] === "profile" && segs[1] && UUID_PATTERN.test(segs[1])) {
        openProfile(segs[1]);
        return true;
    }
    return false;
}

/** وقتی کاربر از منوی بخش‌های قدیمی می‌رود، hash وظایف نباید بماند */
function clearTaskHash() {
    if (location.hash) {
        history.replaceState(null, "", location.pathname + location.search);
    }
}

window.addEventListener("hashchange", () => {
    handleTaskRoute();
});

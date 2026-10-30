/**
 * theme-selector.js
 * ---------------------------------------------------------------------------
 * کامپوننت واحد «ظاهر برنامه» (Appearance).
 * هر عنصری با attribute `data-theme-selector` به یک دکمه + پنل انتخاب تم تبدیل می‌شود
 * (هدر برنامه و صفحه‌ی ورود هر دو از همین کامپوننت استفاده می‌کنند).
 *
 * فهرست تم‌ها، رنگ‌ها و پیش‌نمایش‌ها همه از ThemeManager/theme-definitions.js خوانده
 * می‌شود؛ افزودن تم جدید نیازی به تغییر این فایل ندارد.
 * ---------------------------------------------------------------------------
 */
(function (global) {
    "use strict";

    const LABEL = "ظاهر برنامه";
    const GROUPS = [
        { mode: "dark", label: "تیره" },
        { mode: "light", label: "روشن" },
    ];
    const SHEET_BREAKPOINT = 560;

    const ICON_PALETTE =
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<circle cx="13.5" cy="6.5" r="1.1"/><circle cx="17.5" cy="10.5" r="1.1"/><circle cx="8.5" cy="7.5" r="1.1"/><circle cx="6.5" cy="12.5" r="1.1"/>' +
        '<path d="M12 2a10 10 0 1 0 0 20c1.1 0 2-.9 2-2 0-.5-.2-1-.5-1.3-.3-.4-.5-.8-.5-1.3 0-1.1.9-2 2-2H18a4 4 0 0 0 4-4c0-5.5-4.5-9.4-10-9.4z"/></svg>';
    const ICON_CHECK =
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';

    let counter = 0;
    const instances = [];

    function esc(value) {
        return String(value).replace(/[&<>"']/g, (ch) => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
        })[ch]);
    }

    function cardMarkup(def) {
        const p = global.ThemeManager.preview(def.id);
        const vars = `--tp-bg:${p.bg};--tp-surface:${p.surface};--tp-text:${p.text};`
            + `--tp-border:${p.border};--tp-accent:${p.accent};--tp-accent2:${p.accent2}`;
        const swatches = [p.bg, p.surface, p.accent, p.accent2, p.success]
            .map((color) => `<i style="background:${color}"></i>`)
            .join("");
        return `
            <button type="button" class="theme-card" role="radio" aria-checked="false" tabindex="-1"
                    data-theme-id="${esc(def.id)}" style="${vars}">
                <span class="theme-preview" aria-hidden="true">
                    <span class="tp-bar"><i></i><i></i><i></i></span>
                    <span class="tp-body"><span class="tp-card"></span><span class="tp-card tp-card-accent"></span></span>
                </span>
                <span class="theme-card-meta">
                    <span class="theme-card-name">${esc(def.label)}</span>
                    <span class="theme-card-name-en">${esc(def.labelEn || def.id)}</span>
                </span>
                <span class="theme-swatches" aria-hidden="true">${swatches}</span>
                <span class="theme-card-check" aria-hidden="true">${ICON_CHECK}</span>
            </button>`;
    }

    function panelMarkup() {
        const themes = global.ThemeManager.list();
        const groups = GROUPS.map((group, index) => {
            const items = themes.filter((t) => t.mode === group.mode);
            if (!items.length) return "";
            const titleId = `theme-group-title-${counter}-${index}`;
            return `
                <section class="theme-group">
                    <h3 class="theme-group-title" id="${titleId}">${group.label}</h3>
                    <div class="theme-grid" role="radiogroup" aria-labelledby="${titleId}">
                        ${items.map(cardMarkup).join("")}
                    </div>
                </section>`;
        }).join("");
        return `
            <div class="theme-panel-header">
                <strong>${LABEL}</strong>
                <span class="theme-panel-hint">یک پوسته‌ی رنگی انتخاب کنید؛ بلافاصله اعمال و ذخیره می‌شود.</span>
            </div>
            <div class="theme-panel-body">${groups}</div>
            <span class="theme-sr-only" role="status" aria-live="polite"></span>`;
    }

    function mount(host) {
        if (host.dataset.themeSelectorMounted) return;
        host.dataset.themeSelectorMounted = "1";
        counter += 1;
        const panelId = `theme-panel-${counter}`;

        host.classList.add("theme-selector");
        host.innerHTML =
            `<button type="button" class="theme-toggle" aria-haspopup="dialog" aria-expanded="false"
                     aria-controls="${panelId}" aria-label="${LABEL}" title="${LABEL}">
                ${ICON_PALETTE}
                <span class="theme-toggle-label">ظاهر</span>
                <span class="theme-toggle-dot" aria-hidden="true"></span>
            </button>`;
        const toggle = host.querySelector(".theme-toggle");

        /* پنل به body منتقل می‌شود: هدر backdrop-filter دارد و position:fixed داخل آن
           نسبت به هدر (نه صفحه) محاسبه می‌شد. */
        const panel = document.createElement("div");
        panel.className = "theme-panel hidden";
        panel.id = panelId;
        panel.setAttribute("role", "dialog");
        panel.setAttribute("aria-label", LABEL);
        panel.innerHTML = panelMarkup();
        document.body.appendChild(panel);

        const cards = () => Array.from(panel.querySelectorAll(".theme-card"));
        const status = panel.querySelector(".theme-sr-only");
        let isOpen = false;
        let frame = 0;

        function sync() {
            const activeId = global.ThemeManager.currentId();
            cards().forEach((card) => {
                const selected = card.dataset.themeId === activeId;
                card.setAttribute("aria-checked", selected ? "true" : "false");
                card.tabIndex = selected ? 0 : -1;
            });
        }

        function position() {
            if (global.innerWidth <= SHEET_BREAKPOINT) {
                /* در موبایل پنل با CSS به‌صورت bottom sheet نمایش داده می‌شود */
                panel.style.left = "";
                panel.style.top = "";
                panel.style.maxHeight = "";
                return;
            }
            const rect = toggle.getBoundingClientRect();
            const vw = global.innerWidth;
            const vh = global.innerHeight;
            const width = Math.min(panel.offsetWidth || 400, vw - 24);
            const rtl = global.getComputedStyle(document.documentElement).direction === "rtl";
            let left = rtl ? rect.left : rect.right - width;
            left = Math.max(12, Math.min(left, vw - width - 12));
            const top = Math.max(12, Math.min(rect.bottom + 8, vh - 160));
            panel.style.left = `${left}px`;
            panel.style.top = `${top}px`;
            panel.style.maxHeight = `${vh - top - 12}px`;
        }

        function schedulePosition() {
            if (!isOpen || frame) return;
            frame = global.requestAnimationFrame(() => {
                frame = 0;
                position();
            });
        }

        function onScroll(event) {
            if (panel.contains(event.target)) return;
            schedulePosition();
        }

        function open() {
            if (isOpen) return;
            instances.forEach((other) => { if (other !== api) other.close(); });
            isOpen = true;
            panel.classList.remove("hidden");
            toggle.setAttribute("aria-expanded", "true");
            sync();
            position();
            const target = panel.querySelector('.theme-card[aria-checked="true"]') || cards()[0];
            if (target) target.focus({ preventScroll: true });
            global.addEventListener("resize", schedulePosition);
            global.addEventListener("scroll", onScroll, true);
        }

        function close(returnFocus) {
            if (!isOpen) return;
            isOpen = false;
            panel.classList.add("hidden");
            toggle.setAttribute("aria-expanded", "false");
            global.removeEventListener("resize", schedulePosition);
            global.removeEventListener("scroll", onScroll, true);
            if (returnFocus) toggle.focus();
        }

        function select(id) {
            const def = global.ThemeManager.get(id);
            if (!def) return;
            global.ThemeManager.apply(id, { persist: true, animate: true });
            if (status) status.textContent = `تم «${def.label}» اعمال شد`;
        }

        toggle.addEventListener("click", (event) => {
            event.stopPropagation();
            if (isOpen) close(false);
            else open();
        });

        panel.addEventListener("click", (event) => {
            const card = event.target instanceof Element ? event.target.closest(".theme-card") : null;
            if (card) select(card.dataset.themeId);
        });

        panel.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                event.stopPropagation();
                close(true);
                return;
            }
            const navKeys = ["ArrowDown", "ArrowUp", "ArrowRight", "ArrowLeft", "Home", "End"];
            if (!navKeys.includes(event.key)) return;
            const list = cards();
            const index = list.indexOf(document.activeElement);
            if (index < 0) return;
            event.preventDefault();
            const rtl = global.getComputedStyle(document.documentElement).direction === "rtl";
            const forwardKey = rtl ? "ArrowLeft" : "ArrowRight";
            let next = index;
            if (event.key === "Home") next = 0;
            else if (event.key === "End") next = list.length - 1;
            else {
                const forward = event.key === "ArrowDown" || event.key === forwardKey;
                next = (index + (forward ? 1 : -1) + list.length) % list.length;
            }
            list[next].focus();
            select(list[next].dataset.themeId);
        });

        document.addEventListener("click", (event) => {
            if (!isOpen) return;
            if (panel.contains(event.target) || host.contains(event.target)) return;
            close(false);
        });

        document.addEventListener("themechange", () => {
            sync();
        });

        const api = { close, open, host };
        instances.push(api);
        sync();
    }

    function init() {
        if (!global.ThemeManager) return;
        document.querySelectorAll("[data-theme-selector]").forEach(mount);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }

    global.ThemeSelector = { mount, init };
})(window);

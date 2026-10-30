/**
 * theme-manager.js
 * ---------------------------------------------------------------------------
 * موتور سیستم تم:
 *   - تعریف‌های theme-definitions.js را به توکن‌های CSS (متغیرهای semantic) تبدیل می‌کند
 *   - تم را روی <html> اعمال می‌کند (بدون بارگذاری مجدد صفحه)
 *   - انتخاب کاربر را در localStorage نگه می‌دارد و مقدارهای نامعتبر را نادیده می‌گیرد
 *   - رویداد `themechange` را برای کامپوننت‌هایی مثل نمودار canvas منتشر می‌کند
 *
 * این فایل باید در <head> و قبل از اولین رنگ‌آمیزی صفحه بارگذاری شود تا
 * تم ذخیره‌شده بدون چشمک زدن (FOUC) اعمال شود.
 *
 * API عمومی: window.ThemeManager
 *   list(), get(id), currentId(), current(), apply(id, opts), preview(id),
 *   cssVar(name, fallback), audit(opts)
 * ---------------------------------------------------------------------------
 */
(function (global) {
    "use strict";

    const STORAGE_KEY = "pm_theme";
    const HEX_RE = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i;
    const INK = "#0d0e11";

    /* ------------------------------------------------------------------
       پالت‌های مشترک هر حالت (dark/light).
       معنای رنگ‌های وضعیت بین تم‌ها ثابت است؛ فقط سایه‌ی آن‌ها با حالت عوض می‌شود.
       ------------------------------------------------------------------ */
    const STATUS = {
        dark: {
            success: "#4fbf8b",
            warning: "#e8b04a",
            danger: "#f0736f",
            info: "#5aa9f5",
            teal: "#45c2b5",
            coral: "#ee8a6a",
        },
        light: {
            success: "#047857",
            warning: "#b45309",
            danger: "#c23b3b",
            info: "#2563eb",
            teal: "#0f766e",
            coral: "#c2410c",
        },
    };

    /* رنگ نقطه‌ها/میله‌های نمودار برای وضعیت‌ها (سبز/زرد/قرمز واقعی، نه رنگ متن) */
    const CHART_STATUS = {
        dark: { success: "#4fc07a", warning: "#f2c744", danger: "#f0605d" },
        light: { success: "#16a34a", warning: "#ca8a04", danger: "#dc2626" },
    };

    /* سری‌های دسته‌ای نمودار؛ سری اول همیشه رنگ accent تم است. */
    const CHART_POOL = {
        dark: ["#5aa9f5", "#45c2b5", "#e8b04a", "#b995f2", "#ee8a6a", "#e885b0"],
        light: ["#2563eb", "#0f766e", "#b45309", "#7c3aed", "#c2410c", "#be185d"],
    };

    /* مراحل تولید (نشان وضعیت + نمودار توزیع مراحل) */
    const STAGE = {
        "not-started": "#B0AEC0",
        casting: "#E8A86A",
        "rough-machining": "#8B9BB4",
        radiography: "#E0B85C",
        welding: "#E07A7A",
        "heat-treatment": "#5DB8AE",
        tensile: "#E09070",
        "shot-blasting": "#8B7AE8",
        ready: "#5DBB8A",
        delivered: "#5B8DEF",
        scrapped: "#D16B6B",
    };

    /* ------------------------------------------------------------------
       ابزارهای رنگ
       ------------------------------------------------------------------ */
    function isHex(value) {
        return typeof value === "string" && HEX_RE.test(value.trim());
    }

    function hexToRgb(hex) {
        let h = String(hex).trim().replace("#", "");
        if (h.length === 3) h = h.split("").map((ch) => ch + ch).join("");
        const n = parseInt(h, 16);
        return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
    }

    function rgbToHex(rgb) {
        return "#" + rgb
            .map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0"))
            .join("");
    }

    /** t = سهم رنگ b در ترکیب (۰ تا ۱) */
    function mix(a, b, t) {
        const A = hexToRgb(a);
        const B = hexToRgb(b);
        return rgbToHex(A.map((v, i) => v + (B[i] - v) * t));
    }

    function num(value) {
        return +value.toFixed(3);
    }

    function alpha(hex, a) {
        const [r, g, b] = hexToRgb(hex);
        return `rgba(${r}, ${g}, ${b}, ${num(a)})`;
    }

    function channel(v) {
        const s = v / 255;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    function luminance(hex) {
        const [r, g, b] = hexToRgb(hex);
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
    }

    function contrast(a, b) {
        const la = luminance(a);
        const lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** رنگ fg را تا رسیدن به کنتراست حداقل، به سمت toward می‌برد. */
    function ensureContrast(fg, bg, min, toward) {
        let color = fg;
        for (let i = 0; i < 24 && contrast(color, bg) < min; i += 1) {
            color = mix(color, toward, 0.12);
        }
        return color;
    }

    function onColor(bg) {
        return contrast("#ffffff", bg) >= contrast(INK, bg) ? "#ffffff" : INK;
    }

    function hslOf(hex) {
        const [r, g, b] = hexToRgb(hex).map((v) => v / 255);
        const max = Math.max(r, g, b);
        const min = Math.min(r, g, b);
        const l = (max + min) / 2;
        const d = max - min;
        let h = 0;
        let s = 0;
        if (d) {
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max === r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max === g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
        }
        return { h, s, l };
    }

    function hueDistance(a, b) {
        const d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    /* ------------------------------------------------------------------
       کامپایل یک تعریف به توکن‌های CSS
       ------------------------------------------------------------------ */
    const REQUIRED_COLORS = [
        "bg", "surface", "surfaceElevated", "surfaceHover", "surfaceActive",
        "text", "textSecondary", "textMuted", "border", "borderStrong", "accent",
    ];

    function validateDefinition(def) {
        if (!def || typeof def.id !== "string" || !def.id) return "id ندارد";
        if (def.mode !== "dark" && def.mode !== "light") return "mode باید dark یا light باشد";
        if (!def.colors) return "colors ندارد";
        for (const key of REQUIRED_COLORS) {
            if (!isHex(def.colors[key])) return `رنگ «${key}» نامعتبر یا تعریف‌نشده است`;
        }
        return "";
    }

    function compileTheme(def) {
        const c = def.colors;
        const dark = def.mode === "dark";
        const pole = dark ? "#ffffff" : "#000000";
        const flat = def.flat ? 0.5 : 1;
        const v = {};

        /* --- سطوح و پس‌زمینه --- */
        v["--color-bg-primary"] = c.bg;
        v["--color-surface"] = c.surface;
        v["--color-surface-elevated"] = c.surfaceElevated;
        v["--color-surface-hover"] = c.surfaceHover;
        v["--color-surface-active"] = c.surfaceActive;

        /* --- متن --- */
        v["--color-text-primary"] = c.text;
        v["--color-text-secondary"] = c.textSecondary;
        v["--color-text-muted"] = c.textMuted;
        v["--color-text-disabled"] = mix(c.textMuted, c.surface, 0.4);

        /* --- خط‌ها --- */
        v["--color-border"] = c.border;
        v["--color-border-subtle"] = mix(c.border, c.surface, 0.5);
        v["--color-border-strong"] = c.borderStrong;

        /* --- accent --- */
        const accent = c.accent;
        const accent2 = c.accent2 || accent;
        const accentHover = c.accentHover || mix(accent, pole, 0.14);
        const accentSoft = c.accentSoft || mix(c.surface, accent, dark ? 0.18 : 0.11);
        const accentText = c.accentText
            || ensureContrast(mix(accent, pole, 0.2), accentSoft, 4.5, pole);
        const onAccent = c.onAccent || onColor(accent);
        v["--color-accent"] = accent;
        v["--color-accent-hover"] = accentHover;
        v["--color-accent-soft"] = accentSoft;
        v["--color-accent-text"] = accentText;
        v["--color-accent-2"] = accent2;
        v["--color-text-on-accent"] = onAccent;
        v["--gradient-primary"] = `linear-gradient(135deg, ${accent} 0%, ${accent2} 100%)`;
        v["--gradient-primary-hover"] =
            `linear-gradient(135deg, ${accentHover} 0%, ${mix(accent2, pole, 0.14)} 100%)`;

        /* --- وضعیت‌ها (معنا ثابت؛ سایه بسته به حالت) --- */
        const status = Object.assign({}, STATUS[def.mode], def.status);
        ["success", "warning", "danger", "info", "teal", "coral"].forEach((key) => {
            const fg = status[key];
            const soft = mix(c.surface, fg, dark ? 0.16 : 0.12);
            v[`--color-${key}`] = fg;
            v[`--color-${key}-soft`] = soft;
            v[`--color-${key}-text`] = ensureContrast(mix(fg, c.text, dark ? 0.35 : 0.45), soft, 5, pole);
        });
        v["--color-text-on-status"] = dark ? INK : "#ffffff";

        /* --- فرم‌ها --- */
        v["--color-input-bg"] = c.inputBg || (dark ? mix(c.surface, c.bg, 0.5) : c.surfaceHover);
        v["--color-input-bg-focus"] = c.surface;
        v["--color-input-border"] = c.inputBorder || (dark ? c.borderStrong : c.border);
        v["--color-input-placeholder"] = c.textMuted;

        /* --- ناوبری، سایدبار و کارت --- */
        v["--color-navbar-bg"] = alpha(dark ? c.surfaceElevated : c.surface, 0.72);
        v["--color-navbar-border"] = c.border;
        v["--color-navbar-text"] = c.textSecondary;
        v["--color-navbar-text-active"] = onAccent;
        v["--color-sidebar-bg"] = c.surface;
        v["--color-sidebar-border"] = c.border;
        v["--color-sidebar-text"] = c.textSecondary;
        v["--color-sidebar-active-bg"] = accentSoft;
        v["--color-sidebar-active-text"] = accentText;
        v["--color-card-bg"] = c.cardBg || c.surface;
        v["--color-card-border"] = c.border;

        /* --- پوسته، لایه‌ی تیره‌کننده و لکه‌های نوری --- */
        v["--gradient-surface"] = dark
            ? `linear-gradient(180deg, ${alpha(c.surface, 0.8)} 0%, ${alpha(c.surface, 0.68)} 100%)`
            : `linear-gradient(180deg, ${alpha(c.surface, 0.92)} 0%, ${alpha(c.surface, 0.78)} 100%)`;
        v["--color-shell-border"] = def.shellBorder
            || (dark ? "rgba(255, 255, 255, 0.06)" : "rgba(255, 255, 255, 0.72)");
        v["--color-overlay"] = dark ? "rgba(0, 0, 0, 0.62)" : alpha(c.text, 0.38);
        v["--color-loading-overlay"] = alpha(c.surface, 0.72);

        const k = def.glow === undefined ? 1 : def.glow;
        const glowColors = def.glowColors || [accent, accent2, status.info];
        const glowAlpha = def.glowAlpha || (dark ? [0.14, 0.09, 0.07] : [0.2, 0.14, 0.12]);
        [0, 1, 2].forEach((i) => {
            v[`--bg-glow-${i + 1}`] = alpha(glowColors[i], glowAlpha[i] * k);
        });

        /* --- سایه‌ها --- */
        const tint = dark ? [0, 0, 0] : hexToRgb(c.shadowTint || c.text);
        const sh = (a) => `rgba(${tint.join(", ")}, ${num(a * flat)})`;
        if (dark) {
            v["--shadow"] = `0 8px 28px ${sh(0.28)}, 0 0 0 1px rgba(255, 255, 255, 0.05)`;
            v["--shadow-md"] = `0 18px 48px ${sh(0.42)}, 0 0 0 1px rgba(255, 255, 255, 0.06)`;
            v["--shadow-lg"] = `0 24px 64px ${sh(0.45)}`;
            v["--shadow-border"] = "0 0 0 1px rgba(255, 255, 255, 0.07), 0 1px 2px -1px rgba(0, 0, 0, 0.4), 0 2px 4px rgba(0, 0, 0, 0.2)";
            v["--shadow-border-hover"] = "0 0 0 1px rgba(255, 255, 255, 0.12), 0 1px 2px -1px rgba(0, 0, 0, 0.45), 0 2px 6px rgba(0, 0, 0, 0.28)";
            v["--shadow-lift"] = "0 14px 28px rgba(0, 0, 0, 0.4)";
            v["--shadow-lift-hover"] = "0 28px 56px rgba(0, 0, 0, 0.55)";
        } else {
            /* حلقه‌ی ۱px ثابت می‌ماند (مرز کارت‌ها خوانا بماند)؛ فقط سایه‌های نرم با flat کم می‌شوند */
            v["--shadow"] = `0 8px 28px ${sh(0.06)}, 0 0 0 1px rgba(0, 0, 0, 0.06)`;
            v["--shadow-md"] = `0 18px 48px ${sh(0.1)}, 0 0 0 1px rgba(0, 0, 0, 0.06)`;
            v["--shadow-lg"] = `0 24px 64px ${sh(0.12)}`;
            v["--shadow-border"] = `0px 0px 0px 1px rgba(0, 0, 0, 0.08), 0px 1px 2px -1px rgba(0, 0, 0, ${num(0.06 * flat)}), 0px 2px 4px 0px rgba(0, 0, 0, ${num(0.04 * flat)})`;
            v["--shadow-border-hover"] = `0px 0px 0px 1px rgba(0, 0, 0, 0.1), 0px 1px 2px -1px rgba(0, 0, 0, ${num(0.08 * flat)}), 0px 2px 4px 0px rgba(0, 0, 0, ${num(0.06 * flat)})`;
            v["--shadow-lift"] = `0 14px 28px rgba(0, 0, 0, ${num(0.12 * flat)})`;
            v["--shadow-lift-hover"] = `0 28px 56px rgba(0, 0, 0, ${num(0.18 * flat)})`;
        }
        v["--shadow-accent"] = `0 8px 18px ${alpha(accent, dark ? 0.22 : 0.26 * flat + 0.04)}`;
        v["--shadow-accent-hover"] = `0 10px 22px ${alpha(accent, dark ? 0.3 : 0.32 * flat + 0.04)}`;
        v["--focus-ring"] = `0 0 0 4px ${alpha(accent, dark ? 0.3 : 0.18)}`;

        /* --- نمودارها --- */
        const chartStatus = Object.assign({}, CHART_STATUS[def.mode], def.chartStatus);
        const accentHsl = hslOf(accent);
        const series = [accent];
        const isClose = (hex) => accentHsl.s > 0.25 && hueDistance(hslOf(hex).h, accentHsl.h) < 40;
        CHART_POOL[def.mode].forEach((hex) => {
            if (series.length < 6 && !isClose(hex)) series.push(hex);
        });
        /* همیشه ۶ سری؛ اگر رنگی به accent نزدیک بود آخر فهرست می‌آید تا توکنی بدون مقدار نماند */
        CHART_POOL[def.mode].forEach((hex) => {
            if (series.length < 6 && !series.includes(hex)) series.push(hex);
        });
        series.forEach((hex, i) => { v[`--chart-${i + 1}`] = hex; });
        const plotBg = dark ? mix(c.surface, c.bg, 0.5) : c.surface;
        v["--chart-success"] = chartStatus.success;
        v["--chart-warning"] = chartStatus.warning;
        v["--chart-danger"] = chartStatus.danger;
        v["--chart-neutral"] = c.textMuted;
        v["--chart-plot-bg"] = plotBg;
        v["--chart-grid"] = dark ? c.border : mix(c.border, c.surface, 0.15);
        v["--chart-axis"] = mix(c.text, c.surface, 0.25);
        v["--chart-label"] = c.textSecondary;
        v["--chart-title"] = c.text;
        v["--chart-reference"] = ensureContrast(accentText, plotBg, 4.5, pole);
        v["--chart-point-stroke"] = plotBg;
        v["--chart-tooltip-background"] = dark ? c.surfaceActive : c.text;
        v["--chart-tooltip-text"] = dark ? c.text : c.surface;

        /* --- مراحل تولید ---
           در تم روشن رنگ مرحله کمی عمیق‌تر می‌شود تا میله‌ها روی ترک روشن دیده شوند؛
           درصد ترکیب برای پس‌زمینه/متن نشان هم بسته به حالت تنظیم می‌شود (خوانایی ≥ 4.5). */
        Object.keys(STAGE).forEach((key) => {
            const base = (def.stage && def.stage[key]) || STAGE[key];
            v[`--stage-${key}`] = dark ? base : mix(base, "#000000", 0.1);
        });
        v["--stage-bg-mix"] = "16%";
        v["--stage-text-mix"] = dark ? "50%" : "36%";

        /* --- بازنویسی‌های دستی تم --- */
        if (def.tokens) Object.assign(v, def.tokens);

        return {
            vars: v,
            mode: def.mode,
            preview: {
                bg: c.bg,
                surface: c.surface,
                text: c.text,
                border: c.border,
                accent,
                accent2,
                success: v["--color-success"],
                warning: v["--color-warning"],
                danger: v["--color-danger"],
            },
        };
    }

    /* ------------------------------------------------------------------
       رجیستری
       ------------------------------------------------------------------ */
    const registry = new Map();
    const compiled = new Map();
    const order = [];

    function register(def) {
        const problem = validateDefinition(def);
        if (problem) {
            console.warn(`[theme] تم نادیده گرفته شد (${def && def.id}): ${problem}`);
            return;
        }
        if (!registry.has(def.id)) order.push(def.id);
        registry.set(def.id, def);
        compiled.delete(def.id);
    }

    (global.PM_THEME_DEFINITIONS || []).forEach(register);

    if (!order.length) {
        console.error("[theme] هیچ تم معتبری در theme-definitions.js تعریف نشده است؛ سیستم تم غیرفعال شد");
        return;
    }

    const fallbackId = registry.has(global.PM_DEFAULT_THEME_ID)
        ? global.PM_DEFAULT_THEME_ID
        : order[0];

    function get(id) {
        return registry.get(id) || null;
    }

    function getCompiled(id) {
        if (!compiled.has(id)) compiled.set(id, compileTheme(registry.get(id)));
        return compiled.get(id);
    }

    /* ------------------------------------------------------------------
       ذخیره‌سازی (localStorage؛ خطاها بی‌صدا نادیده گرفته می‌شوند)
       ------------------------------------------------------------------ */
    function readStored() {
        try {
            const value = global.localStorage.getItem(STORAGE_KEY);
            return value && registry.has(value) ? value : null;
        } catch (_) {
            return null;
        }
    }

    function writeStored(id) {
        try {
            global.localStorage.setItem(STORAGE_KEY, id);
        } catch (_) {
            /* حالت خصوصی یا حافظه‌ی پر: تم فقط برای همین نشست اعمال می‌شود */
        }
    }

    /* ------------------------------------------------------------------
       اعمال تم
       ------------------------------------------------------------------ */
    let currentId = null;
    let switchTimer = null;

    function updateThemeColorMeta(color) {
        if (!document.head) return;
        let meta = document.querySelector('meta[name="theme-color"]');
        if (!meta) {
            meta = document.createElement("meta");
            meta.name = "theme-color";
            document.head.appendChild(meta);
        }
        meta.content = color;
    }

    function apply(id, options) {
        const opts = Object.assign({ persist: true, animate: false }, options);
        const targetId = registry.has(id) ? id : fallbackId;
        const def = registry.get(targetId);
        const result = getCompiled(targetId);
        const root = document.documentElement;

        if (opts.animate && !global.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            root.classList.add("theme-switching");
            clearTimeout(switchTimer);
            switchTimer = setTimeout(() => root.classList.remove("theme-switching"), 360);
        }

        Object.keys(result.vars).forEach((name) => {
            root.style.setProperty(name, result.vars[name]);
        });
        root.dataset.theme = targetId;
        root.dataset.themeMode = def.mode;
        root.style.colorScheme = def.mode;
        updateThemeColorMeta(def.colors.bg);

        const changed = currentId !== targetId;
        currentId = targetId;
        if (opts.persist) writeStored(targetId);
        if (changed) {
            document.dispatchEvent(new CustomEvent("themechange", {
                detail: { id: targetId, theme: def, mode: def.mode },
            }));
        }
        return targetId;
    }

    /* ------------------------------------------------------------------
       ابزارهای عمومی
       ------------------------------------------------------------------ */

    /** مقدار فعلی یک توکن CSS (برای canvas و کتابخانه‌های نمودار). */
    function cssVar(name, fallback) {
        const value = global.getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return value || fallback || "";
    }

    /**
     * بررسی کنتراست WCAG برای همه‌ی تم‌ها. در کنسول: ThemeManager.audit()
     * فقط جفت‌های مهم را می‌سنجد و موارد مردود را برمی‌گرداند.
     */
    function audit(options) {
        const opts = Object.assign({ log: true }, options);
        const failures = [];
        const rows = [];
        order.forEach((id) => {
            const v = getCompiled(id).vars;
            const check = (label, fg, bg, min) => {
                if (!isHex(v[fg] || fg) || !isHex(v[bg] || bg)) return;
                const ratio = contrast(v[fg] || fg, v[bg] || bg);
                const row = { theme: id, pair: label, ratio: +ratio.toFixed(2), min, pass: ratio >= min };
                rows.push(row);
                if (!row.pass) failures.push(row);
            };
            check("متن اصلی / سطح", "--color-text-primary", "--color-surface", 4.5);
            check("متن اصلی / پس‌زمینه", "--color-text-primary", "--color-bg-primary", 4.5);
            check("متن ثانویه / سطح", "--color-text-secondary", "--color-surface", 4.5);
            check("متن ثانویه / hover", "--color-text-secondary", "--color-surface-hover", 4.5);
            check("متن کم‌رنگ / سطح", "--color-text-muted", "--color-surface", 4.5);
            check("متن کم‌رنگ / پس‌زمینه", "--color-text-muted", "--color-bg-primary", 4.5);
            check("متن کم‌رنگ / hover", "--color-text-muted", "--color-surface-hover", 4.5);
            check("accent به‌عنوان متن / سطح", "--color-accent", "--color-surface", 4.5);
            check("متن روی accent", "--color-text-on-accent", "--color-accent", 4.5);
            check("متن روی accent (hover)", "--color-text-on-accent", "--color-accent-hover", 4.5);
            check("متن accent / پس‌زمینه‌ی ملایم", "--color-accent-text", "--color-accent-soft", 4.5);
            ["success", "warning", "danger", "info"].forEach((key) => {
                check(`${key} به‌عنوان متن / سطح`, `--color-${key}`, "--color-surface", 4.5);
                check(`${key} متن / پس‌زمینه‌ی ملایم`, `--color-${key}-text`, `--color-${key}-soft`, 4.5);
                check(`متن روی ${key}`, "--color-text-on-status", `--color-${key}`, 4.5);
            });
            check("برچسب نمودار / زمینه", "--chart-label", "--chart-plot-bg", 4.5);
            check("خط مرجع نمودار / زمینه", "--chart-reference", "--chart-plot-bg", 3);
            ["success", "warning", "danger"].forEach((key) => {
                check(`نقطه‌ی نمودار ${key} / زمینه`, `--chart-${key}`, "--chart-plot-bg", 2.5);
            });
            check("متن tooltip", "--chart-tooltip-text", "--chart-tooltip-background", 4.5);
        });
        if (opts.log && global.console) {
            if (failures.length) {
                console.warn(`[theme] ${failures.length} جفت رنگ کنتراست کافی ندارند`);
                if (console.table) console.table(failures);
            } else {
                console.info(`[theme] همه‌ی ${rows.length} بررسی کنتراست در ${order.length} تم موفق بود`);
            }
        }
        return failures;
    }

    /* ------------------------------------------------------------------
       راه‌اندازی: تم ذخیره‌شده را هم‌زمان (قبل از اولین رنگ‌آمیزی) اعمال کن
       ------------------------------------------------------------------ */
    apply(readStored() || fallbackId, { persist: false });

    /* هم‌گام‌سازی بین تب‌ها */
    global.addEventListener("storage", (event) => {
        if (event.key !== STORAGE_KEY) return;
        apply(readStored() || fallbackId, { persist: false, animate: true });
    });

    global.ThemeManager = {
        storageKey: STORAGE_KEY,
        register,
        list() {
            return order.map((id) => registry.get(id));
        },
        get,
        currentId() {
            return currentId;
        },
        current() {
            return registry.get(currentId);
        },
        apply,
        preview(id) {
            return registry.has(id) ? getCompiled(id).preview : null;
        },
        cssVar,
        audit,
    };
})(window);

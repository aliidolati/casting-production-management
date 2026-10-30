/**
 * theme-definitions.js
 * ---------------------------------------------------------------------------
 * تنها فایلی که برای افزودن یک تم جدید باید ویرایش شود.
 *
 * هر تم فقط «رنگ‌های پایه» را تعریف می‌کند؛ بقیه‌ی توکن‌ها (سایه، گرادیان، رنگ‌های
 * وضعیت، نمودار، ناوبری، ...) در theme-manager.js از روی همین رنگ‌ها ساخته می‌شوند
 * و کنتراست متن‌ها به‌صورت خودکار اصلاح می‌شود.
 *
 * فیلدهای الزامی `colors`:
 *   bg, surface, surfaceElevated, surfaceHover, surfaceActive,
 *   text, textSecondary, textMuted, border, borderStrong, accent
 *
 * فیلدهای اختیاری `colors`:
 *   accentHover, accent2 (انتهای گرادیان), accentSoft, accentText, onAccent,
 *   inputBg, inputBorder, shadowTint
 *
 * فیلدهای اختیاری تم:
 *   status      بازنویسی رنگ‌های وضعیت { success, warning, danger, info, teal, coral }
 *               (معنای رنگ‌ها ثابت می‌ماند؛ فقط سایه‌ی آن‌ها تغییر می‌کند)
 *   chartStatus بازنویسی رنگ‌های نمودار { success, warning, danger }
 *   glow        شدت لکه‌های نوری پس‌زمینه (۰ تا ۱)
 *   glowColors  [c1, c2, c3] و glowAlpha: [a1, a2, a3]
 *   flat        سایه‌ها و عمق کمتر (برای تم‌های مینیمال)
 *   shellBorder رنگ لبه‌ی پوسته‌ی اصلی
 *   tokens      بازنویسی مستقیم هر توکن CSS (راه خروج اضطراری)
 *
 * mode: "dark" | "light" (روی color-scheme مرورگر و پالت وضعیت‌ها اثر می‌گذارد)
 * ---------------------------------------------------------------------------
 */
(function (global) {
    "use strict";

    const THEMES = [
        /* ===================== پیش‌فرض: هویت فعلی برنامه ===================== */
        {
            id: "classic",
            label: "کلاسیک",
            labelEn: "Classic",
            mode: "light",
            colors: {
                bg: "#F3F1F8",
                surface: "#FFFFFF",
                surfaceElevated: "#FFFFFF",
                surfaceHover: "#F7F5FC",
                surfaceActive: "#ECE9F8",
                text: "#1B1A2E",
                textSecondary: "#5C5973",
                textMuted: "#6E6B84",
                border: "#E6E2F0",
                borderStrong: "#D4CFE3",
                accent: "#6B5EF7",
                accentHover: "#5A4DE8",
                accent2: "#3A70DD",
                accentSoft: "#EEEDFE",
                accentText: "#5346E0",
                shadowTint: "#50468C",
            },
            status: {
                success: "#04805a",
                warning: "#b45f06",
                danger: "#cf4545",
                info: "#3569de",
                teal: "#0f766e",
                coral: "#c4502f",
            },
            glowColors: ["#6B5EF7", "#FF9E6E", "#4F8CFF"],
            glowAlpha: [0.2, 0.18, 0.14],
        },

        /* ============================== تم‌های تیره ============================== */
        {
            id: "obsidian",
            label: "ابسیدین",
            labelEn: "Obsidian",
            mode: "dark",
            glow: 0.55,
            colors: {
                bg: "#0e0f11",
                surface: "#16171a",
                surfaceElevated: "#1d1f23",
                surfaceHover: "#212328",
                surfaceActive: "#2a2c32",
                text: "#e7e8eb",
                textSecondary: "#a9acb4",
                textMuted: "#8e929b",
                border: "#26282d",
                borderStrong: "#363940",
                accent: "#93a4c4",
                accentHover: "#abb9d3",
                accent2: "#7f93b8",
            },
        },
        {
            id: "deep-purple",
            label: "بنفش عمیق",
            labelEn: "Deep Purple",
            mode: "dark",
            colors: {
                bg: "#110f18",
                surface: "#1a1724",
                surfaceElevated: "#221e2f",
                surfaceHover: "#272238",
                surfaceActive: "#302a45",
                text: "#ece9f6",
                textSecondary: "#b0aac6",
                textMuted: "#9790b3",
                border: "#2e2941",
                borderStrong: "#41395a",
                accent: "#a78bfa",
                accentHover: "#b9a1fc",
                accent2: "#8b7cf6",
            },
        },
        {
            id: "industrial-blue",
            label: "آبی صنعتی",
            labelEn: "Industrial Blue",
            mode: "dark",
            colors: {
                bg: "#0c131c",
                surface: "#131d2a",
                surfaceElevated: "#192637",
                surfaceHover: "#1e2d41",
                surfaceActive: "#263951",
                text: "#e5edf6",
                textSecondary: "#a4b5ca",
                textMuted: "#8599b1",
                border: "#24344a",
                borderStrong: "#344865",
                accent: "#5aa2f0",
                accentHover: "#78b5f5",
                accent2: "#4a90e2",
            },
        },
        {
            id: "foundry-green",
            label: "سبز ریخته‌گری",
            labelEn: "Foundry Green",
            mode: "dark",
            glow: 0.7,
            colors: {
                bg: "#0f1210",
                surface: "#171a18",
                surfaceElevated: "#1e2220",
                surfaceHover: "#232725",
                surfaceActive: "#2c312e",
                text: "#e6eae7",
                textSecondary: "#a7afa9",
                textMuted: "#8b948d",
                border: "#292f2b",
                borderStrong: "#3a423d",
                accent: "#6fae7f",
                accentHover: "#88c196",
                accent2: "#5d9b6d",
            },
        },
        {
            id: "copper",
            label: "مس",
            labelEn: "Copper",
            mode: "dark",
            colors: {
                bg: "#120f0d",
                surface: "#1b1816",
                surfaceElevated: "#231f1c",
                surfaceHover: "#292420",
                surfaceActive: "#332d29",
                text: "#eee8e3",
                textSecondary: "#b2a79e",
                textMuted: "#958a81",
                border: "#2f2925",
                borderStrong: "#423a34",
                accent: "#d08a5c",
                accentHover: "#dd9d72",
                accent2: "#c2774a",
            },
        },
        {
            id: "steel",
            label: "فولادی",
            labelEn: "Steel",
            mode: "dark",
            glow: 0.5,
            colors: {
                bg: "#1b1f24",
                surface: "#242930",
                surfaceElevated: "#2c323a",
                surfaceHover: "#323942",
                surfaceActive: "#3b434d",
                text: "#eaeef2",
                textSecondary: "#b3bbc5",
                textMuted: "#a3acb7",
                border: "#38404a",
                borderStrong: "#4a535f",
                accent: "#7fa8cf",
                accentHover: "#97bad9",
                accent2: "#6c97c4",
            },
        },
        {
            id: "midnight",
            label: "نیمه‌شب",
            labelEn: "Midnight",
            mode: "dark",
            glow: 1.15,
            colors: {
                bg: "#090c1d",
                surface: "#10142b",
                surfaceElevated: "#171c38",
                surfaceHover: "#1c2243",
                surfaceActive: "#252c54",
                text: "#e8eafc",
                textSecondary: "#a9aed6",
                textMuted: "#8c92c0",
                border: "#252b50",
                borderStrong: "#353d6f",
                accent: "#8e9bff",
                accentHover: "#a5b0ff",
                accent2: "#a38cf5",
            },
        },
        {
            id: "foundry",
            label: "ریخته‌گری",
            labelEn: "Foundry",
            mode: "dark",
            glow: 0.8,
            colors: {
                bg: "#141619",
                surface: "#1f2226",
                surfaceElevated: "#272b30",
                surfaceHover: "#2d3136",
                surfaceActive: "#373c43",
                text: "#ecedef",
                textSecondary: "#b0b4ba",
                textMuted: "#a0a5ad",
                border: "#33373d",
                borderStrong: "#454a52",
                accent: "#e08a45",
                accentHover: "#ea9d60",
                accent2: "#d4742f",
            },
        },

        /* ============================== تم‌های روشن ============================== */
        {
            id: "soft-light",
            label: "روشن ملایم",
            labelEn: "Soft Light",
            mode: "light",
            glow: 0.5,
            colors: {
                bg: "#e8e9eb",
                surface: "#f3f3f4",
                surfaceElevated: "#f9f9fa",
                surfaceHover: "#ebebed",
                surfaceActive: "#e0e1e4",
                text: "#24262b",
                textSecondary: "#4d525c",
                textMuted: "#5f6570",
                border: "#d8d9dd",
                borderStrong: "#c2c4ca",
                accent: "#3f6894",
                accentHover: "#2f5679",
                accent2: "#46709f",
                shadowTint: "#3a4150",
            },
        },
        {
            id: "minimal-light",
            label: "روشن مینیمال",
            labelEn: "Minimal Light",
            mode: "light",
            glow: 0,
            flat: true,
            shellBorder: "rgba(0, 0, 0, 0.05)",
            colors: {
                bg: "#f1f2f4",
                surface: "#fbfbfc",
                surfaceElevated: "#ffffff",
                surfaceHover: "#f0f1f3",
                surfaceActive: "#e6e8ec",
                text: "#1f2328",
                textSecondary: "#4f5661",
                textMuted: "#666d79",
                border: "#e1e3e7",
                borderStrong: "#cdd0d6",
                accent: "#334155",
                accentHover: "#1e293b",
                accent2: "#475569",
                shadowTint: "#1f2328",
            },
        },
    ];

    /** تمی که اگر چیزی ذخیره نشده باشد (یا مقدار ذخیره‌شده نامعتبر باشد) اعمال می‌شود. */
    global.PM_DEFAULT_THEME_ID = "classic";
    global.PM_THEME_DEFINITIONS = THEMES;
})(window);

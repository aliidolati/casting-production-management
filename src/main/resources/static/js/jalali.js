/**
 * jalali.js
 * تبدیل تاریخ بین میلادی و شمسی (جلالی) — بدون هیچ وابستگی خارجی.
 * پیاده‌سازی بر اساس الگوریتم نجومی استاندارد جلالی (الگوریتم Borkowski، همان الگوریتمی که
 * کتابخانه‌ی معروف jalaali-js از آن استفاده می‌کند). تمام محاسبات از طریق عدد روز ژولیَنی (JDN)
 * انجام می‌شود تا تبدیل دوطرفه دقیق و بدون خطا باشد.
 */

// تقسیم و باقی‌مانده‌ی صحیح به‌سمت صفر (نه به‌سمت منفی بی‌نهایت)؛ این الگوریتم دقیقاً به همین رفتار نیاز دارد
function jDiv(a, b) {
    return ~~(a / b);
}

function jMod(a, b) {
    return a - jDiv(a, b) * b;
}

// نقاط شکستِ الگوی کبیسه‌ی تقویم جلالی در طول تاریخ
const JALALI_BREAKS = [
    -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
    1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
];

function jalCal(jy) {
    const bl = JALALI_BREAKS.length;
    const gy = jy + 621;
    let leapJ = -14;
    let jp = JALALI_BREAKS[0];

    if (jy < jp || jy >= JALALI_BREAKS[bl - 1]) {
        throw new Error("سال جلالی نامعتبر: " + jy);
    }

    let jump = 0;
    let jm = 0;
    let i;
    for (i = 1; i < bl; i += 1) {
        jm = JALALI_BREAKS[i];
        jump = jm - jp;
        if (jy < jm) break;
        leapJ = leapJ + jDiv(jump, 33) * 8 + jDiv(jMod(jump, 33), 4);
        jp = jm;
    }

    let n = jy - jp;
    leapJ = leapJ + jDiv(n, 33) * 8 + jDiv(jMod(n, 33) + 3, 4);
    if (jMod(jump, 33) === 4 && jump - n === 4) {
        leapJ += 1;
    }

    const leapG = jDiv(gy, 4) - jDiv((jDiv(gy, 100) + 1) * 3, 4) - 150;
    const march = 20 + leapJ - leapG;

    if (jump - n < 6) {
        n = n - jump + jDiv(jump + 4, 33) * 33;
    }

    let leap = jMod(jMod(n + 1, 33) - 1, 4);
    if (leap === -1) leap = 4;

    return { leap, gy, march };
}

function g2d(gy, gm, gd) {
    let d =
        jDiv((gy + jDiv(gm - 8, 6) + 100100) * 1461, 4) +
        jDiv(153 * jMod(gm + 9, 12) + 2, 5) +
        gd -
        34840408;
    d = d - jDiv(jDiv(gy + 100100 + jDiv(gm - 8, 6), 100) * 3, 4) + 752;
    return d;
}

function d2g(jdn) {
    let j = 4 * jdn + 139361631;
    j = j + jDiv(jDiv(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908;
    const i = jDiv(jMod(j, 1461), 4) * 5 + 308;
    const gd = jDiv(jMod(i, 153), 5) + 1;
    const gm = jMod(jDiv(i, 153), 12) + 1;
    const gy = jDiv(j, 1461) - 100100 + jDiv(8 - gm, 6);
    return [gy, gm, gd];
}

function j2d(jy, jm, jd) {
    const r = jalCal(jy);
    return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - jDiv(jm, 7) * (jm - 7) + jd - 1;
}

function d2j(jdn) {
    const gy = d2g(jdn)[0];
    let jy = gy - 621;
    const r = jalCal(jy);
    const jdn1f = g2d(gy, 3, r.march);
    let k = jdn - jdn1f;
    let jm;
    let jd;

    if (k >= 0) {
        if (k <= 185) {
            jm = 1 + jDiv(k, 31);
            jd = jMod(k, 31) + 1;
            return [jy, jm, jd];
        }
        k -= 186;
    } else {
        jy -= 1;
        k += 179;
        if (r.leap === 1) k += 1;
    }

    jm = 7 + jDiv(k, 30);
    jd = jMod(k, 30) + 1;
    return [jy, jm, jd];
}

/**
 * تبدیل تاریخ میلادی به شمسی. خروجی: [سال, ماه, روز] شمسی.
 */
function gregorianToJalali(gy, gm, gd) {
    return d2j(g2d(gy, gm, gd));
}

/**
 * تبدیل تاریخ شمسی به میلادی. خروجی: [سال, ماه, روز] میلادی.
 */
function jalaliToGregorian(jy, jm, jd) {
    return d2g(j2d(jy, jm, jd));
}

function isJalaliLeapYear(jy) {
    return jalCal(jy).leap === 0;
}

function jalaliMonthLength(jy, jm) {
    if (jm <= 6) return 31;
    if (jm <= 11) return 30;
    return isJalaliLeapYear(jy) ? 30 : 29;
}

const JALALI_MONTH_NAMES = [
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
];

const JALALI_WEEKDAY_NAMES = ["یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"];

/**
 * تاریخ شمسی امروز را برمی‌گرداند: [سال, ماه, روز].
 */
function todayJalali() {
    const now = new Date();
    return gregorianToJalali(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

/**
 * یک رشته‌ی تاریخ میلادی (yyyy-mm-dd یا ISO) را به رشته‌ی شمسی «yyyy/mm/dd» تبدیل می‌کند.
 */
function isoToJalaliString(isoString) {
    if (!isoString) return "";
    const m = String(isoString).match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (m) {
        const [jy, jm, jd] = gregorianToJalali(Number(m[1]), Number(m[2]), Number(m[3]));
        return `${jy}/${String(jm).padStart(2, "0")}/${String(jd).padStart(2, "0")}`;
    }
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return "";
    const [jy, jm, jd] = gregorianToJalali(date.getFullYear(), date.getMonth() + 1, date.getDate());
    return `${jy}/${String(jm).padStart(2, "0")}/${String(jd).padStart(2, "0")}`;
}

/**
 * یک تاریخ شمسی (سال، ماه، روز) را به رشته‌ی تاریخ میلادی yyyy-mm-dd تبدیل می‌کند
 * (برای استفاده در مقایسه‌های تاریخی؛ مثلاً new Date(...)).
 */
function jalaliToIsoDateString(jy, jm, jd) {
    const [gy, gm, gd] = jalaliToGregorian(jy, jm, jd);
    return `${gy}-${String(gm).padStart(2, "0")}-${String(gd).padStart(2, "0")}`;
}

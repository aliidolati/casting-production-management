/**
 * rough-machining.js
 * زیرمرحله‌های داخلی مرحله‌ی «خشن‌تراشی»: نمایش، تغییر تکی و نشان در جدول ردیابی.
 *
 * برخلاف رادیوگرافی و عملیات حرارتی، این سه زیرمرحله خطی‌اند: حرکت فقط رو به
 * جلوست و بازگشت به عقب فقط برای ادمین باز است. ترتیب آرایه‌ی زیر همان ترتیب
 * enum سمت سرور است و مبنای همین محدودیت قرار می‌گیرد.
 */

const ROUGH_MACHINING_SUBSTAGE_OPTIONS = ["DEBURRING", "INITIAL_CORRECTION", "MACHINING"];

const ROUGH_MACHINING_SUBSTAGE_LABELS = {
    DEBURRING: "پلیسه‌گیری",
    INITIAL_CORRECTION: "اصلاح اولیه",
    MACHINING: "تراش",
};

function roughMachiningSubStageBadgeClass() {
    return "substage-badge-rough-machining";
}

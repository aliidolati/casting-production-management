/**
 * heat-treatment.js
 * زیرمرحله‌های داخلی مرحله‌ی «عملیات حرارتی»: نمایش، تغییر تکی و نشان در جدول ردیابی.
 */

const HEAT_TREATMENT_SUBSTAGE_OPTIONS = [
    "AWAITING_OPERATIONS",
    "STAGE_1",
    "STAGE_2",
    "STAGE_3",
    "STAGE_4",
    "STAGE_5",
    "CNC_SAMPLE",
];

const HEAT_TREATMENT_SUBSTAGE_LABELS = {
    AWAITING_OPERATIONS: "منتظر عملیات",
    STAGE_1: "مرحله اول",
    STAGE_2: "مرحله دوم",
    STAGE_3: "مرحله سوم",
    STAGE_4: "مرحله چهارم",
    STAGE_5: "مرحله پنجم",
    CNC_SAMPLE: "نمونه CNC",
};

function heatTreatmentSubStageBadgeClass() {
    return "substage-badge-heat-treatment";
}

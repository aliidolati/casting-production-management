package com.factory.productmanager.model.entity;

public enum RadiographyReportVersionKind {
    /** وضعیت گزارش‌ها در لحظه‌ی تخصیص مفسر (پیش از هر ویرایش مفسر) */
    ORIGINAL,
    INTERPRETER_APPROVED,
    /** نسخه‌ی نهایی با تأیید مدیر */
    MANAGER_FINAL
}
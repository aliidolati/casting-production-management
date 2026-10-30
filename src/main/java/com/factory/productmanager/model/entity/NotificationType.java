package com.factory.productmanager.model.entity;

/**
 * نوع رویداد اعلان سیستمی. اعلان‌های جدید به همان صندوق و همان زنگوله اضافه می‌شوند.
 */
public enum NotificationType {
    TENSILE_AUTO_APPROVED,

    /** وظیفه‌ی جدید به کاربر محول شد */
    TASK_ASSIGNED,
    /** مسئول، مهلت یا اولویت وظیفه عوض شد */
    TASK_UPDATED,
    TASK_COMPLETED,
    TASK_REOPENED,
    TASK_COMMENT,
    TASK_DUE_TOMORROW,
    TASK_DUE_TODAY,
    TASK_OVERDUE,

    /** گردش‌کار رادیوگرافی: تخصیص گزارش به مفسر */
    RADIOGRAPHY_ASSIGNED,
    /** گردش‌کار رادیوگرافی: گزارش برای تأیید مدیر آماده است */
    RADIOGRAPHY_READY_FOR_REVIEW,
    /** گردش‌کار رادیوگرافی: مدیر گزارش را رد کرد */
    RADIOGRAPHY_REJECTED,
    /** گردش‌کار رادیوگرافی: تأیید نهایی و انتقال قطعه */
    RADIOGRAPHY_APPROVED
}

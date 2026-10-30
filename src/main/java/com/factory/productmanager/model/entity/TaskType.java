package com.factory.productmanager.model.entity;

/**
 * نوع وظیفه. وظیفه‌ی عادی نوع ندارد (null = GENERAL)؛ وظایف گردش‌کار فقط از مسیر همان گردش‌کار
 * بسته یا بازگشایی می‌شوند و مسئولشان با API عمومی وظایف عوض نمی‌شود.
 */
public enum TaskType {
    GENERAL,
    /** تفسیر و صدور گزارش رادیوگرافی توسط مفسر */
    RADIOGRAPHY_INTERPRETATION,
    /** بررسی و تأیید نهایی گزارش رادیوگرافی توسط مدیر */
    RADIOGRAPHY_MANAGER_APPROVAL,
    MELT_PASSPORT_ENTRY,
    MELT_PASSPORT_APPROVAL;

    public boolean isWorkflow() {
        return this != GENERAL;
    }
}

package com.factory.productmanager.model.entity;

/**
 * وضعیت گردش‌کار تفسیر رادیوگرافی یک قطعه. «تأیید مفسر» حالت پایدار جدا ندارد:
 * هم‌زمان با ثبت تأیید، گردش‌کار به MANAGER_REVIEW می‌رود (تأیید مفسر در جدول تأییدها ثبت است).
 * مرحله‌ی قطعه (RADIOGRAPHY + INTERPRETATION) در همان ساختار موجود قطعه می‌ماند و اینجا کپی نمی‌شود.
 */
public enum RadiographyWorkflowStatus {
    INTERPRETER_ASSIGNED,
    REPORT_IN_PROGRESS,
    MANAGER_REVIEW,
    REINTERPRETATION_REQUIRED,
    MANAGER_APPROVED,
    CANCELLED;

    public boolean isOpen() {
        return this != MANAGER_APPROVED && this != CANCELLED;
    }

    /** مفسر در این وضعیت‌ها می‌تواند گزارش را ویرایش و صادر کند */
    public boolean isInterpreterEditable() {
        return this == INTERPRETER_ASSIGNED || this == REPORT_IN_PROGRESS || this == REINTERPRETATION_REQUIRED;
    }
}
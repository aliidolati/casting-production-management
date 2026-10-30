package com.factory.productmanager.model.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * وضعیت وظیفه. مقدار در دیتابیس به‌صورت رشته (VARCHAR) ذخیره می‌شود تا افزودن
 * وضعیت جدید در آینده نیازی به تغییر ساختار ستون نداشته باشد.
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    WAITING,
    COMPLETED,
    CANCELLED;

    /** وضعیت‌هایی که وظیفه هنوز «باز» حساب می‌شود (مبنای محاسبه‌ی تأخیر و آمار) */
    public static final Set<TaskStatus> OPEN = EnumSet.of(TODO, IN_PROGRESS, WAITING);

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean isOpen() {
        return !isTerminal();
    }
}

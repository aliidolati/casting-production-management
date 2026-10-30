package com.factory.productmanager.model.dto;

/**
 * کلیدهای مرتب‌سازی لیست وظایف.
 * SMART همان ترتیب پیش‌فرض است: دارای تأخیر، بحرانی، زیاد، مهلت نزدیک، بقیه، و وظایف بسته‌شده در انتها.
 * کاربر همیشه می‌تواند کلید دیگری را دستی انتخاب کند.
 */
public enum TaskSort {
    SMART,
    DUE_DATE,
    PRIORITY,
    CREATED,
    UPDATED;

    /** کلیدهای ساده‌ی API: smart | dueDate | priority | createdAt | updatedAt */
    public static TaskSort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return SMART;
        }
        return switch (value.trim().toLowerCase()) {
            case "smart" -> SMART;
            case "duedate" -> DUE_DATE;
            case "priority" -> PRIORITY;
            case "createdat" -> CREATED;
            case "updatedat" -> UPDATED;
            default -> throw new IllegalArgumentException("کلید مرتب‌سازی معتبر نیست");
        };
    }
}

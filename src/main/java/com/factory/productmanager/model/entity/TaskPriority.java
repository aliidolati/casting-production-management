package com.factory.productmanager.model.entity;

import lombok.Getter;

/**
 * اولویت وظیفه. رنگ‌ها عمداً اینجا نیستند؛ فرانت‌اند آن‌ها را از توکن‌های تم می‌گیرد.
 * severity برای مرتب‌سازی بر اساس شدت (نه ترتیب الفبایی) استفاده می‌شود.
 */
@Getter
public enum TaskPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int severity;

    TaskPriority(int severity) {
        this.severity = severity;
    }
}

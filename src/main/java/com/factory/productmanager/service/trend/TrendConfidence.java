package com.factory.productmanager.service.trend;

/**
 * اطمینان به روند محاسبه‌شده؛ از ترکیب تعداد داده و R² به دست می‌آید.
 * شیب مثبت با R² پایین یا داده‌ی کم نباید روند «قوی» را القا کند.
 */
public enum TrendConfidence {
    HIGH,
    MEDIUM,
    LOW,
    /** روند محاسبه نشده است */
    NONE
}

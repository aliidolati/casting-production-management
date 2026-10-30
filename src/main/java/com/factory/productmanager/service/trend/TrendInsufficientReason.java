package com.factory.productmanager.service.trend;

/** دلیل اینکه روند محاسبه نشده است (فقط وقتی جهت INSUFFICIENT_DATA است). */
public enum TrendInsufficientReason {
    /** هیچ داده‌ای نیست */
    NO_DATA,
    /** فقط یک داده هست */
    SINGLE_POINT,
    /** چند داده هست ولی همه در یک لحظه‌اند و شیب زمانی معنا ندارد */
    NO_TIME_SPREAD
}

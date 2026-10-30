package com.factory.productmanager.service.trend;

/** جهت کلی روند یک سری زمانی. */
public enum TrendDirection {
    INCREASING,
    DECREASING,
    STABLE,
    /** داده برای برازش خط روند کافی نیست (کمتر از دو نقطه یا همه‌ی نقطه‌ها در یک لحظه) */
    INSUFFICIENT_DATA
}

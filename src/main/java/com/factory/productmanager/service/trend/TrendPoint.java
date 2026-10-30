package com.factory.productmanager.service.trend;

/**
 * یک مشاهده برای تحلیل روند.
 *
 * @param x زمان بر حسب «روز» (اعشاری) از یک مبدأ دلخواه؛ فاصله‌ی واقعی تاریخ‌ها در آن حفظ می‌شود
 * @param y مقدار اندازه‌گیری‌شده
 */
public record TrendPoint(double x, double y) {
}

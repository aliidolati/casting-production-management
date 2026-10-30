package com.factory.productmanager.service.trend;

import java.util.List;

/**
 * خروجی تحلیل روند یک سری زمانی. مقدارهای خام (گردنشده) هستند؛ گرد کردن برای نمایش
 * وظیفه‌ی لایه‌ی ساخت پاسخ است.
 *
 * @param count                    تعداد مشاهده‌ها
 * @param average                  میانگین مقادیر
 * @param firstValue               مقدار اولین مشاهده در زمان
 * @param lastValue                مقدار آخرین مشاهده در زمان
 * @param percentageChange         (last − first) / |first| × 100؛ null اگر first صفر یا داده ناکافی باشد
 * @param slopePerDay              شیب رگرسیون خطی بر حسب «واحد مقدار در روز»؛ null اگر محاسبه نشده
 * @param r2                       ضریب تعیین؛ null اگر محاسبه نشده یا همه‌ی مقادیر برابر باشند
 * @param normalizedChangePercent  تغییر کل خط روند در طول بازه، به‌صورت درصد از میانگین (معیار تشخیص Stable)
 * @param direction                جهت روند
 * @param confidence               اطمینان به روند
 * @param insufficientReason       دلیل ناکافی بودن داده (فقط برای INSUFFICIENT_DATA)
 * @param fittedValues             مقدار خط روند برای هر مشاهده (هم‌ترتیب ورودی)؛ خالی اگر روند محاسبه نشده
 * @param outliers                 علامت داده‌ی پرت برای هر مشاهده (هم‌ترتیب ورودی)؛ داده‌ها حذف نمی‌شوند
 */
public record TrendResult(
        int count,
        Double average,
        Double firstValue,
        Double lastValue,
        Double percentageChange,
        Double slopePerDay,
        Double r2,
        Double normalizedChangePercent,
        TrendDirection direction,
        TrendConfidence confidence,
        TrendInsufficientReason insufficientReason,
        List<Double> fittedValues,
        List<Boolean> outliers
) {
}

package com.factory.productmanager.service.trend;

/**
 * پارامترهای قابل تنظیم تحلیل روند. مقدارها از تنظیمات مرکزی برنامه
 * ({@code app.tensile-trend.*}) می‌آیند و در کد ثابت نیستند.
 *
 * @param stableThresholdPercent اگر تغییر کل خط روند در طول بازه (به‌صورت درصد از میانگین) کمتر از این باشد، روند ثابت است
 * @param strongR2               R² برابر یا بیشتر از این: اطمینان بالا
 * @param moderateR2             R² برابر یا بیشتر از این: اطمینان متوسط
 * @param minReliablePoints      با داده‌ی کمتر از این تعداد، اطمینان هرگز بالا/متوسط نمی‌شود
 * @param outlierMinPoints       حداقل تعداد داده برای شناسایی داده‌ی پرت
 * @param outlierModifiedZ       آستانه‌ی modified z-score (بر پایه‌ی MAD باقیمانده‌ها) برای علامت‌گذاری داده‌ی پرت
 */
public record TrendConfig(
        double stableThresholdPercent,
        double strongR2,
        double moderateR2,
        int minReliablePoints,
        int outlierMinPoints,
        double outlierModifiedZ
) {
    public TrendConfig withStableThresholdPercent(double value) {
        return new TrendConfig(value, strongR2, moderateR2, minReliablePoints, outlierMinPoints, outlierModifiedZ);
    }
}

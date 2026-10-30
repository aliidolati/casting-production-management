package com.factory.productmanager.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * پاسخ گزارش «روند تست کشش».
 *
 * <p>{@code samples} فهرست مشترک نمونه‌هاست (محور زمانی مشترک سه نمودار) و {@code series}
 * به ازای هر پارامتر یک تحلیل مستقل دارد. برای توسعه‌ی آینده (مقایسه‌ی بازه‌ها، SPC و ...)
 * کافی است سری‌ها یا فیلدهای جدید به همین ساختار اضافه شوند.
 *
 * @param strongR2    R² از این مقدار به بعد روند «قوی» محسوب می‌شود (برای برچسب UI)
 * @param moderateR2  R² از این مقدار به بعد روند «متوسط» محسوب می‌شود
 */
public record TensileTrendReportResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        int sampleCount,
        int movingAverageWindow,
        double strongR2,
        double moderateR2,
        int minReliablePoints,
        List<TensileTrendSampleResponse> samples,
        List<TensileTrendSeriesResponse> series
) {
}

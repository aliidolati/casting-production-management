package com.factory.productmanager.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * یک نقطه روی نمودار روند یک پارامتر. اطلاعات تکمیلی نمونه (برچسب، قطعه، سایر پارامترها)
 * در فهرست مشترک {@code samples} پاسخ است و با {@code sampleId} به این نقطه وصل می‌شود.
 *
 * @param trendValue    مقدار خط روند در زمان همین نقطه؛ null اگر روند محاسبه نشده
 * @param movingAverage میانگین متحرک؛ null اگر غیرفعال یا هنوز پنجره کامل نشده
 * @param outlier       فقط علامت‌گذاری است؛ نقطه از محاسبات حذف نشده
 */
public record TensileTrendPointResponse(
        UUID sampleId,
        LocalDateTime testedAt,
        double value,
        Double trendValue,
        Double movingAverage,
        boolean outlier
) {
}

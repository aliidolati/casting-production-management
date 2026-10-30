package com.factory.productmanager.model.dto;

import java.math.BigDecimal;

/**
 * حدود مجاز یک پارامتر. فعلاً فقط حداقل (min) از تعریف قطعه خوانده می‌شود؛
 * {@code max} و {@code target} برای توسعه‌ی آینده (SPC، UCL/LCL، Target) رزرو شده‌اند و تا
 * وقتی منبعی برایشان تعریف نشده null می‌مانند.
 */
public record TensileTrendLimitsResponse(
        BigDecimal min,
        BigDecimal max,
        BigDecimal target
) {
}

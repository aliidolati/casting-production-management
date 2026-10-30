package com.factory.productmanager.service.trend;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ردیف سبک (Projection) برای گزارش روند؛ فقط ستون‌های لازم از دیتابیس خوانده می‌شود،
 * نه Entityهای کامل با رابطه‌های Lazy.
 */
public record TensileTrendSampleRow(
        UUID sampleId,
        UUID tensileTestId,
        LocalDateTime testedAt,
        String sampleLabel,
        BigDecimal uts,
        BigDecimal ys,
        BigDecimal el,
        String projectCode,
        UUID pieceDefinitionId,
        String pieceName,
        Integer pieceNumber
) {
}

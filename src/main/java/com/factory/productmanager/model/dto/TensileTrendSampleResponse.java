package com.factory.productmanager.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** اطلاعات یک نمونه تست کشش (برای tooltip و نمایش هم‌زمان هر سه پارامتر). */
public record TensileTrendSampleResponse(
        UUID sampleId,
        UUID tensileTestId,
        LocalDateTime testedAt,
        String sampleLabel,
        String projectCode,
        String pieceName,
        Integer pieceNumber,
        BigDecimal uts,
        BigDecimal ys,
        BigDecimal el
) {
}

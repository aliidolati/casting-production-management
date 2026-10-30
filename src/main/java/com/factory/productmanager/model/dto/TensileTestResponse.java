package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TensileTestStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TensileTestResponse {
    private UUID id;
    private UUID pieceId;
    private Integer testIndex;
    private TensileTestStatus status;
    private String statusLabel;
    private boolean belowThreshold;
    /** پارامترهای زیر آستانه، مثلاً ["UTS", "EL"] */
    private List<String> thresholdFailures = new ArrayList<>();
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private List<TensileSampleDto> samples = new ArrayList<>();
    private TensileDispositionResponse disposition;
    /** آیا این تست هنوز قابل حذف است (هیچ تعیین‌تکلیف انسانی روی آن ثبت نشده) */
    private boolean canDelete;

    // آستانه‌های تعریف قطعه، تا UI بتواند مقدار مردود را کنار حد مجاز نشان دهد
    private BigDecimal minUts;
    private BigDecimal minYs;
    private BigDecimal minEl;
    private BigDecimal minHb;
}

package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.FurnaceSize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * خلاصه‌ی یک محموله‌ی کوره. فرانت‌اند از این استفاده می‌کند تا وقتی کاربر کد یک
 * محموله‌ی موجود را وارد کرد، پارامترهای سیکل را نمایش دهد و قفل کند.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HeatTreatmentBatchResponse {
    private UUID id;
    private String batchCode;
    private LocalDate treatmentDate;
    private BigDecimal temperatureC;
    private BigDecimal durationHours;
    private FurnaceSize furnaceSize;
    private String furnaceSizeLabel;
    private String notes;
    private long pieceCount;
    private LocalDateTime createdAt;
    private String createdBy;
    private List<HeatTreatmentBatchPieceResponse> pieces = new ArrayList<>();
}

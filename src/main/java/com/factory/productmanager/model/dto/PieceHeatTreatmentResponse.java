package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.FurnaceSize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieceHeatTreatmentResponse {
    private UUID id;
    private UUID pieceId;

    // ---- پارامترهای سیکل، از محموله ----
    private UUID batchId;
    private String batchCode;
    private LocalDate treatmentDate;
    private BigDecimal temperatureC;
    private BigDecimal durationHours;
    private FurnaceSize furnaceSize;
    private String furnaceSizeLabel;
    private String batchNotes;
    /** چند قطعه در همین محموله ثبت شده‌اند */
    private long batchPieceCount;

    // ---- داده‌های مخصوص همین قطعه ----
    /** چندمین بار عملیات روی همین قطعه */
    private String treatmentStage;
    private String testPlateLabel;
    private BigDecimal testPlateHardnessHb;
    private String details;

    private UUID tensileDispositionId;
    /** شماره‌ی تست کششی که این عملیات را تجویز کرده، برای نمایش در UI */
    private Integer tensileTestIndex;

    private LocalDateTime createdAt;
    private String createdBy;
    private boolean canDelete;
}

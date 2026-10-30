package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TensileInspectionResult;
import com.factory.productmanager.model.entity.TensileNextStage;
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
public class TensileDispositionResponse {
    private UUID id;
    private LocalDate dispositionDate;
    private Integer quantity;
    private BigDecimal durationHours;
    private BigDecimal temperatureC;
    private TensileNextStage nextStage;
    private String nextStageLabel;
    private TensileInspectionResult inspectionResult;
    private String inspectionResultLabel;
    private UUID responsibleUserId;
    private String responsibleUserName;
    /** تعیین‌تکلیف خودکار سیستم؛ در این حالت مسئول انسانی ندارد */
    private boolean systemGenerated;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}

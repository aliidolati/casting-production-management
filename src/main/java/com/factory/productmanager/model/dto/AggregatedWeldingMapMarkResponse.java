package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * نقطه جوش در نمای تجمیعی یک تعریف قطعه (همه شماره‌های همان ردیف روی یک نقشه).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedWeldingMapMarkResponse {
    private UUID drawingRevisionId;
    private UUID id;
    private UUID pieceId;
    private Integer pieceNumber;
    private Double xPercent;
    private Double yPercent;
    private String defectType;
    private String note;
    private String createdBy;
    private LocalDateTime createdAt;
}

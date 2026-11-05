package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeldingMapMarkResponse {
    private UUID drawingRevisionId;
    private UUID id;
    private Double xPercent;
    private Double yPercent;
    private String defectType;
    private String note;
    private String createdBy;
    private LocalDateTime createdAt;
}

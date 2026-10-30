package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiographyReportResponse {
    private UUID id;
    private UUID pieceId;
    private UUID templateId;
    private String templateName;
    private String partName;
    private String projectCode;
    private LocalDate reportDate;
    private String reportNumber;
    private String requestNumber;
    private String referenceStandard;
    private String status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<RadiographyReportLineResponse> lines;
}

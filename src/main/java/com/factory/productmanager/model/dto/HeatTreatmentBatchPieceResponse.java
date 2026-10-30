package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HeatTreatmentBatchPieceResponse {
    private UUID recordId;
    private UUID pieceId;
    private String projectCode;
    private Integer rowNum;
    private Integer pieceNumber;
    private String pieceName;
    private String treatmentStage;
    private String testPlateLabel;
    private BigDecimal testPlateHardnessHb;
    private String details;
}

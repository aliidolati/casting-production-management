package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TensileQualityPointResponse {
    private UUID sampleId;
    private String sampleLabel;
    private String projectCode;
    private String pieceName;
    private Integer pieceNumber;
    private BigDecimal el;
    private BigDecimal ys;
    private BigDecimal q;
    private String band;
}

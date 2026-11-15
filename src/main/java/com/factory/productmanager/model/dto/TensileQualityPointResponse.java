package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TensileTestStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private Integer testIndex;
    private LocalDateTime testedAt;
    private TensileTestStatus testStatus;
    private BigDecimal uts;
    private BigDecimal hb;
    private String sampleDescription;
}

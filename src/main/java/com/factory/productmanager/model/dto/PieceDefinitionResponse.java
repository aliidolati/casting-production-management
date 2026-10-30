package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PieceDefinitionResponse {
    private UUID id;
    private UUID projectId;
    private String projectCode;
    private Integer rowNum;
    private String pieceName;
    private LocalDateTime createdAt;
    private int pieceCount;
    private boolean hasDrawing;
    private BigDecimal minUts;
    private BigDecimal minYs;
    private BigDecimal minEl;
    private BigDecimal minHb;
}

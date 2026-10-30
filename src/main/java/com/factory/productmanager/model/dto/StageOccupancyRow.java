package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.SubStageKind;
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
public class StageOccupancyRow {
    private UUID pieceId;
    private String projectCode;
    private Integer rowNum;
    private String pieceName;
    private Integer pieceNumber;
    private PieceStage stage;
    private SubStageKind subStageKind;
    private String subStage;
    private LocalDateTime enteredAt;
    /** اگر هنوز در این مرحله باشد خالی است */
    private LocalDateTime leftAt;
    private boolean stillInStage;
}

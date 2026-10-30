package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.SubStageKind;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubStageOverdueAlertItem {
    private UUID pieceId;
    private String projectCode;
    private String pieceName;
    private Integer pieceNumber;
    private PieceStage currentStage;
    private SubStageKind kind;
    private String subStage;
    private long daysInSubStage;
}

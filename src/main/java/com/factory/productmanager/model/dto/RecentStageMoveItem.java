package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
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
public class RecentStageMoveItem {
    private UUID historyId;
    private UUID pieceId;
    private String projectCode;
    private String pieceName;
    private Integer pieceNumber;
    private PieceStage stage;
    private String changedBy;
    private LocalDateTime changedAt;
}

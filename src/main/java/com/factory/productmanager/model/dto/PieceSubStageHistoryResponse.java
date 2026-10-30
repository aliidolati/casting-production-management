package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.SubStageKind;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieceSubStageHistoryResponse {
    private UUID id;
    private SubStageKind kind;
    private String subStage;
    private LocalDateTime changedAt;
    private String changedBy;
    private LocalDate processDate;
}

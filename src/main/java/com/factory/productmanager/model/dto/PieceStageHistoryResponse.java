package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
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
public class PieceStageHistoryResponse {
    private UUID id;
    private PieceStage stage;
    private LocalDateTime changedAt;
    private String changedBy;
    /** Existing per-stage execution date; not a separately recorded date per visit. */
    private LocalDate processDate;
}

package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private long totalProjects;
    private long totalDefinitions;
    private long totalPieces;
    private Map<PieceStage, Long> stageCounts = new EnumMap<>(PieceStage.class);
}

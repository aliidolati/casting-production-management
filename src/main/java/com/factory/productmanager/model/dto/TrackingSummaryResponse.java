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
public class TrackingSummaryResponse {
    private long totalCount;
    private long inProgressCount;
    private long notStartedCount;
    private long deliveredCount;
    private Map<PieceStage, Long> stageCounts = new EnumMap<>(PieceStage.class);
}

package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStageSkip;
import java.time.LocalDateTime;
import java.util.UUID;

public record PieceStageSkipResponse(UUID id, UUID actorId, String actorUsername, String actorName,
        String fromStage, String fromSubStage, String toStage, String toSubStage, String reason, LocalDateTime occurredAt) {
    public static PieceStageSkipResponse from(PieceStageSkip event) {
        return new PieceStageSkipResponse(event.getId(),event.getActorId(),event.getActorUsername(),event.getActorName(),
                event.getFromStage(),event.getFromSubStage(),event.getToStage(),event.getToSubStage(),event.getReason(),event.getOccurredAt());
    }
}

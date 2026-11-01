package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.UserAccessEvent;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserAccessEventResponse(UUID id, UUID actorId, String actorUsername, UUID targetId,
        String action, LocalDateTime occurredAt, String beforeState, String afterState) {
    public static UserAccessEventResponse from(UserAccessEvent event) {
        return new UserAccessEventResponse(event.getId(), event.getActorId(), event.getActorUsername(),
                event.getTargetId(), event.getAction(), event.getOccurredAt(), event.getBeforeState(), event.getAfterState());
    }
}

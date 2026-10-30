package com.factory.productmanager.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChecklistItemResponse(
        UUID id,
        String title,
        boolean completed,
        UserRefResponse completedBy,
        LocalDateTime completedAt,
        int sortOrder
) {
}

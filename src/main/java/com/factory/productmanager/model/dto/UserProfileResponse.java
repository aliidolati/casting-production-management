package com.factory.productmanager.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String fullName,
        String role,
        String department,
        boolean active,
        LocalDateTime lastActivityAt,
        LocalDateTime createdAt,
        TaskStatsResponse stats,
        boolean canReceiveTasks
) {
}

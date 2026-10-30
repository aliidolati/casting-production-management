package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TaskActivityAction;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskActivityResponse(
        UUID id,
        TaskActivityAction action,
        UserRefResponse actor,
        String oldValue,
        String newValue,
        String note,
        LocalDateTime createdAt
) {
}

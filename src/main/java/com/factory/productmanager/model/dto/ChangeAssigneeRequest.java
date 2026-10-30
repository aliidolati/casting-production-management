package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeAssigneeRequest(
        @NotNull(message = "مسئول وظیفه الزامی است")
        UUID assigneeId
) {
}

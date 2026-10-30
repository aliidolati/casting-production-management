package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TaskPriority;
import jakarta.validation.constraints.NotNull;

public record ChangePriorityRequest(
        @NotNull(message = "اولویت الزامی است")
        TaskPriority priority
) {
}

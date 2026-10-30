package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRoughMachiningSubStageRequest {
    @NotNull(message = "زیرمرحله‌ی جدید نمی‌تواند خالی باشد")
    private RoughMachiningSubStage roughMachiningSubStage;
}

package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.RadiographySubStage;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRadiographySubStageRequest {
    @NotNull(message = "زیرمرحله‌ی جدید نمی‌تواند خالی باشد")
    private RadiographySubStage radiographySubStage;
}

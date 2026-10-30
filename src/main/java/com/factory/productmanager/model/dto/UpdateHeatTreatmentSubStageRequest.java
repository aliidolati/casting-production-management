package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateHeatTreatmentSubStageRequest {
    @NotNull(message = "زیرمرحله‌ی جدید نمی‌تواند خالی باشد")
    private HeatTreatmentSubStage heatTreatmentSubStage;
}

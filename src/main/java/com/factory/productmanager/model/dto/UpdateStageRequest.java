package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStageRequest {
    private ScrapRequest scrap;
    // Keep existing manager backward corrections atomic when their destination has a substage.
    private RadiographySubStage radiographySubStage;
    private HeatTreatmentSubStage heatTreatmentSubStage;
    private RoughMachiningSubStage roughMachiningSubStage;
    @NotNull(message = "وضعیت جدید نمی‌تواند خالی باشد")
    private PieceStage newStage ;
}

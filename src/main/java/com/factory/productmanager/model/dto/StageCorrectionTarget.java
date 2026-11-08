package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import jakarta.validation.constraints.NotNull;

public record StageCorrectionTarget(
        @NotNull PieceStage stage,
        RadiographySubStage radiographySubStage,
        HeatTreatmentSubStage heatTreatmentSubStage,
        RoughMachiningSubStage roughMachiningSubStage
) {}

package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.*;
import jakarta.validation.constraints.*;

public record SkipPieceStageRequest(
        @NotNull PieceStage stage,
        RadiographySubStage radiographySubStage,
        HeatTreatmentSubStage heatTreatmentSubStage,
        RoughMachiningSubStage roughMachiningSubStage,
        @NotBlank @Size(max=1000) String reason) {}

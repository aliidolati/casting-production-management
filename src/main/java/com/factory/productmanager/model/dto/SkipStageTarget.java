package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.*;

public record SkipStageTarget(PieceStage stage, RadiographySubStage radiographySubStage,
        HeatTreatmentSubStage heatTreatmentSubStage, RoughMachiningSubStage roughMachiningSubStage) {}

package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ForwardMoveResponse {
    private ForwardMoveKind kind;
    private PieceStage stage;
    private RadiographySubStage radiographySubStage;
    private HeatTreatmentSubStage heatTreatmentSubStage;
    private RoughMachiningSubStage roughMachiningSubStage;
    private String requiredPermission;
    private boolean processDateRequired;
}

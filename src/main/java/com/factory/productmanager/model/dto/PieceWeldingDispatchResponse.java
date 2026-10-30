package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieceWeldingDispatchResponse {
    private UUID id;
    private UUID pieceId;
    private String defectType;
    private String defectTypeCustom;
    private String notes;
    private LocalDateTime sentAt;
    private String sentBy;
    private LocalDateTime completedAt;
    private String completedBy;
    private boolean open;
    private List<UUID> defectIds = new ArrayList<>();
    private List<String> defectLabels = new ArrayList<>();
    private PieceStage originStage;
    private RadiographySubStage originRadiographySubStage;
    private HeatTreatmentSubStage originHeatTreatmentSubStage;
    private RoughMachiningSubStage originRoughMachiningSubStage;
}

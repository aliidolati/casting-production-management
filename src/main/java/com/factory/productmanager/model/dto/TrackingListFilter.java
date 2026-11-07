package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
public class TrackingListFilter {
    private final String keyword;
    private final String pieceName;
    private final String projectCode;
    private final PieceStage stage;
    private final LocalDateTime enteredFrom;
    private final LocalDateTime enteredToExclusive;
    private final RadiographySubStage radiographySubStage;
    private final HeatTreatmentSubStage heatTreatmentSubStage;
    private final RoughMachiningSubStage roughMachiningSubStage;
    private final String sortBy;
    private final String sortDir;
    private final boolean includeStageAndSubstage;
    private final boolean includeArchived;
}

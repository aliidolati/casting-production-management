package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.StageCorrectionTarget;
import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Production locations eligible for a manual correction. */
@Component
@RequiredArgsConstructor
public class StageCorrectionPolicy {
    private final StageTransitionPolicy transitions;
    private static final List<PieceStage> ROUTE = List.of(PieceStage.NOT_STARTED, PieceStage.CASTING,
            PieceStage.ROUGH_MACHINING, PieceStage.RADIOGRAPHY, PieceStage.HEAT_TREATMENT,
            PieceStage.TENSILE, PieceStage.SHOT_BLASTING, PieceStage.READY, PieceStage.DELIVERED);
    private static final List<HeatTreatmentSubStage> HEAT = List.of(HeatTreatmentSubStage.AWAITING_OPERATIONS,
            HeatTreatmentSubStage.STAGE_1, HeatTreatmentSubStage.STAGE_2, HeatTreatmentSubStage.CNC_SAMPLE);

    public StageCorrectionTarget current(Piece piece) {
        return target(piece.getCurrentStage(), piece.getRadiographySubStage(), piece.getHeatTreatmentSubStage(), piece.getRoughMachiningSubStage());
    }
    public StageCorrectionTarget target(PieceStage stage, RadiographySubStage radio, HeatTreatmentSubStage heat, RoughMachiningSubStage rough) {
        return new StageCorrectionTarget(stage,
                stage == PieceStage.RADIOGRAPHY ? (radio == null ? RadiographySubStage.SHOT : radio) : null,
                stage == PieceStage.HEAT_TREATMENT ? (heat == null ? HeatTreatmentSubStage.AWAITING_OPERATIONS : heat) : null,
                stage == PieceStage.ROUGH_MACHINING ? (rough == null ? RoughMachiningSubStage.DEBURRING : rough) : null);
    }
    public boolean isNormal(Piece piece, StageCorrectionTarget target) {
        var move = transitions.forwardMove(piece);
        return move != null && target.equals(target(move.getStage(), move.getRadiographySubStage(), move.getHeatTreatmentSubStage(), move.getRoughMachiningSubStage()));
    }
    public List<StageCorrectionTarget> correctionTargets(Piece piece, boolean manager) {
        var current = current(piece);
        if (current.stage() == PieceStage.WELDING
                || (!manager && (current.stage() == PieceStage.CASTING || current.stage() == PieceStage.SCRAPPED))) {
            return List.of();
        }
        return routeTargets().stream().filter(target -> !current.equals(target)).toList();
    }

    private List<StageCorrectionTarget> routeTargets() {
        List<StageCorrectionTarget> choices = new ArrayList<>();
        for (var stage : ROUTE) {
            switch (stage) {
                case ROUGH_MACHINING -> {
                    for (var sub : RoughMachiningSubStage.values()) choices.add(target(stage, null, null, sub));
                }
                case RADIOGRAPHY -> {
                    for (var sub : RadiographySubStage.values()) choices.add(target(stage, sub, null, null));
                }
                case HEAT_TREATMENT -> {
                    for (var sub : HEAT) choices.add(target(stage, null, sub, null));
                }
                default -> choices.add(target(stage, null, null, null));
            }
        }
        return choices;
    }
}

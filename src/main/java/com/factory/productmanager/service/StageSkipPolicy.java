package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.model.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Ordered production locations for skips and delegated backward corrections. */
@Component @RequiredArgsConstructor
public class StageSkipPolicy {
    private final StageTransitionPolicy transitions;
    private static final List<PieceStage> ROUTE = List.of(PieceStage.NOT_STARTED, PieceStage.CASTING,
            PieceStage.ROUGH_MACHINING, PieceStage.RADIOGRAPHY, PieceStage.HEAT_TREATMENT,
            PieceStage.TENSILE, PieceStage.SHOT_BLASTING, PieceStage.READY, PieceStage.DELIVERED);
    private static final List<HeatTreatmentSubStage> HEAT = List.of(HeatTreatmentSubStage.AWAITING_OPERATIONS,
            HeatTreatmentSubStage.STAGE_1, HeatTreatmentSubStage.STAGE_2, HeatTreatmentSubStage.CNC_SAMPLE);

    public SkipStageTarget current(Piece piece) {
        return target(piece.getCurrentStage(), piece.getRadiographySubStage(), piece.getHeatTreatmentSubStage(), piece.getRoughMachiningSubStage());
    }
    public SkipStageTarget target(PieceStage stage, RadiographySubStage radio, HeatTreatmentSubStage heat, RoughMachiningSubStage rough) {
        return new SkipStageTarget(stage,
                stage == PieceStage.RADIOGRAPHY ? (radio == null ? RadiographySubStage.SHOT : radio) : null,
                stage == PieceStage.HEAT_TREATMENT ? (heat == null ? HeatTreatmentSubStage.AWAITING_OPERATIONS : heat) : null,
                stage == PieceStage.ROUGH_MACHINING ? (rough == null ? RoughMachiningSubStage.DEBURRING : rough) : null);
    }
    public boolean isNormal(Piece piece, SkipStageTarget target) {
        var move = transitions.forwardMove(piece);
        return move != null && target.equals(target(move.getStage(), move.getRadiographySubStage(), move.getHeatTreatmentSubStage(), move.getRoughMachiningSubStage()));
    }
    public boolean isForward(Piece piece, SkipStageTarget target) {
        var from = current(piece);
        return ROUTE.contains(from.stage()) && ROUTE.contains(target.stage()) && position(target) > position(from);
    }
    private int position(SkipStageTarget target) {
        int sub = switch(target.stage()) {
            case ROUGH_MACHINING -> target.roughMachiningSubStage().ordinal();
            case RADIOGRAPHY -> target.radiographySubStage().ordinal();
            // Aging locations are decision branches; never offer them as manual skip destinations.
            case HEAT_TREATMENT -> HEAT.contains(target.heatTreatmentSubStage()) ? HEAT.indexOf(target.heatTreatmentSubStage()) : 3;
            default -> 0;
        };
        return ROUTE.indexOf(target.stage()) * 100 + sub;
    }
    public List<SkipStageTarget> targets(Piece piece) {
        return routeTargets().stream().filter(t -> isForward(piece,t) && !isNormal(piece,t)).toList();
    }

    public List<SkipStageTarget> correctionTargets(Piece piece) {
        var current = current(piece);
        if (!ROUTE.contains(current.stage()) || current.stage() == PieceStage.CASTING) return List.of();
        int currentPosition = position(current);
        return routeTargets().stream()
                .filter(target -> !current.equals(target) && position(target) <= currentPosition)
                .toList();
    }

    private List<SkipStageTarget> routeTargets() {
        List<SkipStageTarget> choices = new ArrayList<>();
        for (var stage : ROUTE) {
            if (stage == PieceStage.ROUGH_MACHINING) for (var sub : RoughMachiningSubStage.values()) choices.add(target(stage,null,null,sub));
            else if (stage == PieceStage.RADIOGRAPHY) for (var sub : RadiographySubStage.values()) choices.add(target(stage,sub,null,null));
            else if (stage == PieceStage.HEAT_TREATMENT) for (var sub : HEAT) choices.add(target(stage,null,sub,null));
            else choices.add(target(stage,null,null,null));
        }
        return choices;
    }
}

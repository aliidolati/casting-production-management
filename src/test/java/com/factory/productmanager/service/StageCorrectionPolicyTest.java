package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StageCorrectionPolicyTest {
    private final StageCorrectionPolicy policy = new StageCorrectionPolicy(new StageTransitionPolicy());

    @Test void correctionTargetsCoverBothDirectionsAndExcludeOperationalDestinations() {
        var piece = new Piece();
        piece.setCurrentStage(PieceStage.HEAT_TREATMENT);
        piece.setHeatTreatmentSubStage(HeatTreatmentSubStage.STAGE_2);
        var targets = policy.correctionTargets(piece, false);

        assertTrue(targets.contains(policy.target(PieceStage.HEAT_TREATMENT, null, HeatTreatmentSubStage.STAGE_1, null)));
        assertTrue(targets.contains(policy.target(PieceStage.HEAT_TREATMENT, null, HeatTreatmentSubStage.CNC_SAMPLE, null)));
        assertTrue(targets.contains(policy.target(PieceStage.DELIVERED, null, null, null)));
        assertFalse(targets.contains(policy.current(piece)));
        assertFalse(targets.stream().anyMatch(t -> t.stage() == PieceStage.WELDING || t.stage() == PieceStage.SCRAPPED
                || t.heatTreatmentSubStage() == HeatTreatmentSubStage.STAGE_3
                || t.heatTreatmentSubStage() == HeatTreatmentSubStage.STAGE_4
                || t.heatTreatmentSubStage() == HeatTreatmentSubStage.STAGE_5));
    }

    @Test void castingDepartureAndScrapReversalAreReservedForManager() {
        var piece = new Piece();
        piece.setCurrentStage(PieceStage.SCRAPPED);
        assertTrue(policy.correctionTargets(piece, false).isEmpty());
        assertFalse(policy.correctionTargets(piece, true).isEmpty());
        piece.setCurrentStage(PieceStage.CASTING);
        assertTrue(policy.correctionTargets(piece, false).isEmpty());
        assertFalse(policy.correctionTargets(piece, true).isEmpty());
        piece.setCurrentStage(PieceStage.WELDING);
        assertTrue(policy.correctionTargets(piece, true).isEmpty());
    }
}

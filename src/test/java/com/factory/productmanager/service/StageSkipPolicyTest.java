package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StageSkipPolicyTest {
    private final StageSkipPolicy policy=new StageSkipPolicy(new StageTransitionPolicy());
    @Test void onlyLaterNonNormalLocationsAreOffered() {
        var p=new Piece();p.setCurrentStage(PieceStage.ROUGH_MACHINING);p.setRoughMachiningSubStage(RoughMachiningSubStage.DEBURRING);
        var targets=policy.targets(p);
        assertFalse(targets.contains(policy.current(p)));
        assertFalse(targets.contains(policy.target(PieceStage.ROUGH_MACHINING,null,null,RoughMachiningSubStage.INITIAL_CORRECTION)));
        assertTrue(targets.contains(policy.target(PieceStage.ROUGH_MACHINING,null,null,RoughMachiningSubStage.MACHINING)));
        assertFalse(targets.stream().anyMatch(t->t.stage()==PieceStage.CASTING || t.stage()==PieceStage.SCRAPPED || t.stage()==PieceStage.WELDING));
    }
    @Test void heatBranchDoesNotUseEnumOrdinalAsProductionOrder() {
        var p=new Piece();p.setCurrentStage(PieceStage.HEAT_TREATMENT);p.setHeatTreatmentSubStage(HeatTreatmentSubStage.STAGE_1);
        var targets=policy.targets(p);
        assertTrue(targets.contains(policy.target(PieceStage.HEAT_TREATMENT,null,HeatTreatmentSubStage.CNC_SAMPLE,null)));
        assertFalse(targets.contains(policy.target(PieceStage.HEAT_TREATMENT,null,HeatTreatmentSubStage.STAGE_2,null)));
        assertFalse(targets.stream().anyMatch(t->t.stage()==PieceStage.HEAT_TREATMENT &&
                java.util.Set.of(HeatTreatmentSubStage.STAGE_3,HeatTreatmentSubStage.STAGE_4,HeatTreatmentSubStage.STAGE_5).contains(t.heatTreatmentSubStage())));
    }
    @Test void terminalAndWeldingLocationsHaveNoForwardSkipOptions() {
        for(var stage:java.util.List.of(PieceStage.WELDING,PieceStage.SCRAPPED,PieceStage.DELIVERED)) {
            var p=new Piece();p.setCurrentStage(stage);assertTrue(policy.targets(p).isEmpty());
        }
    }
}

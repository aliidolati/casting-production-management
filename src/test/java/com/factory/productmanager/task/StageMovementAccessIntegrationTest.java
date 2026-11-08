package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StageMovementAccessIntegrationTest extends TaskTestBase {
    private Piece piece(PieceStage stage) {
        var p = newPiece("STAGE-"+UUID.randomUUID(),"قطعه ساختگی",1);
        p.setCurrentStage(stage);
        if(stage==PieceStage.ROUGH_MACHINING) p.setRoughMachiningSubStage(RoughMachiningSubStage.DEBURRING);
        if(stage==PieceStage.RADIOGRAPHY) p.setRadiographySubStage(RadiographySubStage.SHOT);
        return pieceRepository.saveAndFlush(p);
    }
    private User actor(Role role, Permission... grants) {
        return newUser("stage-user-"+UUID.randomUUID(),role,Set.of(grants));
    }

    @Test void independentAdvanceGrantAllowsOneStepButNoOtherLegacyPrivileges() throws Exception {
        var operator=actor(Role.OPERATOR,Permission.ADVANCE_PIECE_STAGE);
        var p=piece(PieceStage.NOT_STARTED);
        call(post("/api/pieces/"+p.getId()+"/advance"),operator,Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("CASTING"));
        call(patch("/api/pieces/"+p.getId()+"/process-date"),operator,Map.of("processDate","2026-11-01")).andExpect(status().isForbidden());
        call(post("/api/pieces/"+p.getId()+"/welding-station"),operator,Map.of("defectType","CR")).andExpect(status().isForbidden());
        call(patch("/api/pieces/"+p.getId()+"/stage"),operator,Map.of("newStage","SCRAPPED","scrap",Map.of("defectType","CR"))).andExpect(status().isForbidden());
        var legacy=actor(Role.OPERATOR,Permission.MOVE_PIECE_STAGE,Permission.UPDATE_ROUGH_MACHINING_SUBSTAGE);
        call(post("/api/pieces/"+p.getId()+"/advance"),legacy,Map.of()).andExpect(status().isForbidden());
    }
    @Test void normalGrantWorksForSubstagesAndLegacyBulkPathsWithoutGrantingCorrection() throws Exception {
        var operator=actor(Role.OPERATOR,Permission.ADVANCE_PIECE_STAGE);
        var a=piece(PieceStage.ROUGH_MACHINING);var b=piece(PieceStage.ROUGH_MACHINING);
        call(patch("/api/pieces/bulk/rough-machining-substage"),operator,Map.of("pieceIds",List.of(a.getId(),b.getId()),"roughMachiningSubStage","INITIAL_CORRECTION"))
                .andExpect(status().isOk());
        call(post("/api/pieces/"+a.getId()+"/advance"),operator,Map.of("processDate","2026-11-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roughMachiningSubStage").value("MACHINING"));
        call(patch("/api/pieces/"+b.getId()+"/stage"),operator,Map.of("newStage","RADIOGRAPHY")).andExpect(status().isBadRequest());
        call(patch("/api/pieces/"+b.getId()+"/stage-correction"),operator,Map.of("stage","READY")).andExpect(status().isForbidden());
    }
    @Test void retiredSkipIsAbsentFromRoutesAndPermissionCatalog() throws Exception {
        var legacyAdmin = actor(Role.ADMIN, Permission.SKIP_PIECE_STAGE);
        var piece = piece(PieceStage.NOT_STARTED);
        var api = "/api/pieces/" + piece.getId();
        call(get("/api/permissions"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'SKIP_PIECE_STAGE')]").isEmpty());
        call(get(api + "/skip-targets"), admin).andExpect(status().isNotFound());
        call(get(api + "/stage-skips"), admin).andExpect(status().isNotFound());
        call(post(api + "/skip"), admin, Map.of("stage", "READY")).andExpect(status().isNotFound());
        call(patch(api + "/stage-correction"), legacyAdmin, Map.of("stage", "READY"))
                .andExpect(status().isForbidden());
        assertEquals(PieceStage.NOT_STARTED, pieceRepository.findById(piece.getId()).orElseThrow().getCurrentStage());
    }

    @Test void legacySingleAndBulkEndpointsCannotBeUsedForNonNormalCorrection() throws Exception {
        var a=piece(PieceStage.NOT_STARTED);var b=piece(PieceStage.NOT_STARTED);
        call(patch("/api/pieces/"+a.getId()+"/stage"),admin,Map.of("newStage","READY")).andExpect(status().isBadRequest());
        call(patch("/api/pieces/bulk/stage"),admin,Map.of("pieceIds",List.of(a.getId(),b.getId()),"newStage","READY")).andExpect(status().isBadRequest());
        var r=piece(PieceStage.RADIOGRAPHY);
        call(patch("/api/pieces/"+r.getId()+"/radiography-substage"),admin,Map.of("radiographySubStage","VT")).andExpect(status().isBadRequest());
        assertEquals(PieceStage.NOT_STARTED,pieceRepository.findById(a.getId()).orElseThrow().getCurrentStage());
        assertEquals(RadiographySubStage.SHOT,pieceRepository.findById(r.getId()).orElseThrow().getRadiographySubStage());
    }
    @Test void managerCanCorrectInBothDirectionsWithTypedSubstages() throws Exception {
        var p = piece(PieceStage.READY);
        var api = "/api/pieces/" + p.getId() + "/stage-correction";
        call(patch(api), admin, Map.of("stage", "RADIOGRAPHY", "radiographySubStage", "VT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("RADIOGRAPHY"))
                .andExpect(jsonPath("$.radiographySubStage").value("VT"));
        call(patch(api), admin, Map.of("stage", "DELIVERED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("DELIVERED"));
    }

    @Test void managerCanGrantAndRevokeIndependentStatusCorrectionForAdmin() throws Exception {
        var delegate = actor(Role.ADMIN);
        var p = piece(PieceStage.READY);
        var api = "/api/pieces/" + p.getId() + "/stage-correction";
        var backward = Map.of("stage","RADIOGRAPHY","radiographySubStage","VT");

        call(patch(api),delegate,backward).andExpect(status().isForbidden());
        call(patch("/api/users/"+delegate.getId()+"/permissions"),admin,
                Map.of("permissions",List.of("CORRECT_PIECE_STAGE"))).andExpect(status().isOk());
        call(get(api+"/targets"),delegate).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.stage == 'RADIOGRAPHY' && @.radiographySubStage == 'VT')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.stage == 'SCRAPPED' || @.stage == 'WELDING')]").isEmpty());
        call(patch(api),delegate,Map.of("stage","DELIVERED")).andExpect(status().isOk());
        call(patch(api),delegate,Map.of("stage","SCRAPPED")).andExpect(status().isBadRequest());
        call(post("/api/pieces/"+p.getId()+"/advance"),delegate,Map.of()).andExpect(status().isForbidden());
        call(patch("/api/pieces/bulk/stage"),delegate,Map.of("pieceIds",List.of(p.getId()),"newStage","TENSILE"))
                .andExpect(status().isForbidden());

        call(patch(api),delegate,backward).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("RADIOGRAPHY"))
                .andExpect(jsonPath("$.radiographySubStage").value("VT"));
        call(patch(api),delegate,Map.of("stage","RADIOGRAPHY","radiographySubStage","SHOT")).andExpect(status().isOk())
                .andExpect(jsonPath("$.radiographySubStage").value("SHOT"));
        call(patch(api),delegate,Map.of("stage","RADIOGRAPHY","radiographySubStage","VT")).andExpect(status().isOk());

        call(patch("/api/users/"+delegate.getId()+"/permissions"),admin,Map.of("permissions",List.of())).andExpect(status().isOk());
        call(get(api+"/targets"),delegate).andExpect(status().isForbidden());
        call(patch(api),delegate,Map.of("stage","CASTING")).andExpect(status().isForbidden());
    }

    @Test void statusCorrectionKeepsRoleCastingArchiveAndReviewGuards() throws Exception {
        var delegate = actor(Role.ADMIN,Permission.CORRECT_PIECE_STAGE);
        var operator = actor(Role.OPERATOR,Permission.CORRECT_PIECE_STAGE);
        var casting = piece(PieceStage.CASTING);
        var castingApi = "/api/pieces/" + casting.getId() + "/stage-correction";
        call(get(castingApi+"/targets"),delegate).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        call(patch(castingApi),delegate,Map.of("stage","NOT_STARTED")).andExpect(status().isBadRequest());
        call(patch("/api/users/"+outsider.getId()+"/permissions"),admin,
                Map.of("permissions",List.of("CORRECT_PIECE_STAGE"))).andExpect(status().isBadRequest());

        var p = piece(PieceStage.RADIOGRAPHY);
        p.setRadiographySubStage(RadiographySubStage.INTERPRETATION);
        pieceRepository.saveAndFlush(p);
        var api = "/api/pieces/" + p.getId() + "/stage-correction";
        var backward = Map.of("stage","RADIOGRAPHY","radiographySubStage","SHOT");
        call(patch(api),operator,backward).andExpect(status().isForbidden());
        call(patch(api), delegate, Map.of("stage", "HEAT_TREATMENT", "heatTreatmentSubStage", "STAGE_5"))
                .andExpect(status().isBadRequest());
        p.setArchivedAt(java.time.LocalDateTime.now());
        pieceRepository.saveAndFlush(p);
        call(patch(api),delegate,backward).andExpect(status().isBadRequest());
        p.setArchivedAt(null);
        pieceRepository.saveAndFlush(p);
        var interpreter = actor(Role.OPERATOR,Permission.INTERPRET_RADIOGRAPHY);
        call(post("/api/radiography/pieces/"+p.getId()+"/workflow/assign"),admin,Map.of("interpreterId",interpreter.getId()))
                .andExpect(status().isCreated());
        call(patch(api),delegate,backward).andExpect(status().isBadRequest());
        assertEquals(RadiographySubStage.INTERPRETATION,p.getRadiographySubStage());
    }

    @Test void oneCorrectionGrantMovesABatchForwardAndBackwardAtomically() throws Exception {
        var delegate = actor(Role.ADMIN, Permission.CORRECT_PIECE_STAGE);
        var operator = actor(Role.OPERATOR, Permission.CORRECT_PIECE_STAGE);
        var first = piece(PieceStage.NOT_STARTED);
        var second = piece(PieceStage.READY);
        var ids = List.of(first.getId(), second.getId());
        var api = "/api/pieces/bulk/stage-correction";
        var forward = Map.of("stage", "DELIVERED");
        var backward = Map.of("stage", "HEAT_TREATMENT", "heatTreatmentSubStage", "STAGE_2");
        var request = Map.of("pieceIds", ids, "target", forward);

        call(post(api + "/targets"), operator, Map.of("pieceIds", ids)).andExpect(status().isForbidden());
        call(patch(api), operator, request).andExpect(status().isForbidden());
        call(post(api + "/targets"), delegate, Map.of("pieceIds", ids)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.stage == 'DELIVERED')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.stage == 'READY' || @.stage == 'NOT_STARTED')]").isEmpty());

        call(patch(api), delegate, Map.of("pieceIds", ids, "target", Map.of("stage", "READY")))
                .andExpect(status().isBadRequest());
        assertEquals(PieceStage.NOT_STARTED, pieceRepository.findById(first.getId()).orElseThrow().getCurrentStage());
        assertEquals(PieceStage.READY, pieceRepository.findById(second.getId()).orElseThrow().getCurrentStage());

        call(patch(api), delegate, request).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].currentStage").value(contains("DELIVERED", "DELIVERED")));
        call(patch(api), delegate, Map.of("pieceIds", ids, "target", backward)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].heatTreatmentSubStage").value(contains("STAGE_2", "STAGE_2")));
        for (var id : ids) {
            call(get("/api/pieces/" + id + "/stage-history"), delegate).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].stage").value("HEAT_TREATMENT"))
                    .andExpect(jsonPath("$[0].changedBy").value(delegate.getFullName()));
        }
    }
}

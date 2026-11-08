package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.PieceStageSkipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StageMovementAccessIntegrationTest extends TaskTestBase {
    @Autowired PieceStageSkipRepository skips;
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
    private Map<String,Object> skip(PieceStage stage) { return Map.of("stage",stage.name(),"reason","اصلاح مسیر با مجوز"); }

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
    @Test void normalGrantWorksForSubstagesAndLegacyBulkPathsWithoutGrantingSkip() throws Exception {
        var operator=actor(Role.OPERATOR,Permission.ADVANCE_PIECE_STAGE);
        var a=piece(PieceStage.ROUGH_MACHINING);var b=piece(PieceStage.ROUGH_MACHINING);
        call(patch("/api/pieces/bulk/rough-machining-substage"),operator,Map.of("pieceIds",List.of(a.getId(),b.getId()),"roughMachiningSubStage","INITIAL_CORRECTION"))
                .andExpect(status().isOk());
        call(post("/api/pieces/"+a.getId()+"/advance"),operator,Map.of("processDate","2026-11-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roughMachiningSubStage").value("MACHINING"));
        call(patch("/api/pieces/"+b.getId()+"/stage"),operator,Map.of("newStage","RADIOGRAPHY")).andExpect(status().isBadRequest());
        call(post("/api/pieces/"+b.getId()+"/skip"),operator,skip(PieceStage.READY)).andExpect(status().isForbidden());
    }
    @Test void permittedAdminSkipIsAtomicAndRecordsActorOriginDestinationAndReason() throws Exception {
        var administrator=actor(Role.ADMIN,Permission.SKIP_PIECE_STAGE);
        var p=piece(PieceStage.NOT_STARTED);
        call(post("/api/pieces/"+p.getId()+"/advance"),administrator,Map.of()).andExpect(status().isForbidden());
        call(get("/api/pieces/"+p.getId()+"/skip-targets"),administrator).andExpect(status().isOk());
        call(post("/api/pieces/"+p.getId()+"/skip"),administrator,Map.of("stage","ROUGH_MACHINING","roughMachiningSubStage","MACHINING","reason","  اصلاح مسیر  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("ROUGH_MACHINING"))
                .andExpect(jsonPath("$.roughMachiningSubStage").value("MACHINING"));
        call(get("/api/pieces/"+p.getId()+"/stage-skips"),administrator).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actorId").value(administrator.getId().toString()))
                .andExpect(jsonPath("$[0].fromStage").value("NOT_STARTED"))
                .andExpect(jsonPath("$[0].toSubStage").value("MACHINING"))
                .andExpect(jsonPath("$[0].reason").value("اصلاح مسیر"));
        assertEquals(1,skips.findByPieceIdOrderByOccurredAtDesc(p.getId()).size());
    }
    @Test void operatorCannotSkipEvenWithPersistedGrantAndAdminWithoutGrantCannotSkip() throws Exception {
        var p=piece(PieceStage.NOT_STARTED);
        for(var actor:List.of(actor(Role.OPERATOR,Permission.SKIP_PIECE_STAGE),actor(Role.ADMIN))) {
            call(post("/api/pieces/"+p.getId()+"/skip"),actor,skip(PieceStage.READY)).andExpect(status().isForbidden());
            call(get("/api/pieces/"+p.getId()+"/skip-targets"),actor).andExpect(status().isForbidden());
        }
        assertTrue(skips.findByPieceIdOrderByOccurredAtDesc(p.getId()).isEmpty());
    }
    @Test void evenManagerNeedsReasonAndCannotTreatNormalBackwardScrapWeldingOrAgingAsSkip() throws Exception {
        var p=piece(PieceStage.RADIOGRAPHY);
        call(post("/api/pieces/"+p.getId()+"/skip"),admin,Map.of("stage","READY")).andExpect(status().isBadRequest());
        call(post("/api/pieces/"+p.getId()+"/skip"),admin,Map.of("stage","READY","reason"," ")).andExpect(status().isBadRequest());
        for(var target:List.of(PieceStage.RADIOGRAPHY,PieceStage.CASTING,PieceStage.SCRAPPED,PieceStage.WELDING))
            call(post("/api/pieces/"+p.getId()+"/skip"),admin,skip(target)).andExpect(status().isBadRequest());
        call(post("/api/pieces/"+p.getId()+"/skip"),admin,Map.of("stage","RADIOGRAPHY","radiographySubStage","INTERPRETATION","reason","قدم عادی"))
                .andExpect(status().isBadRequest());
        call(post("/api/pieces/"+p.getId()+"/skip"),admin,Map.of("stage","HEAT_TREATMENT","heatTreatmentSubStage","STAGE_5","reason","پیرسازی"))
                .andExpect(status().isBadRequest());
        assertTrue(skips.findByPieceIdOrderByOccurredAtDesc(p.getId()).isEmpty());
    }
    @Test void legacySingleAndBulkEndpointsCannotBypassForwardSkipReasonForManager() throws Exception {
        var a=piece(PieceStage.NOT_STARTED);var b=piece(PieceStage.NOT_STARTED);
        call(patch("/api/pieces/"+a.getId()+"/stage"),admin,Map.of("newStage","READY")).andExpect(status().isBadRequest());
        call(patch("/api/pieces/bulk/stage"),admin,Map.of("pieceIds",List.of(a.getId(),b.getId()),"newStage","READY")).andExpect(status().isBadRequest());
        var r=piece(PieceStage.RADIOGRAPHY);
        call(patch("/api/pieces/"+r.getId()+"/radiography-substage"),admin,Map.of("radiographySubStage","VT")).andExpect(status().isBadRequest());
        assertEquals(PieceStage.NOT_STARTED,pieceRepository.findById(a.getId()).orElseThrow().getCurrentStage());
        assertEquals(RadiographySubStage.SHOT,pieceRepository.findById(r.getId()).orElseThrow().getRadiographySubStage());
        assertTrue(skips.findByPieceIdOrderByOccurredAtDesc(a.getId()).isEmpty());
    }
    @Test void castingApprovalAndArchiveLocksStillApplyToAdminSkip() throws Exception {
        var administrator=actor(Role.ADMIN,Permission.SKIP_PIECE_STAGE);
        var p=piece(PieceStage.CASTING);
        call(post("/api/pieces/"+p.getId()+"/skip"),administrator,skip(PieceStage.READY)).andExpect(status().isBadRequest());
        assertEquals(PieceStage.CASTING,pieceRepository.findById(p.getId()).orElseThrow().getCurrentStage());
        p.setArchivedAt(java.time.LocalDateTime.now());pieceRepository.saveAndFlush(p);
        call(post("/api/pieces/"+p.getId()+"/skip"),administrator,skip(PieceStage.READY)).andExpect(status().isBadRequest());
        assertTrue(skips.findByPieceIdOrderByOccurredAtDesc(p.getId()).isEmpty());
    }
    @Test void openRadiographyWorkflowCannotBeSkippedEvenByManager() throws Exception {
        var p=piece(PieceStage.RADIOGRAPHY);p.setRadiographySubStage(RadiographySubStage.INTERPRETATION);pieceRepository.saveAndFlush(p);
        var interpreter=actor(Role.OPERATOR,Permission.INTERPRET_RADIOGRAPHY);
        call(post("/api/radiography/pieces/"+p.getId()+"/workflow/assign"),admin,Map.of("interpreterId",interpreter.getId()))
                .andExpect(status().isCreated());
        call(post("/api/pieces/"+p.getId()+"/skip"),admin,skip(PieceStage.READY)).andExpect(status().isBadRequest());
        assertTrue(skips.findByPieceIdOrderByOccurredAtDesc(p.getId()).isEmpty());
    }
    @Test void existingManagerBackwardCorrectionWithSubstageRemainsAtomicAndIsNotLoggedAsSkip() throws Exception {
        var p=piece(PieceStage.READY);
        call(patch("/api/pieces/"+p.getId()+"/stage"),admin,
                Map.of("newStage","RADIOGRAPHY","radiographySubStage","VT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("RADIOGRAPHY"))
                .andExpect(jsonPath("$.radiographySubStage").value("VT"));
        assertTrue(skips.findByPieceIdOrderByOccurredAtDesc(p.getId()).isEmpty());
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
                .andExpect(jsonPath("$[?(@.stage == 'DELIVERED' || @.stage == 'SCRAPPED' || @.stage == 'WELDING')]").isEmpty());
        call(patch(api),delegate,Map.of("stage","DELIVERED")).andExpect(status().isBadRequest());
        call(patch(api),delegate,Map.of("stage","SCRAPPED")).andExpect(status().isBadRequest());
        call(post("/api/pieces/"+p.getId()+"/advance"),delegate,Map.of()).andExpect(status().isForbidden());
        call(post("/api/pieces/"+p.getId()+"/skip"),delegate,skip(PieceStage.DELIVERED)).andExpect(status().isForbidden());
        call(patch("/api/pieces/bulk/stage"),delegate,Map.of("pieceIds",List.of(p.getId()),"newStage","TENSILE"))
                .andExpect(status().isForbidden());

        call(patch(api),delegate,backward).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("RADIOGRAPHY"))
                .andExpect(jsonPath("$.radiographySubStage").value("VT"));
        call(patch(api),delegate,Map.of("stage","RADIOGRAPHY","radiographySubStage","SHOT")).andExpect(status().isOk())
                .andExpect(jsonPath("$.radiographySubStage").value("SHOT"));
        call(patch(api),delegate,Map.of("stage","RADIOGRAPHY","radiographySubStage","INTERPRETATION")).andExpect(status().isBadRequest());
        assertTrue(skips.findByPieceIdOrderByOccurredAtDesc(p.getId()).isEmpty());

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

    @Test void managerCannotAssignSkipPermissionToOperator() throws Exception {
        call(patch("/api/users/"+outsider.getId()+"/permissions"),admin,Map.of("permissions",List.of("SKIP_PIECE_STAGE")))
                .andExpect(status().isBadRequest());
        assertFalse(userRepository.findById(outsider.getId()).orElseThrow().getPermissions().contains(Permission.SKIP_PIECE_STAGE));
    }

}

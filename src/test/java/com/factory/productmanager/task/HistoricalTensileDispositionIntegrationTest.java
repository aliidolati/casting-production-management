package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.PieceStageHistoryRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HistoricalTensileDispositionIntegrationTest extends TaskTestBase {
    @Autowired PieceStageHistoryRepository histories;
    private Piece piece() {
        var p=newPiece("HISTORY-"+UUID.randomUUID(),"قطعه آزمایشی",1);
        p.getPieceDefinition().setMinUts(new BigDecimal("300"));definitionRepository.saveAndFlush(p.getPieceDefinition());
        p.setCurrentStage(PieceStage.TENSILE);return pieceRepository.saveAndFlush(p);
    }
    private String test(Piece p,int uts) throws Exception {
        return JsonPath.read(call(post("/api/pieces/"+p.getId()+"/tensile-tests"),admin,
                Map.of("samples",List.of(Map.of("uts",uts,"ys",90,"el",2))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.id");
    }
    private Map<String,Object> decision(String next) {
        Map<String,Object> body=new HashMap<>();body.put("dispositionDate","2026-11-01");body.put("nextStage",next);
        body.put("inspectionResult",next.equals("APPROVED")?"ACCEPTED":"REJECTED");body.put("durationHours",1);return body;
    }
    private Piece reload(Piece p) { pieceRepository.flush();return pieceRepository.findById(p.getId()).orElseThrow(); }

    @Test void editingHistoricalDecisionCannotRerouteDownstreamPieceEvenForManager() throws Exception {
        var reviewer=newUser("tensile-review-"+UUID.randomUUID(),Role.OPERATOR,Set.of(Permission.REVIEW_TENSILE_TEST));
        for(User as:List.of(admin,reviewer)) {
            var p=piece();String id=test(p,100);
            call(put("/api/tensile-tests/"+id+"/disposition"),as,decision("RE_OPERATION")).andExpect(status().isOk());
            p=reload(p);assertEquals(PieceStage.HEAT_TREATMENT,p.getCurrentStage());
            p.setCurrentStage(PieceStage.READY);p.setProcessDate(LocalDate.of(2026,10,25));pieceRepository.saveAndFlush(p);
            long count=histories.findByPieceIdOrderByChangedAtDesc(p.getId()).size();
            call(put("/api/tensile-tests/"+id+"/disposition"),as,decision("AGING")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.disposition.nextStage").value("AGING"));
            p=reload(p);assertEquals(PieceStage.READY,p.getCurrentStage());assertEquals(LocalDate.of(2026,10,25),p.getProcessDate());
            assertEquals(count,histories.findByPieceIdOrderByChangedAtDesc(p.getId()).size());
        }
    }
    @Test void explicitMovementIsRejectedForDownstreamOrArchivedPieceBeforeEditingDecision() throws Exception {
        var p=piece();String id=test(p,100);
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,decision("RE_OPERATION")).andExpect(status().isOk());
        var body=decision("APPROVED");body.put("applyStageMove",true);
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,body).andExpect(status().isBadRequest());
        assertEquals(PieceStage.HEAT_TREATMENT,reload(p).getCurrentStage());
        call(get("/api/pieces/"+p.getId()+"/tensile-tests"),admin).andExpect(jsonPath("$[0].disposition.nextStage").value("RE_OPERATION"));
        p=reload(p);p.setCurrentStage(PieceStage.TENSILE);p.setArchivedAt(LocalDateTime.now());pieceRepository.saveAndFlush(p);
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,body).andExpect(status().isBadRequest());
    }
    @Test
    void explicitlyApplyingAnOlderDecisionMovesOnlyThePieceAndSavesTheSelectedTest() throws Exception {
        Piece piece = piece();
        String olderTest = test(piece, 100);
        String newerTest = test(piece, 110);
        String testsUrl = "/api/pieces/" + piece.getId() + "/tensile-tests";
        String dispositionUrl = "/api/tensile-tests/" + olderTest + "/disposition";

        call(put(dispositionUrl), admin, decision("RE_OPERATION")).andExpect(status().isOk());
        assertEquals(PieceStage.TENSILE, reload(piece).getCurrentStage());
        assertTrue(histories.findByPieceIdOrderByChangedAtDesc(piece.getId()).isEmpty());

        var saveAndMove = decision("AGING");
        saveAndMove.put("applyStageMove", true);
        call(put(dispositionUrl), admin, saveAndMove).andExpect(status().isOk())
                .andExpect(jsonPath("$.disposition.nextStage").value("AGING"));

        Piece moved = reload(piece);
        assertEquals(PieceStage.HEAT_TREATMENT, moved.getCurrentStage());
        assertEquals(HeatTreatmentSubStage.STAGE_3, moved.getHeatTreatmentSubStage());
        assertEquals(1, histories.findByPieceIdOrderByChangedAtDesc(piece.getId()).size());
        call(get(testsUrl), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[1].id").value(newerTest))
                .andExpect(jsonPath("$[1].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$[1].disposition").doesNotExist());
    }
    @Test void editingCurrentDecisionRequiresSeparateExplicitChoiceToMovePiece() throws Exception {
        var p=piece();String id=test(p,100);
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,decision("NEXT_TENSILE")).andExpect(status().isOk());
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,decision("APPROVED")).andExpect(status().isOk());
        assertEquals(PieceStage.TENSILE,reload(p).getCurrentStage());
        var explicit=decision("APPROVED");explicit.put("applyStageMove",true);
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,explicit).andExpect(status().isOk());
        assertEquals(PieceStage.SHOT_BLASTING,reload(p).getCurrentStage());
        assertEquals(1,histories.findByPieceIdOrderByChangedAtDesc(p.getId()).size());
    }
    @Test void firstDecisionOnHistoricalPendingTestOnlyRecordsData() throws Exception {
        var p=piece();String id=test(p,100);p=reload(p);p.setCurrentStage(PieceStage.DELIVERED);pieceRepository.saveAndFlush(p);
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,decision("RE_OPERATION")).andExpect(status().isOk());
        assertEquals(PieceStage.DELIVERED,reload(p).getCurrentStage());
        assertTrue(histories.findByPieceIdOrderByChangedAtDesc(p.getId()).isEmpty());
    }
    @Test void freshCurrentDecisionRetainsExistingAutomaticMovementBehavior() throws Exception {
        var p=piece();String id=test(p,100);
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,decision("AGING")).andExpect(status().isOk());
        p=reload(p);assertEquals(PieceStage.HEAT_TREATMENT,p.getCurrentStage());assertEquals(HeatTreatmentSubStage.STAGE_3,p.getHeatTreatmentSubStage());
    }
    @Test void systemApprovedDecisionCorrectionDoesNotReverseAutomaticMovement() throws Exception {
        var p=piece();String id=test(p,400);assertEquals(PieceStage.SHOT_BLASTING,reload(p).getCurrentStage());
        long count=histories.findByPieceIdOrderByChangedAtDesc(p.getId()).size();
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,decision("AGING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.disposition.systemGenerated").value(false));
        assertEquals(PieceStage.SHOT_BLASTING,reload(p).getCurrentStage());assertEquals(count,histories.findByPieceIdOrderByChangedAtDesc(p.getId()).size());
    }
    @Test void explicitRecordOnlySaveDoesNotMoveCurrentPiece() throws Exception {
        var p=piece();String id=test(p,100);var body=decision("APPROVED");body.put("applyStageMove",false);
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,body).andExpect(status().isOk());
        assertEquals(PieceStage.TENSILE,reload(p).getCurrentStage());
    }
}

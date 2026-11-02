package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.service.PieceService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// The NOT_SUPPORTED rollback regressions commit their synthetic fixtures deliberately.
// Keep this context separate from tracking tests which assert global piece counts.
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:scrap_workflow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "app.upload.dir=./target/test-uploads/scrap-workflow"})
class ScrapIntegrationTest extends TaskTestBase {
    @Autowired PieceScrapDispatchRepository scraps;
    @Autowired PieceService pieceService;
    @Autowired PieceStageHistoryRepository histories;
    Piece piece() { return newPiece("SCR"+UUID.randomUUID().toString().substring(0,8),"Synthetic scrap",1); }
    Map<String,Object> reason() { return Map.of("defectType","ابعادی"); }
    String scrap(Piece p) throws Exception {
        call(patch("/api/pieces/"+p.getId()+"/stage"),admin,Map.of("newStage","SCRAPPED","scrap",reason()))
            .andExpect(status().isOk());
        return call(get("/api/pieces/"+p.getId()+"/scrap-dispatches"),admin)
            .andReturn().getResponse().getContentAsString();
    }
    @Test void everyReasonUsesTheWeldingCatalogAndNotesAreOptional() throws Exception {
        for (String type: DefectTypes.STANDARD) {
            Piece p=piece();
            call(patch("/api/pieces/"+p.getId()+"/stage"),admin,Map.of("newStage","SCRAPPED","scrap",Map.of("defectType",type)))
                .andExpect(status().isOk());
            assertEquals(type,scraps.findByActivePieceKey(p.getId()).orElseThrow().getDefectType());
        }
    }
    @Test void directAdminAndTrustedMovesCannotOmitOrInventAReason() throws Exception {
        Piece p=piece();
        for (var body: List.of(Map.of("newStage","SCRAPPED"),Map.of("newStage","SCRAPPED","scrap",Map.of("defectType","OTHER")),
                Map.of("newStage","SCRAPPED","scrap",Map.of("defectType","invented")))) {
            call(patch("/api/pieces/"+p.getId()+"/stage"),admin,body).andExpect(status().isBadRequest());
        }
        assertThrows(IllegalArgumentException.class,()->pieceService.applyTrustedStageMove(p.getId(),PieceStage.SCRAPPED,null,null,null,admin.getUsername()));
        assertEquals(PieceStage.NOT_STARTED,pieceRepository.findById(p.getId()).orElseThrow().getCurrentStage());
        assertTrue(scraps.findByPieceIdOrderBySentAtDesc(p.getId()).isEmpty());
    }
    @Test void editChangesReasonButPreservesOriginalTimeActorAndOrigin() throws Exception {
        Piece p=piece(); scrap(p);
        var before=scraps.findByActivePieceKey(p.getId()).orElseThrow();
        var sent=before.getSentAt(); var actor=before.getSentBy();
        call(patch("/api/pieces/"+p.getId()+"/scrap-dispatch"),admin,
            Map.of("defectType","OTHER","defectTypeCustom","  علت ساختگی  ","notes","  توضیح  ","version",before.getVersion()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.defectLabel").value("علت ساختگی"))
            .andExpect(jsonPath("$.notes").value("توضیح"));
        var after=scraps.findByActivePieceKey(p.getId()).orElseThrow();
        assertEquals(sent,after.getSentAt()); assertEquals(actor,after.getSentBy());
        assertEquals(PieceStage.NOT_STARTED,after.getOriginStage());
        assertEquals(1,scraps.findByPieceIdOrderBySentAtDesc(p.getId()).size());
        assertEquals(1,histories.findByPieceIdOrderByChangedAtDesc(p.getId()).size());
    }
    @Test void staleEditsAndUsersWithoutMovementPermissionAreRejected() throws Exception {
        Piece p=piece(); scrap(p);
        call(patch("/api/pieces/"+p.getId()+"/scrap-dispatch"),worker,reason()).andExpect(status().isForbidden());
        call(patch("/api/pieces/"+p.getId()+"/scrap-dispatch"),admin,Map.of("defectType","CR","version",999))
            .andExpect(status().isConflict());
        call(get("/api/dashboard/scrap-report"),worker).andExpect(status().isForbidden());
    }
    @Test void archivedAndLegacyScrapsAppearInProjectReportAndArchivedEditingIsBlocked() throws Exception {
        Piece p=piece(); scrap(p);
        p=pieceRepository.findById(p.getId()).orElseThrow(); p.setArchivedAt(LocalDateTime.now()); pieceRepository.saveAndFlush(p);
        Piece old=piece(); old.setCurrentStage(PieceStage.SCRAPPED); old.setArchivedAt(LocalDateTime.now()); pieceRepository.saveAndFlush(old);
        var legacyEntry=new PieceStageHistory(); legacyEntry.setPiece(old); legacyEntry.setStage(PieceStage.SCRAPPED);
        legacyEntry.setChangedBy("Synthetic historical actor"); histories.saveAndFlush(legacyEntry);
        call(get("/api/dashboard/scrap-report").param("projectId",p.getPieceDefinition().getProject().getId().toString()),admin)
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].archived").value(true)).andExpect(jsonPath("$[0].defectLabel").value("ابعادی"));
        call(get("/api/dashboard/scrap-report").param("projectId",old.getPieceDefinition().getProject().getId().toString()),admin)
            .andExpect(jsonPath("$[0].id").isEmpty()).andExpect(jsonPath("$[0].defectLabel").value("ثبت نشده"))
            .andExpect(jsonPath("$[0].sentAt").isNotEmpty()).andExpect(jsonPath("$[0].sentBy").value("Synthetic historical actor"));
        call(patch("/api/pieces/"+p.getId()+"/scrap-dispatch"),admin,reason()).andExpect(status().isBadRequest());
    }
    @Test void returningAndScrappingAgainPreservesBothCycles() throws Exception {
        Piece p=piece(); scrap(p);
        var first=scraps.findByActivePieceKey(p.getId()).orElseThrow().getId();
        call(patch("/api/pieces/"+p.getId()+"/stage"),admin,Map.of("newStage","NOT_STARTED")).andExpect(status().isOk());
        assertTrue(scraps.findByActivePieceKey(p.getId()).isEmpty());
        call(get("/api/dashboard/scrap-report").param("projectId",p.getPieceDefinition().getProject().getId().toString()),admin)
            .andExpect(jsonPath("$.length()").value(0));
        scrap(p);
        assertEquals(2,scraps.findByPieceIdOrderBySentAtDesc(p.getId()).size());
        assertNotEquals(first,scraps.findByActivePieceKey(p.getId()).orElseThrow().getId());
        assertNotNull(scraps.findById(first).orElseThrow().getReversedAt());
    }
    @Test void legacyReasonCanBeFilledWithoutInventingAnOriginalDate() throws Exception {
        Piece p=piece();p.setCurrentStage(PieceStage.SCRAPPED);pieceRepository.saveAndFlush(p);
        call(patch("/api/pieces/"+p.getId()+"/scrap-dispatch"),admin,reason()).andExpect(status().isOk())
            .andExpect(jsonPath("$.sentAt").isEmpty()).andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }
    @Test void weldingExitClosesItsDispatchAndRetainsScrapOrigin() throws Exception {
        Piece p=piece();
        call(post("/api/pieces/"+p.getId()+"/welding-station"),admin,Map.of("defectType","CR")).andExpect(status().isCreated());
        scrap(p);
        assertEquals(PieceStage.WELDING,scraps.findByActivePieceKey(p.getId()).orElseThrow().getOriginStage());
        call(get("/api/pieces/"+p.getId()+"/welding-dispatches"),admin).andExpect(jsonPath("$[0].open").value(false));
    }
    // Requests must use separate transactions: the rejected first request must roll back its decision.
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void tensileDispositionRequiresReasonAndRepeatedEditsDoNotDuplicateScrap() throws Exception {
        Piece p=piece(); p.setCurrentStage(PieceStage.TENSILE); pieceRepository.saveAndFlush(p);
        String test=call(post("/api/pieces/"+p.getId()+"/tensile-tests"),admin,
            Map.of("samples",List.of(Map.of("uts",100)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id=JsonPath.read(test,"$.id");
        var body=new HashMap<String,Object>(); body.put("dispositionDate","2026-10-31");
        body.put("nextStage","SCRAP");body.put("inspectionResult","REJECTED");
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,body).andExpect(status().isBadRequest());
        body.put("scrap",reason());
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,body).andExpect(status().isOk());
        var d=scraps.findByActivePieceKey(p.getId()).orElseThrow();
        var time=d.getSentAt();
        body.put("scrap",Map.of("defectType","OTHER","defectTypeCustom","کشش ساختگی","version",d.getVersion()));
        call(put("/api/tensile-tests/"+id+"/disposition"),admin,body).andExpect(status().isOk());
        assertEquals(1,scraps.findByPieceIdOrderBySentAtDesc(p.getId()).size());
        var after=scraps.findByActivePieceKey(p.getId()).orElseThrow();
        assertEquals(time,after.getSentAt());
        assertEquals(d.getDefectType(),after.getDefectType()); // Record-only corrections do not edit operational scrap data.
        assertEquals(PieceStage.SCRAPPED,pieceRepository.findById(p.getId()).orElseThrow().getCurrentStage());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void bulkFailureRollsBackTheFirstPiecesReasonStageAndHistory() throws Exception {
        Piece a=piece(),b=piece();
        Piece first=a.getId().compareTo(b.getId())<0?a:b;
        String defect=call(post("/api/pieces/"+first.getId()+"/radiography-defects"),admin,Map.of("defectType","CR","course","C1"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id=JsonPath.read(defect,"$.id");
        call(patch("/api/pieces/bulk/stage"),admin,Map.of("pieceIds",List.of(a.getId(),b.getId()),"newStage","SCRAPPED",
            "scrap",Map.of("defectType","CR","defectIds",List.of(id)))).andExpect(status().isBadRequest());
        for (Piece p:List.of(a,b)) {
            assertEquals(PieceStage.NOT_STARTED,pieceRepository.findById(p.getId()).orElseThrow().getCurrentStage());
            assertTrue(scraps.findByPieceIdOrderBySentAtDesc(p.getId()).isEmpty());
            assertTrue(histories.findByPieceIdOrderByChangedAtDesc(p.getId()).isEmpty());
        }
        call(patch("/api/pieces/bulk/stage"),admin,Map.of("pieceIds",List.of(a.getId(),b.getId()),"newStage","SCRAPPED","scrap",reason()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
}

package com.factory.productmanager.task;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MeltPassportIntegrationTest extends TaskTestBase {
    @Autowired MeltPassportService service;
    @Autowired PieceService pieceService;
    @Autowired StageTransitionPolicy policy;
    @Autowired MeltMaterialRepository materials;
    @Autowired PieceStageHistoryRepository histories;
    @Autowired PieceStageProcessDateRepository dates;
    @Autowired EntityManager em;
    Piece first;
    Piece second;

    @BeforeEach void seedMelt() {
        first = newPiece("MELT-"+UUID.randomUUID(), "بدنه ذوب", 1);
        first.setCurrentStage(PieceStage.CASTING);
        second = new Piece(); second.setPieceDefinition(first.getPieceDefinition()); second.setPieceNumber(2); second.setCurrentStage(PieceStage.CASTING);
        pieceRepository.saveAndFlush(first); pieceRepository.saveAndFlush(second);
        manager.getPermissions().addAll(Set.of(Permission.APPROVE_MELT_PASSPORT, Permission.VIEW_DASHBOARD)); userRepository.saveAndFlush(manager);
        worker.getPermissions().addAll(Set.of(Permission.MOVE_PIECE_STAGE,Permission.ADVANCE_PIECE_STAGE,Permission.MANAGE_MELT_MATERIALS)); userRepository.saveAndFlush(worker);
    }

    MeltPassportRequest body(Piece... selected) {
        var r = new MeltPassportRequest(); r.setVersion(0L); r.setPieceIds(Arrays.stream(selected).map(Piece::getId).toList()); return r;
    }
    MeltPassportRequest complete(Piece... selected) {
        var r = body(selected); r.setMeltDate(LocalDate.of(2026,10,18)); r.setFurnaceNumber(new BigDecimal("3")); r.setIngotType("آلیاژ سفارشی");
        r.setPrimaryWeight(new BigDecimal("70")); r.setRecycledWeight(new BigDecimal("30")); r.setInjectionOperator("اپراتور تزریق");
        r.setChecks(Arrays.stream(MeltCheckKind.values()).map(k -> new MeltPassportRequest.CheckLine(k,MeltCheckStatus.ACCEPTED,null)).toList()); return r;
    }
    MeltPassportResponse createComplete(Piece... selected) { return service.create(complete(selected),worker.getUsername()); }
    MeltDecisionRequest decision(MeltPassportResponse m) { return new MeltDecisionRequest(m.version(),null); }
    MeltPassportResponse pending(Piece... selected) { var m = createComplete(selected); return service.submit(m.id(),decision(m),worker.getUsername()); }

    @Test void clockAt610RoundTripsMinutePrecisionAndCanBeCleared() throws Exception {
        var m=createComplete(first);
        for (String clock:List.of("14:50","00:00","23:59")) {
            var request=json.valueToTree(complete(first));
            ((tools.jackson.databind.node.ObjectNode)request).put("version",m.version());
            ((tools.jackson.databind.node.ObjectNode)request.get("process")).put("timeAt610Clock",clock);
            call(put("/api/melt-passports/"+m.id()),worker,request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.process.timeAt610Clock").value(clock));
            em.flush();em.clear();m=service.get(m.id(),worker.getUsername());
            assertEquals(LocalTime.parse(clock),m.process().getTimeAt610Clock());
            call(get("/api/melt-passports/"+m.id()),worker).andExpect(status().isOk())
                    .andExpect(jsonPath("$.process.timeAt610Clock").value(clock));
        }
        var request=complete(first);request.setVersion(m.version());
        m=service.update(m.id(),request,worker.getUsername());
        assertNull(m.process().getTimeAt610Clock());
    }

    @Test void malformedClockAt610IsRejectedWithoutChangingSavedData() throws Exception {
        var request=complete(first);request.getProcess().setTimeAt610Clock(LocalTime.of(14,50));
        var m=service.create(request,worker.getUsername());
        for (Object invalid:List.of("24:00","12:60","14:50:00","14.50","12:5",1450)) {
            var body=json.valueToTree(request);
            ((tools.jackson.databind.node.ObjectNode)body).put("version",m.version());
            ((tools.jackson.databind.node.ObjectNode)body.get("process")).set("timeAt610Clock",json.valueToTree(invalid));
            call(put("/api/melt-passports/"+m.id()),worker,body).andExpect(status().isBadRequest());
            assertEquals(LocalTime.of(14,50),service.get(m.id(),worker.getUsername()).process().getTimeAt610Clock());
        }
    }

    @Test void legacy610NumberIsReadOnlyAndSurvivesOmittedProcessCorrectionAndApproval() throws Exception {
        var m=createComplete(first);
        em.find(MeltPassport.class,m.id()).getProcess().setTimeAt610(new BigDecimal("15.75"));
        em.flush();em.clear();m=service.get(m.id(),worker.getUsername());
        var request=json.valueToTree(complete(first));
        ((tools.jackson.databind.node.ObjectNode)request).put("version",m.version());
        var process=(tools.jackson.databind.node.ObjectNode)request.get("process");
        process.put("timeAt610",999);process.put("timeAt610Clock","14:50");
        call(put("/api/melt-passports/"+m.id()),worker,request).andExpect(status().isOk())
                .andExpect(jsonPath("$.process.timeAt610").value(15.75));
        m=service.get(m.id(),worker.getUsername());
        var omitted=complete(first);omitted.setVersion(m.version());omitted.setProcess(null);
        m=service.update(m.id(),omitted,worker.getUsername());
        assertEquals(0,new BigDecimal("15.75").compareTo(m.process().getTimeAt610()));
        m=service.submit(m.id(),decision(m),worker.getUsername());
        m=service.returnForCorrection(m.id(),new MeltDecisionRequest(m.version(),"تکمیل ساعت"),manager.getUsername());
        var corrected=complete(first);corrected.setVersion(m.version());
        corrected.getProcess().setTimeAt610(new BigDecimal("999"));
        corrected.getProcess().setTimeAt610Clock(LocalTime.of(14,50));
        m=service.update(m.id(),corrected,worker.getUsername());
        m=service.submit(m.id(),decision(m),worker.getUsername());
        m=service.approve(m.id(),decision(m),manager.getUsername());
        em.flush();em.clear();
        call(get("/api/melt-passports/"+m.id()),worker).andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.process.timeAt610").value(15.75))
                .andExpect(jsonPath("$.process.timeAt610Clock").value("14:50"));
    }

    @Test void newPassportCannotWriteLegacy610Number() throws Exception {
        var request=json.valueToTree(complete(first));
        ((tools.jackson.databind.node.ObjectNode)request.get("process")).put("timeAt610",999);
        call(post("/api/melt-passports"),worker,request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.process.timeAt610").isEmpty());
    }

    @Test void draftDefaultsAndSharedAccessPreserveEmptyCalculatorAndUncheckedControls() throws Exception {
        var m = service.create(body(first,second),worker.getUsername());
        assertEquals(LocalDate.now(),m.meltDate()); assertNull(m.magnesium().getW()); assertNull(m.magnesiumResult());
        assertEquals(6,m.checks().size()); assertTrue(m.checks().stream().allMatch(c -> c.status()==MeltCheckStatus.UNCHECKED));
        assertEquals(m.id(),service.forPiece(first.getId(),outsider.getUsername()).get(0).id());
        assertEquals(m.id(),service.forPiece(second.getId(),outsider.getUsername()).get(0).id());
        assertTrue(m.pieces().get(0).pieceRef().contains("-1-"));
        call(get("/api/melt-passports/piece/"+first.getId()),worker).andExpect(status().isOk()).andExpect(jsonPath("$[0].pieces.length()").value(2));
    }

    @Test void invalidWeightsAndIncompleteSubmissionAreRejected() throws Exception {
        var r=body(first); r.setPrimaryWeight(new BigDecimal("-1"));
        call(post("/api/melt-passports"),worker,r).andExpect(status().isBadRequest());
        call(post("/api/melt-passports"),worker,Map.of("version",0,"pieceIds",List.of(first.getId()),"furnaceNumber",3.5))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.furnaceNumber").exists());
        call(post("/api/melt-passports"),worker,Map.of("version",0,"pieceIds",List.of(first.getId()),"furnaceNumber",2147483648L))
                .andExpect(status().isBadRequest());
        var m=service.create(body(first),worker.getUsername());
        call(post("/api/melt-passports/"+m.id()+"/submit"),worker,decision(m)).andExpect(status().isBadRequest());
    }

    @Test void percentagesAndIndependentManualCalculatorPersistAndInvalidate() throws Exception {
        var r=complete(first);
        r.getProcess().setRotaryDuration(new BigDecimal("999")); r.getProcess().setArgonInjectionDuration(new BigDecimal("777"));
        var m=service.create(r,worker.getUsername());
        assertEquals(0,new BigDecimal("70").compareTo(m.primaryPercent())); assertEquals(0,new BigDecimal("30").compareTo(m.recycledPercent()));
        assertNull(m.magnesium().getRotary()); assertNull(m.magnesium().getAr()); assertNull(m.magnesium().getW());
        call(post("/api/melt-passports/"+m.id()+"/calculate-magnesium"),worker,decision(m)).andExpect(status().isBadRequest());
        r.setVersion(m.version());
        var v=r.getMagnesium(); v.setMg(new BigDecimal("0.1")); v.setRotary(new BigDecimal("2")); v.setAt610(new BigDecimal("3")); v.setAr(new BigDecimal("4")); v.setB(new BigDecimal("0.5")); v.setW(new BigDecimal("10"));
        m=service.update(m.id(),r,worker.getUsername()); assertNull(m.magnesiumResult());
        m=service.calculate(m.id(),decision(m),worker.getUsername());
        assertEquals(0,new BigDecimal("453.2").compareTo(m.magnesiumResult())); assertNotNull(m.magnesiumCalculatedAt());
        assertEquals(0,new BigDecimal("70").compareTo(m.primaryWeight())); assertEquals(0,new BigDecimal("999").compareTo(m.process().getRotaryDuration()));
        r.setVersion(m.version());r.getProcess().setTimeAt610Clock(LocalTime.of(14,50));
        m=service.update(m.id(),r,worker.getUsername());
        assertEquals(0,new BigDecimal("453.2").compareTo(m.magnesiumResult()));assertNotNull(m.magnesiumCalculatedAt());
        assertEquals(new BigDecimal("3"),m.magnesium().getAt610());
        r.setVersion(m.version()); r.getMagnesium().setMg(new BigDecimal("10"));
        m=service.update(m.id(),r,worker.getUsername()); assertNull(m.magnesiumResult()); assertNull(m.magnesiumCalculatedAt());
        m=service.calculate(m.id(),decision(m),worker.getUsername()); assertTrue(m.magnesiumResult().signum()<0);
    }

    @Test void normalUsersCannotReviewAndPendingOrApprovedCannotBeEdited() throws Exception {
        var m=pending(first);
        call(post("/api/melt-passports/"+m.id()+"/approve"),worker,decision(m)).andExpect(status().isForbidden());
        call(post("/api/melt-passports/"+m.id()+"/return"),worker,new MeltDecisionRequest(m.version(),"اصلاح شود")).andExpect(status().isForbidden());
        var r=complete(first); r.setVersion(m.version());
        call(put("/api/melt-passports/"+m.id()),worker,r).andExpect(status().isBadRequest());
        call(get("/api/melt-passports").param("status","PENDING_APPROVAL"),worker).andExpect(status().isForbidden());
        call(get("/api/melt-passports").param("status","PENDING_APPROVAL"),manager).andExpect(status().isOk());
    }

    @Test void returnRequiresReasonThenAllowsCorrectionAndResubmission() throws Exception {
        var m=pending(first);
        call(post("/api/melt-passports/"+m.id()+"/return"),manager,new MeltDecisionRequest(m.version(),"  ")).andExpect(status().isBadRequest());
        m=service.returnForCorrection(m.id(),new MeltDecisionRequest(m.version(),"شماره بچ اصلاح شود"),manager.getUsername());
        assertTrue(m.editable()); // A returned passport can also be edited by the reviewer.
        assertTrue(service.get(m.id(),worker.getUsername()).editable());
        assertEquals("شماره بچ اصلاح شود",m.correctionReason()); assertNotNull(m.returnedAt()); assertEquals(manager.getFullName(),m.returnedBy());
        var r=complete(first); r.setVersion(m.version()); r.setPrimaryBatch("NEW-BATCH");
        m=service.update(m.id(),r,worker.getUsername());
        m=service.submit(m.id(),decision(m),worker.getUsername());
        assertEquals(MeltPassportStatus.PENDING_APPROVAL,m.status()); assertEquals(2,m.events().stream().filter(e -> e.action().equals("SUBMITTED")).count());
    }

    @Test void onlyManagerCanManuallyDepartFromCastingEvenWithDelegatedPermissions() throws Exception {
        User delegate = newUser("melt-delegate-" + UUID.randomUUID(), Role.ADMIN, Set.of(
                Permission.MOVE_PIECE_STAGE, Permission.ADVANCE_PIECE_STAGE,
                Permission.CORRECT_PIECE_STAGE, Permission.APPROVE_MELT_PASSPORT));
        for(User as:List.of(worker,delegate)) {
            call(patch("/api/pieces/"+first.getId()+"/stage"),as,Map.of("newStage","ROUGH_MACHINING")).andExpect(status().isBadRequest());
            call(post("/api/pieces/"+first.getId()+"/advance"),as,Map.of("processDate","2026-10-18")).andExpect(status().isBadRequest());
            call(patch("/api/pieces/bulk/stage"),as,Map.of("pieceIds",List.of(first.getId(),second.getId()),"newStage","ROUGH_MACHINING")).andExpect(status().isBadRequest());
        }
        call(get("/api/pieces/"+first.getId()+"/stage-correction/targets"),delegate)
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        call(patch("/api/pieces/"+first.getId()+"/stage-correction"),delegate,Map.of("stage","READY"))
                .andExpect(status().isBadRequest());
        call(patch("/api/pieces/bulk/stage-correction"),delegate,
                Map.of("pieceIds", List.of(first.getId(), second.getId()), "target", Map.of("stage", "READY")))
                .andExpect(status().isBadRequest());
        assertThrows(IllegalStateException.class,()->pieceService.applyTrustedStageMove(first.getId(),PieceStage.HEAT_TREATMENT,null,null,null,delegate.getUsername()));
        call(post("/api/pieces/"+first.getId()+"/welding-station"),worker,Map.of("defectType","OTHER","defectTypeCustom","آزمایشی")).andExpect(status().isBadRequest());
        assertEquals(PieceStage.CASTING,first.getCurrentStage()); assertEquals(PieceStage.CASTING,second.getCurrentStage());
    }

    @Test void managerCanAdvanceAndCorrectWithoutPassportButOpenPassportStillProtectsItsMembers() throws Exception {
        var m = pending(first);
        call(post("/api/pieces/"+first.getId()+"/advance"),admin,Map.of("processDate","2026-10-18"))
                .andExpect(status().isBadRequest());
        call(patch("/api/pieces/"+first.getId()+"/stage-correction"),admin,Map.of("stage","READY"))
                .andExpect(status().isBadRequest());
        assertEquals(m.version(), service.get(m.id(),admin.getUsername()).version());
        call(post("/api/pieces/"+second.getId()+"/advance"),admin,Map.of("processDate","2026-10-18"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("ROUGH_MACHINING"));
        assertTrue(service.forPiece(second.getId(),admin.getUsername()).isEmpty());
        pieceService.correctStage(second.getId(), new StageCorrectionTarget(PieceStage.CASTING, null, null, null), admin.getUsername());
        call(patch("/api/pieces/"+second.getId()+"/stage-correction"),admin,Map.of("stage","READY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("READY"));
    }

    @Test void authorizedApprovalMovesAllPiecesByPolicyAndPreservesDatesAndHistory() throws Exception {
        first.setProcessDate(LocalDate.of(2026,10,17)); pieceRepository.saveAndFlush(first);
        PieceStage next=policy.forwardMove(first).getStage();
        var m=pending(first,second); m=service.approve(m.id(),decision(m),manager.getUsername());
        assertEquals(MeltPassportStatus.APPROVED,m.status()); assertEquals(manager.getFullName(),m.approvedBy()); assertFalse(m.editable());
        for(Piece p:List.of(first,second)) {
            assertEquals(next,p.getCurrentStage()); assertNull(p.getProcessDate());
            assertEquals(1,histories.findByPieceIdOrderByChangedAtDesc(p.getId()).size());
        }
        assertEquals(LocalDate.of(2026,10,17),dates.findByPieceIdAndStage(first.getId(),PieceStage.CASTING).orElseThrow().getProcessDate());
        assertEquals(LocalDate.of(2026,10,18),dates.findByPieceIdAndStage(second.getId(),PieceStage.CASTING).orElseThrow().getProcessDate());
        call(post("/api/melt-passports/"+m.id()+"/approve"),manager,decision(m)).andExpect(status().isBadRequest());
        var r=complete(first,second);r.setVersion(m.version());
        call(put("/api/melt-passports/"+m.id()),admin,r).andExpect(status().isBadRequest());
    }

    @Test void adminAlsoCanApprove() {
        var m=pending(first); assertEquals(MeltPassportStatus.APPROVED,service.approve(m.id(),decision(m),admin.getUsername()).status());
    }

    @Test void materialIdentitySnapshotsNormalizationUnitsAndReportDoNotMultiplyByPieces() throws Exception {
        var material=service.createMaterial(new MeltMaterialRequest("  پودر   منگنز ","افزودنی"),worker.getUsername());
        var duplicate=service.createMaterial(new MeltMaterialRequest("پودر منگنز","دیگر"),worker.getUsername());
        assertEquals(material.id(),duplicate.id());
        var r=complete(first,second);
        r.setMaterials(List.of(new MeltPassportRequest.MaterialLine(material.id(),new BigDecimal("750"),MeltMaterialUnit.G,LocalTime.of(9,30),"B1","توضیح"),
                new MeltPassportRequest.MaterialLine(material.id(),new BigDecimal("1.25"),MeltMaterialUnit.KG,null,null,null)));
        var m=service.create(r,worker.getUsername());
        r.setVersion(m.version()); m=service.update(m.id(),r,worker.getUsername());
        m=service.submit(m.id(),decision(m),worker.getUsername()); m=service.approve(m.id(),decision(m),admin.getUsername());
        service.setMaterialActive(material.id(),false,admin.getUsername());
        call(patch("/api/melt-passports/materials/"+material.id()),admin,Map.of("name","نام جدید","category","دسته جدید")).andExpect(status().isNoContent());
        assertEquals("پودر   منگنز",service.get(m.id(),worker.getUsername()).materials().get(0).materialName());
        var f=new MeltPassportFilter();f.setPieceId(first.getId());f.setMaterialId(material.id());
        var totals=service.consumption(f,manager.getUsername());
        assertEquals(1,totals.size());assertEquals(0,new BigDecimal("2").compareTo(totals.get(0).quantity()));assertEquals(1,totals.get(0).meltCount());
    }

    @Test void staleVersionDuplicateMembershipArchivedAndWrongStageAreRejected() throws Exception {
        var m=createComplete(first);
        call(post("/api/melt-passports"),worker,complete(first)).andExpect(status().isConflict());
        var r=complete(first);r.setVersion(m.version());var updated=service.update(m.id(),r,worker.getUsername());
        call(put("/api/melt-passports/"+m.id()),worker,r).andExpect(status().isConflict());
        second.setArchivedAt(LocalDateTime.now());pieceRepository.saveAndFlush(second);
        call(post("/api/melt-passports"),worker,complete(second)).andExpect(status().isBadRequest());
        assertTrue(updated.version()>m.version());
    }

    @Test void materialQuantityCanRemainMissingInDraftButNotAtSubmission() throws Exception {
        var material=service.createMaterial(new MeltMaterialRequest("ماده پیش‌نویس","افزودنی"),worker.getUsername());
        var r=complete(first);
        r.setMaterials(List.of(new MeltPassportRequest.MaterialLine(material.id(),null,MeltMaterialUnit.KG,null,null,null)));
        var m=service.create(r,worker.getUsername());
        assertNull(m.materials().get(0).quantity());assertNull(m.materials().get(0).baseQuantity());
        call(post("/api/melt-passports/"+m.id()+"/submit"),worker,decision(m)).andExpect(status().isBadRequest());
        var f=new MeltPassportFilter();f.setStatus(MeltPassportStatus.DRAFT);f.setMaterialId(material.id());
        assertTrue(service.consumption(f,manager.getUsername()).isEmpty());
    }

    @Test void candidateLookupExcludesLinkedArchivedAndWrongStagePiecesAndFiltersUseExists() {
        var m=createComplete(first);
        String code=first.getPieceDefinition().getProject().getCode();
        var candidates=service.candidates(code,worker.getUsername());
        assertEquals(List.of(second.getId()),candidates.stream().map(MeltPassportResponse.PieceLink::id).toList());
        assertEquals(second.getId(),service.candidates(code+"-1-2",worker.getUsername()).get(0).id());
        var f=new MeltPassportFilter();f.setPieceId(first.getId());f.setFurnaceNumber(3);f.setIngotType("آلیاژ سفارشی");
        f.setOperator("اپراتور تزریق");f.setDateFrom(LocalDate.of(2026,10,18));f.setDateTo(LocalDate.of(2026,10,18));
        assertEquals(1,service.search(f,0,20,manager.getUsername()).totalElements());
        f.setPieceId(second.getId());assertEquals(0,service.search(f,0,20,manager.getUsername()).totalElements());
        second.setArchivedAt(LocalDateTime.now());pieceRepository.saveAndFlush(second);
        assertTrue(service.candidates(code,worker.getUsername()).isEmpty());
        second.setArchivedAt(null);second.setCurrentStage(PieceStage.TENSILE);pieceRepository.saveAndFlush(second);
        assertTrue(service.candidates(code,worker.getUsername()).isEmpty());
    }

    @Test void reenteringCastingAllowsANewPassportAndRetainsPriorMembership() {
        var m=pending(first);m=service.approve(m.id(),decision(m),admin.getUsername());
        pieceService.correctStage(first.getId(), new StageCorrectionTarget(PieceStage.CASTING, null, null, null), admin.getUsername());
        assertThrows(IllegalStateException.class,()->pieceService.updateStage(first.getId(),newStage(PieceStage.ROUGH_MACHINING),worker.getUsername()));
        var next=createComplete(first);assertNotEquals(m.id(),next.id());assertEquals(2,service.forPiece(first.getId(),worker.getUsername()).size());
    }

    @Test void reportPermissionIsIndependentFromPieceAccessAndApproval() throws Exception {
        var m=createComplete(first);
        for (String path:List.of("/api/melt-passports","/api/melt-passports/consumption")) {
            call(get(path),worker).andExpect(status().isForbidden());
            call(get(path),manager).andExpect(status().isOk());
            call(get(path),admin).andExpect(status().isOk());
        }
        call(get("/api/melt-passports/"+m.id()),worker).andExpect(status().isOk());
        call(get("/api/melt-passports/piece/"+first.getId()),worker).andExpect(status().isOk());
        otherManager.getPermissions().add(Permission.VIEW_DASHBOARD);userRepository.saveAndFlush(otherManager);
        call(get("/api/melt-passports"),otherManager).andExpect(status().isOk());
        call(get("/api/melt-passports").param("status","PENDING_APPROVAL"),otherManager).andExpect(status().isForbidden());
        call(get("/api/melt-passports/consumption").param("status","PENDING_APPROVAL"),otherManager).andExpect(status().isForbidden());
        call(post("/api/melt-passports/"+m.id()+"/approve"),otherManager,decision(m)).andExpect(status().isForbidden());
        worker.getPermissions().add(Permission.APPROVE_MELT_PASSPORT);userRepository.saveAndFlush(worker);
        call(get("/api/melt-passports"),worker).andExpect(status().isForbidden());
        m=service.submit(m.id(),decision(m),worker.getUsername());
        assertEquals(MeltPassportStatus.APPROVED,service.approve(m.id(),decision(m),worker.getUsername()).status());
    }

    @Test void candidateSearchParsesReferencesAndLocalizedNumbersWithoutCastingToText() {
        String code=first.getPieceDefinition().getProject().getCode();
        for (String keyword:List.of("2","۲","٢"," ۰۰۲ ", code+"-1-2", code+"-۱-۲")) {
            assertEquals(List.of(second.getId()),service.candidates(keyword,worker.getUsername()).stream().map(MeltPassportResponse.PieceLink::id).toList());
        }
        assertEquals(2,service.candidates("بدنه",worker.getUsername()).size());
        assertEquals(2,service.candidates(code.toLowerCase(),worker.getUsername()).size());
        assertEquals(2,service.candidates("",worker.getUsername()).size());
        assertTrue(service.candidates("9999999999999999999999",worker.getUsername()).isEmpty());
        assertTrue(service.candidates("%",worker.getUsername()).isEmpty());
        assertTrue(service.candidates("_",worker.getUsername()).isEmpty());
        assertTrue(service.candidates(code+"-",worker.getUsername()).isEmpty());
    }

    @Test void candidateSearchKeepsStableLimitAndDoesNotHideEligiblePiecesAfterFiltering() {
        for(int number=3;number<=115;number++) {
            var p=new Piece();p.setPieceDefinition(first.getPieceDefinition());p.setPieceNumber(number);p.setCurrentStage(PieceStage.CASTING);pieceRepository.save(p);
        }
        pieceRepository.flush();createComplete(first);
        var result=service.candidates(first.getPieceDefinition().getProject().getCode(),worker.getUsername());
        assertEquals(100,result.size());assertEquals(second.getId(),result.get(0).id());assertTrue(result.get(99).pieceRef().endsWith("-101"));
    }

    @Test void pieceHistoryRemainsReadableWithoutDashboardAfterAdvancingAndAcrossCycles() throws Exception {
        var original=pending(first);original=service.approve(original.id(),decision(original),manager.getUsername());
        call(get("/api/melt-passports/piece/"+first.getId()),worker).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(original.id().toString())).andExpect(jsonPath("$[0].editable").value(false));
        pieceService.correctStage(first.getId(), new StageCorrectionTarget(PieceStage.CASTING, null, null, null), admin.getUsername());
        var newer=createComplete(first);
        call(get("/api/melt-passports/piece/"+first.getId()),worker).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(newer.id().toString()));
        call(get("/api/melt-passports/"+original.id()),worker).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }
    private UpdateStageRequest newStage(PieceStage s) { var r=new UpdateStageRequest();r.setNewStage(s);return r; }
}

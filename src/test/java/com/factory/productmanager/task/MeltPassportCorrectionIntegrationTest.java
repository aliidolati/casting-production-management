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

class MeltPassportCorrectionIntegrationTest extends TaskTestBase {
    @Autowired MeltPassportService service;
    @Autowired MeltPassportRepository passports;
    @Autowired PieceService pieceService;
    @Autowired PieceStageHistoryRepository histories;
    @Autowired PieceStageProcessDateRepository dates;
    @Autowired EntityManager em;
    Piece first, second;

    @BeforeEach void casting() {
        manager.getPermissions().add(Permission.APPROVE_MELT_PASSPORT); userRepository.saveAndFlush(manager);
        var project=new Project(); project.setCode("CORRECTION-"+UUID.randomUUID());project.setName("Synthetic correction");projectRepository.save(project);
        var def=new PieceDefinition();def.setProject(project);def.setRowNum(1);def.setPieceName("قطعه ساختگی");definitionRepository.save(def);
        first=piece(def,1);second=piece(def,2);
    }
    Piece piece(PieceDefinition def,int number) {
        var p=new Piece();p.setPieceDefinition(def);p.setPieceNumber(number);p.setCurrentStage(PieceStage.CASTING);return pieceRepository.saveAndFlush(p);
    }
    MeltPassportRequest complete() {
        var r=new MeltPassportRequest();r.setVersion(0L);r.setPieceIds(List.of(first.getId()));r.setMeltDate(LocalDate.of(2026,10,18));
        r.setFurnaceNumber(BigDecimal.ONE);r.setIngotType("آلیاژ ساختگی");r.setPrimaryWeight(new BigDecimal("70"));r.setRecycledWeight(new BigDecimal("30"));r.setInjectionOperator("اپراتور آزمایشی");
        r.setChecks(Arrays.stream(MeltCheckKind.values()).map(k->new MeltPassportRequest.CheckLine(k,MeltCheckStatus.ACCEPTED,null)).toList());return r;
    }
    MeltPassportResponse approved(boolean withMg) {
        var r=complete();
        if(withMg) { r.getMagnesium().setMg(new BigDecimal("0.1"));r.getMagnesium().setRotary(BigDecimal.ONE);r.getMagnesium().setAt610(BigDecimal.ONE);
            r.getMagnesium().setAr(BigDecimal.ONE);r.getMagnesium().setB(BigDecimal.ONE);r.getMagnesium().setW(BigDecimal.TEN); }
        var m=service.create(r,worker.getUsername());
        if(withMg) m=service.calculate(m.id(),new MeltDecisionRequest(m.version(),null),worker.getUsername());
        m=service.submit(m.id(),new MeltDecisionRequest(m.version(),null),worker.getUsername());
        return service.approve(m.id(),new MeltDecisionRequest(m.version(),null),admin.getUsername());
    }
    MeltPassportRequest form(MeltPassportResponse m) {
        var r=new MeltPassportRequest();r.setVersion(m.version());r.setPieceIds(m.pieces().stream().map(MeltPassportResponse.PieceLink::id).toList());
        r.setMeltDate(m.meltDate());r.setFurnaceNumber(BigDecimal.valueOf(m.furnaceNumber()));r.setIngotType(m.ingotType());r.setPrimaryBatch(m.primaryBatch());r.setRecycledBatch(m.recycledBatch());
        r.setPrimaryWeight(m.primaryWeight());r.setRecycledWeight(m.recycledWeight());r.setInjectionOperator(m.injectionOperator());r.setNotes(m.notes());r.setAdditionalNotes(m.additionalNotes());
        r.setProcess(m.process());r.setMagnesium(m.magnesium());r.setChecks(m.checks());
        r.setMaterials(m.materials().stream().map(l->new MeltPassportRequest.MaterialLine(l.materialId(),l.quantity(),l.unit(),l.addedAt(),l.batchNumber(),l.notes())).toList());return r;
    }
    MeltPassportCorrectionRequest correction(MeltPassportResponse m,String note,String reason,boolean calculate) {
        var r=form(m);r.setNotes(note);return new MeltPassportCorrectionRequest(r,reason,calculate);
    }
    String route(MeltPassportResponse m) {return "/api/melt-passports/"+m.id()+"/corrections";}

    @Test void onlyAdminCanCorrectAndLegacyMutationRoutesStayLocked() throws Exception {
        var m=approved(false);
        call(get("/api/melt-passports/"+m.id()),admin).andExpect(jsonPath("$.correctable").value(true)).andExpect(jsonPath("$.editable").value(false));
        for(User actor:List.of(worker,manager)) {
            call(get("/api/melt-passports/"+m.id()),actor).andExpect(jsonPath("$.correctable").value(false));
            call(post(route(m)),actor,correction(m,"forbidden",null,false)).andExpect(status().isForbidden());
        }
        String adminToken=token(admin);admin.setDisabled(true);userRepository.saveAndFlush(admin);
        mvc.perform(post(route(m)).header("Authorization","Bearer "+adminToken).contentType("application/json")
                .content(json.writeValueAsString(correction(m,"forbidden",null,false)))).andExpect(status().isUnauthorized());
        admin.setDisabled(false);userRepository.saveAndFlush(admin);
        call(put("/api/melt-passports/"+m.id()),admin,form(m)).andExpect(status().isBadRequest());
        call(post("/api/melt-passports/"+m.id()+"/calculate-magnesium"),admin,new MeltDecisionRequest(m.version(),null)).andExpect(status().isBadRequest());
    }

    @Test void correctionAuditsBeforeAfterAndPreservesProductionAndOriginalApproval() throws Exception {
        var m=approved(false);em.flush();em.clear();m=service.get(m.id(),admin.getUsername());
        long historyCount=histories.findByPieceIdOrderByChangedAtDesc(first.getId()).size();
        var oldDate=dates.findByPieceIdAndStage(first.getId(),PieceStage.CASTING).orElseThrow().getProcessDate();
        var oldStage=first.getCurrentStage();var r=form(m);r.setNotes("اصلاح <script>آزمایشی</script>");r.setMeltDate(m.meltDate().plusDays(2));
        r.getProcess().setPouringTemperature(new BigDecimal("710.25"));
        var material=service.createMaterial(new MeltMaterialRequest("ماده آزمون","کاورال"),admin.getUsername());
        r.setMaterials(List.of(new MeltPassportRequest.MaterialLine(material.id(),new BigDecimal("2.50"),MeltMaterialUnit.KG,null,"بچ جدید",null)));
        var out=call(post(route(m)),admin,new MeltPassportCorrectionRequest(r,"  اصلاح اطلاعات واقعی  ",false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.events[-1].action").value("ADMIN_CORRECTED"))
                .andExpect(jsonPath("$.events[-1].actorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.events[-1].reason").value("اصلاح اطلاعات واقعی"))
                .andExpect(jsonPath("$.events[-1].oldValues.notes").isEmpty())
                .andExpect(jsonPath("$.events[-1].newValues.notes").value(r.getNotes())).andReturn();
        var after=json.readValue(out.getResponse().getContentAsString(),MeltPassportResponse.class);
        assertEquals(m.approvedAt(),after.approvedAt());assertEquals(m.approvedBy(),after.approvedBy());assertEquals(m.pieces(),after.pieces());assertTrue(after.version()>m.version());
        assertEquals(oldStage,first.getCurrentStage());assertEquals(historyCount,histories.findByPieceIdOrderByChangedAtDesc(first.getId()).size());
        assertEquals(oldDate,dates.findByPieceIdAndStage(first.getId(),PieceStage.CASTING).orElseThrow().getProcessDate());
        em.flush();em.clear();
        var stored=passports.findById(m.id()).orElseThrow();
        assertEquals(m.approvedAt(),stored.getApprovedAt());
        assertTrue(stored.getEvents().stream().filter(e->e.getAction().equals("ADMIN_CORRECTED")).findFirst().orElseThrow().getNewValues().contains("710.25"));
    }

    @Test void unchangedValuesDoNotAddHistoryReplaceMaterialsOrIncrementVersion() {
        var m=approved(false);var material=service.createMaterial(new MeltMaterialRequest("ماده ثابت","کاورال"),admin.getUsername());
        var r=form(m);r.setMaterials(List.of(new MeltPassportRequest.MaterialLine(material.id(),BigDecimal.ONE,MeltMaterialUnit.KG,null,null,null)));
        m=service.correct(m.id(),new MeltPassportCorrectionRequest(r,null,false),admin.getUsername());
        em.flush();em.clear();m=service.get(m.id(),admin.getUsername());
        UUID materialRowId=passports.findById(m.id()).orElseThrow().getMaterials().get(0).getId();
        r=form(m);r.setPrimaryWeight(new BigDecimal("70.0000"));
        var after=service.correct(m.id(),new MeltPassportCorrectionRequest(r,"صرفاً ذخیره",false),admin.getUsername());
        em.flush();em.clear();
        assertEquals(m.version(),after.version());assertEquals(m.events().size(),after.events().size());
        assertEquals(materialRowId,passports.findById(m.id()).orElseThrow().getMaterials().get(0).getId());
    }

    @Test void staleVersionAndChangedOrDuplicatedMembershipAreRejected() throws Exception {
        var m=approved(false);var r=form(m);r.setPieceIds(List.of(second.getId()));
        call(post(route(m)),admin,new MeltPassportCorrectionRequest(r,null,false)).andExpect(status().isBadRequest());
        r.setPieceIds(List.of(first.getId(),first.getId()));
        call(post(route(m)),admin,new MeltPassportCorrectionRequest(r,null,false)).andExpect(status().isBadRequest());
        r=form(m);r.setVersion(m.version()-1);
        call(post(route(m)),admin,new MeltPassportCorrectionRequest(r,null,false)).andExpect(status().isConflict());
        assertEquals(m.pieces(),service.get(m.id(),admin.getUsername()).pieces());
    }

    @Test void managerAddsPiecesAtOneChosenStageWithoutMovingExistingMembers() throws Exception {
        var m = approved(false);
        var third = piece(first.getPieceDefinition(), 3);
        pieceService.updateStage(third.getId(), newStage(PieceStage.ROUGH_MACHINING), admin.getUsername());
        var originalMove = new SkipPieceStageRequest(PieceStage.READY, null, null, null, "وضعیت قبلی");
        pieceService.skipStage(first.getId(), originalMove, admin.getUsername());
        int originalHistory = histories.findByPieceIdOrderByChangedAtDesc(first.getId()).size();

        var body = form(m);
        body.setPieceIds(List.of(first.getId(), second.getId(), third.getId()));
        var request = new MeltPassportCorrectionRequest(body, "ثبت قطعات تکمیلی", false, PieceStage.RADIOGRAPHY, null);
        call(post(route(m)),manager,request).andExpect(status().isForbidden());
        call(post(route(m)),admin,new MeltPassportCorrectionRequest(body,null,false)).andExpect(status().isBadRequest());
        call(get("/api/melt-passports/candidates").param("approvedPassportId",m.id().toString()),manager)
                .andExpect(status().isForbidden());
        call(get("/api/melt-passports/candidates").param("approvedPassportId",m.id().toString()).param("q","3"),admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(third.getId().toString()));

        call(post(route(m)),admin,request).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.pieces.length()").value(3))
                .andExpect(jsonPath("$.events[-1].oldValues.pieceRefs.length()").value(1))
                .andExpect(jsonPath("$.events[-1].newValues.pieceRefs.length()").value(3));
        var saved = service.get(m.id(),admin.getUsername());
        assertEquals(m.approvedAt(),saved.approvedAt());
        assertEquals(m.approvedBy(),saved.approvedBy());
        assertEquals(PieceStage.READY,first.getCurrentStage());
        assertEquals(originalHistory,histories.findByPieceIdOrderByChangedAtDesc(first.getId()).size());
        for (Piece added : List.of(second,third)) {
            assertEquals(PieceStage.RADIOGRAPHY,added.getCurrentStage());
            assertEquals(RadiographySubStage.SHOT,added.getRadiographySubStage());
            assertEquals(m.id(),service.forPiece(added.getId(),worker.getUsername()).get(0).id());
        }
        assertEquals(m.meltDate(),dates.findByPieceIdAndStage(second.getId(),PieceStage.CASTING).orElseThrow().getProcessDate());
        call(post(route(m)),admin,request).andExpect(status().isConflict());
    }

    @Test void correctionRejectsPiecesInAnotherActivePassportAndKeepsCastingMembershipWhenSelected() throws Exception {
        var m = approved(false);
        var draftRequest = complete();
        draftRequest.setPieceIds(List.of(second.getId()));
        var draft = service.create(draftRequest,worker.getUsername());
        var body = form(m);
        body.setPieceIds(List.of(first.getId(),second.getId()));
        var request = new MeltPassportCorrectionRequest(body,null,false,PieceStage.CASTING,null);
        call(post(route(m)),admin,request).andExpect(status().isConflict());
        service.cancel(draft.id(),new MeltDecisionRequest(draft.version(),"لغو آزمایشی"),admin.getUsername());
        call(post(route(m)),admin,request).andExpect(status().isOk());
        assertEquals(PieceStage.CASTING,second.getCurrentStage());
        assertTrue(histories.findByPieceIdOrderByChangedAtDesc(second.getId()).isEmpty());
        assertTrue(service.get(m.id(),admin.getUsername()).pieces().stream().anyMatch(link -> link.id().equals(second.getId()) && link.active()));
    }

    private UpdateStageRequest newStage(PieceStage stage) {
        var request = new UpdateStageRequest();
        request.setNewStage(stage);
        return request;
    }

    @Test void nonApprovedRecordsAndInvalidApprovedDataAreRejected() throws Exception {
        var draftRequest=complete();draftRequest.setPieceIds(List.of(second.getId()));
        var draft=service.create(draftRequest,worker.getUsername());
        call(post(route(draft)),admin,correction(draft,"invalid",null,false)).andExpect(status().isBadRequest());
        var m=approved(false);var r=form(m);r.setIngotType(" ");
        call(post(route(m)),admin,new MeltPassportCorrectionRequest(r,null,false)).andExpect(status().isBadRequest());
        assertEquals(m.ingotType(),service.get(m.id(),admin.getUsername()).ingotType());
    }

    @Test void magnesiumCorrectionRequiresExplicitAtomicCalculationAndSupportsClearing() throws Exception {
        var m=approved(true);var r=form(m);r.getMagnesium().setMg(new BigDecimal("0.2"));
        call(post(route(m)),admin,new MeltPassportCorrectionRequest(r,null,false)).andExpect(status().isBadRequest());
        assertEquals(0,new BigDecimal("0.1").compareTo(service.get(m.id(),admin.getUsername()).magnesium().getMg()));
        m=service.correct(m.id(),new MeltPassportCorrectionRequest(r,null,true),admin.getUsername());
        assertEquals(0,MeltCalculations.magnesium(m.magnesium()).compareTo(m.magnesiumResult()));
        assertNull(m.events().get(m.events().size()-1).reason());
        r=form(m);r.setMagnesium(new MeltPassportRequest.MagnesiumInput());
        m=service.correct(m.id(),new MeltPassportCorrectionRequest(r,"پاک کردن ورودی اختیاری",false),admin.getUsername());
        assertNull(m.magnesiumResult());assertNull(m.magnesiumCalculatedAt());assertEquals(MeltPassportStatus.APPROVED,m.status());
    }

    @Test void partialMagnesiumFailsWithoutSavingAnyOtherCorrection() throws Exception {
        var m=approved(false);var r=form(m);r.setNotes("نباید ذخیره شود");r.getMagnesium().setMg(BigDecimal.ZERO);
        call(post(route(m)),admin,new MeltPassportCorrectionRequest(r,null,true)).andExpect(status().isBadRequest());
        var after=service.get(m.id(),admin.getUsername());assertNull(after.notes());assertEquals(m.version(),after.version());assertEquals(m.events().size(),after.events().size());
    }

    @Test void historicalPassportRemainsCorrectableAfterNewCastingCycleAndArchival() {
        var old=approved(false);var move=new UpdateStageRequest();move.setNewStage(PieceStage.CASTING);pieceService.updateStage(first.getId(),move,admin.getUsername());
        var newer=service.create(complete(),worker.getUsername());
        first.setArchivedAt(LocalDateTime.now());pieceRepository.saveAndFlush(first);
        var after=service.correct(old.id(),correction(old,"اصلاح سابقه قدیمی",null,false),admin.getUsername());
        assertEquals("اصلاح سابقه قدیمی",after.notes());assertEquals(MeltPassportStatus.APPROVED,after.status());
        var current=service.get(newer.id(),admin.getUsername());assertEquals(newer.version(),current.version());assertNull(current.notes());
        assertEquals(first.getId(),passports.findById(newer.id()).orElseThrow().getPieces().get(0).getActivePieceKey());
    }

    @Test void auditKeepsFullDecimalPrecisionInsteadOfConvertingThroughDouble() {
        var m=approved(false);var r=form(m);var precise=new BigDecimal("1234567890123456.12345678");r.setPrimaryWeight(precise);
        var after=service.correct(m.id(),new MeltPassportCorrectionRequest(r,null,false),admin.getUsername());
        var e=after.events().get(after.events().size()-1);
        assertEquals(0,precise.compareTo(e.newValues().get("primaryWeight").decimalValue()));
        em.flush();em.clear();
        after=service.get(m.id(),admin.getUsername());e=after.events().get(after.events().size()-1);
        assertEquals(0,precise.compareTo(e.newValues().get("primaryWeight").decimalValue()));
    }
}

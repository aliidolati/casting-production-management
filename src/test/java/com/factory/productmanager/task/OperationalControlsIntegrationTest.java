package com.factory.productmanager.task;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.jayway.jsonpath.JsonPath;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class OperationalControlsIntegrationTest extends TaskTestBase {
 @Autowired MeltPassportService melts;
 @Autowired MeltPassportPieceRepository links;
 @Autowired PieceWeldingDispatchRepository dispatches;
 @Autowired PieceStageHistoryRepository history;
 @Autowired HeatTreatmentService heats;
 @Autowired HeatBatchEventRepository batchEvents;
 @Autowired HeatTreatmentBatchRepository batches;
 @Autowired PieceHeatTreatmentRepository heatRecords;
 Piece piece(){return newPiece("OPS-"+UUID.randomUUID(),"قطعه آزمایشی",1);}
 User permitted(Permission p){return newUser("ops-"+UUID.randomUUID(),Role.ADMIN,Set.of(p));}
 MeltPassportResponse draft(Piece p){p.setCurrentStage(PieceStage.CASTING);pieceRepository.saveAndFlush(p);var r=new MeltPassportRequest();r.setVersion(0L);r.setPieceIds(List.of(p.getId()));return melts.create(r,admin.getUsername());}
 Map<String,Object> cancel(long version){return Map.of("version",version,"reason","ثبت اشتباه آزمایشی");}
 @Test void draftCancellationPreservesMembershipHistoryReleasesActiveKeyAndClosesEntryTask()throws Exception{
  var p=piece();var m=draft(p);var ed=permitted(Permission.CANCEL_MELT_PASSPORT);
  call(post("/api/melt-passports/"+m.id()+"/cancel"),worker,cancel(m.version())).andExpect(status().isForbidden());
  call(post("/api/melt-passports/"+m.id()+"/cancel"),ed,cancel(m.version())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.editable").value(false));
  assertTrue(links.findByActivePieceKey(p.getId()).isEmpty());assertEquals(1,links.findByPiece_IdOrderByPassport_CreatedAtDesc(p.getId()).size());
  assertEquals(TaskStatus.CANCELLED,taskRepository.findByWorkflowIdAndTaskType(m.id(),TaskType.MELT_PASSPORT_ENTRY).get(0).getStatus());
  assertEquals(PieceStage.CASTING,p.getCurrentStage());assertEquals(0,history.findByPieceIdOrderByChangedAtDesc(p.getId()).size());
  assertNotEquals(m.id(),draft(p).id());
 }
 @Test void cancellationRequiresReasonCurrentVersionAndNeverCancelsAnApprovedPassport()throws Exception{
  var p=piece();var m=draft(p);
  call(post("/api/melt-passports/"+m.id()+"/cancel"),admin,Map.of("version",m.version(),"reason","")).andExpect(status().isBadRequest());
  call(post("/api/melt-passports/"+m.id()+"/cancel"),admin,cancel(m.version()+1)).andExpect(status().isConflict());
  // A synthetic approved fixture exercises the immutable status guard without manufacturing a new stage move.
  var entity=links.findByActivePieceKey(p.getId()).orElseThrow().getPassport();entity.setStatus(MeltPassportStatus.APPROVED);links.flush();
  call(post("/api/melt-passports/"+m.id()+"/cancel"),admin,cancel(entity.getVersion())).andExpect(status().isBadRequest());assertTrue(links.findByActivePieceKey(p.getId()).isPresent());
 }
 @Test void pendingAndReturnedCancellationClosesOnlyOpenWorkflowTasks()throws Exception{
  for(boolean returned:List.of(false,true)){
   var p=piece();var m=draft(p);var r=new MeltPassportRequest();r.setVersion(m.version());r.setPieceIds(List.of(p.getId()));r.setMeltDate(LocalDate.of(2026,11,2));r.setFurnaceNumber(java.math.BigDecimal.ONE);r.setIngotType("آزمایشی");r.setPrimaryWeight(java.math.BigDecimal.TEN);r.setRecycledWeight(java.math.BigDecimal.ZERO);r.setInjectionOperator("آزمایشی");r.setChecks(Arrays.stream(MeltCheckKind.values()).map(k->new MeltPassportRequest.CheckLine(k,MeltCheckStatus.ACCEPTED,null)).toList());
   m=melts.update(m.id(),r,admin.getUsername());m=melts.submit(m.id(),new MeltDecisionRequest(m.version(),null),admin.getUsername());
   if(returned)m=melts.returnForCorrection(m.id(),new MeltDecisionRequest(m.version(),"اصلاح"),admin.getUsername());
   m=melts.cancel(m.id(),new MeltDecisionRequest(m.version(),"لغو"),admin.getUsername());
   for(var type:List.of(TaskType.MELT_PASSPORT_ENTRY,TaskType.MELT_PASSPORT_APPROVAL))assertTrue(taskRepository.findByWorkflowIdAndTaskType(m.id(),type).stream().allMatch(t->t.getStatus().isTerminal()));
   call(post("/api/melt-passports/"+m.id()+"/approve"),admin,Map.of("version",m.version())).andExpect(status().isBadRequest());
  }
 }
 PieceWeldingDispatch send(Piece p)throws Exception{
  call(post("/api/pieces/"+p.getId()+"/welding-station"),admin,Map.of("defectType","CR")).andExpect(status().isCreated());
  return dispatches.findFirstByPieceIdAndCompletedAtIsNullOrderBySentAtDesc(p.getId()).orElseThrow();
 }
 Map<String,Object> weldCancel(){return Map.of("reason","اعزام اشتباه","noWorkConfirmed",true);}
 @Test void weldingCancellationRestoresDateAndStageAndKeepsBothHistoryRecords()throws Exception{
  var p=piece();p.setProcessDate(LocalDate.of(2026,10,20));pieceRepository.saveAndFlush(p);var d=send(p);
  call(post("/api/pieces/"+p.getId()+"/welding-dispatches/"+d.getId()+"/cancel"),worker,weldCancel()).andExpect(status().isForbidden());
  call(post("/api/pieces/"+p.getId()+"/welding-dispatches/"+d.getId()+"/cancel"),permitted(Permission.CANCEL_WELDING_DISPATCH),weldCancel()).andExpect(status().isOk());
  assertEquals(PieceStage.NOT_STARTED,p.getCurrentStage());assertEquals(LocalDate.of(2026,10,20),p.getProcessDate());assertFalse(d.isOpen());assertNotNull(d.getCancelledAt());assertEquals(2,history.findByPieceIdOrderByChangedAtDesc(p.getId()).size());
  call(get("/api/pieces/"+p.getId()+"/welding-dispatches"),admin).andExpect(jsonPath("$[0].cancellationReason").value("اعزام اشتباه"));
 }
 @Test void weldingCancellationRequiresNoWorkConfirmationAndRejectsAnotherPiecesDispatch()throws Exception{
  var p=piece();var d=send(p);
  call(post("/api/pieces/"+p.getId()+"/welding-dispatches/"+d.getId()+"/cancel"),admin,Map.of("reason","اشتباه","noWorkConfirmed",false)).andExpect(status().isBadRequest());
  call(post("/api/pieces/"+piece().getId()+"/welding-dispatches/"+d.getId()+"/cancel"),admin,weldCancel()).andExpect(status().isNotFound());assertTrue(d.isOpen());
 }
 @Test void weldingCancellationRejectsLaterPieceChangesEvenForManager()throws Exception{
  var p=piece();var d=send(p);p.setDescription("اصلاح پس از اعزام");pieceRepository.saveAndFlush(p);
  call(post("/api/pieces/"+p.getId()+"/welding-dispatches/"+d.getId()+"/cancel"),admin,weldCancel()).andExpect(status().isBadRequest());assertTrue(d.isOpen());
 }
 @Test void weldingCancellationRejectsLaterHeatRecordsAndLegacyMissingSnapshot()throws Exception{
  var p=piece();var d=send(p);var r=new CreatePieceHeatTreatmentRequest();r.setBatchCode("LATER-"+UUID.randomUUID());r.setTreatmentDate(LocalDate.of(2026,11,2));heats.recordForPiece(p.getId(),r,admin.getUsername());
  call(post("/api/pieces/"+p.getId()+"/welding-dispatches/"+d.getId()+"/cancel"),admin,weldCancel()).andExpect(status().isBadRequest());
  var old=piece();var legacy=send(old);legacy.setDispatchPieceVersion(null);dispatches.saveAndFlush(legacy);
  call(post("/api/pieces/"+old.getId()+"/welding-dispatches/"+legacy.getId()+"/cancel"),admin,weldCancel()).andExpect(status().isBadRequest());
 }
 HeatTreatmentBatchResponse batch(Piece... ps){String code="BATCH-"+UUID.randomUUID();for(var p:ps){var r=new CreatePieceHeatTreatmentRequest();r.setBatchCode(code);r.setTreatmentDate(LocalDate.of(2026,10,2));heats.recordForPiece(p.getId(),r,admin.getUsername());}return heats.findBatchByCode(code).orElseThrow();}
 Map<String,Object> cycle(HeatTreatmentBatchResponse b){var r=new HashMap<String,Object>();r.put("version",b.getVersion());r.put("treatmentDate","2026-11-02");r.put("temperatureC",165);r.put("durationHours",4);r.put("reason","اصلاح آزمایشی سیکل");r.put("confirmedPieceIds",b.getPieces().stream().map(HeatTreatmentBatchPieceResponse::getPieceId).toList());return r;}
 @Test void batchArchiveUsesHistoricalDatesExactCodePaginationAndDetails()throws Exception{
  var p=piece();var b=batch(p);
  call(get("/api/heat-treatment-batches/archive").param("code",b.getBatchCode()).param("from","2026-10-01").param("to","2026-10-03").param("size","1"),worker).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  call(get("/api/heat-treatment-batches/"+b.getId()),worker).andExpect(status().isOk()).andExpect(jsonPath("$.pieces[0].pieceId").value(p.getId().toString()));
  call(get("/api/heat-treatment-batches/archive").param("code",b.getBatchCode().substring(0,10)),worker).andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void batchEditRequiresSeparatePermissionAndRecordsAffectedMembersWithoutMovingPieces()throws Exception{
  var p=piece();var q=piece();var b=batch(p,q);var stage=p.getCurrentStage();var date=p.getProcessDate();
  call(patch("/api/heat-treatment-batches/"+b.getId()),permitted(Permission.MANAGE_HEAT_TREATMENT_LOG),cycle(b)).andExpect(status().isForbidden());
  var ed=permitted(Permission.EDIT_HEAT_TREATMENT_BATCH);
  call(patch("/api/heat-treatment-batches/"+b.getId()),ed,cycle(b)).andExpect(status().isOk()).andExpect(jsonPath("$.pieces.length()").value(2));
  var records=heatRecords.findByPieceIdOrderByCreatedAtDesc(p.getId());assertEquals(LocalDate.of(2026,11,2),records.get(0).getBatch().getTreatmentDate());assertEquals(stage,p.getCurrentStage());assertEquals(date,p.getProcessDate());assertTrue(history.findByPieceIdOrderByChangedAtDesc(p.getId()).isEmpty());
  call(get("/api/heat-treatment-batches/"+b.getId()+"/history"),ed).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  call(get("/api/heat-treatment-batches/"+b.getId()+"/history"),worker).andExpect(status().isForbidden());
 }
 @Test void batchEditRejectsStaleCycleAndChangedMembershipBeforeWritingAudit()throws Exception{
  var p=piece();var b=batch(p);var body=cycle(b);body.put("confirmedPieceIds",List.of());
  call(patch("/api/heat-treatment-batches/"+b.getId()),admin,body).andExpect(status().isConflict());
  body=cycle(b);body.put("version",99);
  call(patch("/api/heat-treatment-batches/"+b.getId()),admin,body).andExpect(status().isConflict());
  assertEquals(0,batchEvents.findByBatchId(b.getId(),org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
  call(patch("/api/heat-treatment-batches/"+b.getId()),admin,cycle(b)).andExpect(status().isOk());
  call(patch("/api/heat-treatment-batches/"+b.getId()),admin,cycle(b)).andExpect(status().isConflict());
 }
}

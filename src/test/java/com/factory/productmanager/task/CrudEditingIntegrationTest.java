package com.factory.productmanager.task;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.time.*;
import com.jayway.jsonpath.JsonPath;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class CrudEditingIntegrationTest extends TaskTestBase {
 @Autowired PieceNoteRepository notes;
 @Autowired WeldingMapMarkRepository marks;
 @Autowired PieceHeatTreatmentRepository heat;
 @Autowired HeatTreatmentBatchRepository batches;
 @Autowired MeltMaterialRepository materials;
 @Autowired RecordEditEventRepository events;
 User editor(Permission p){return newUser("edit-"+UUID.randomUUID(),Role.ADMIN,Set.of(p));}
 Piece piece(){return newPiece("CRUD-"+UUID.randomUUID(),"قطعه آزمایشی",1);}
 void history(String kind,UUID id,User actor,int count)throws Exception{call(get("/api/record-edits/"+kind+"/"+id),actor).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(count));}
 @Test void noteOwnerAndManagerCanCorrectButOtherEditorsCannot()throws Exception{
  var owner=editor(Permission.MANAGE_PIECE_NOTES);var other=editor(Permission.MANAGE_PIECE_NOTES);var p=piece();
  String result=call(post("/api/pieces/"+p.getId()+"/notes"),owner,Map.of("content","متن اولیه")).andReturn().getResponse().getContentAsString();UUID id=UUID.fromString(JsonPath.read(result,"$.id"));
  var n=notes.findById(id).orElseThrow();var created=n.getCreatedAt();
  call(patch("/api/notes/"+id),other,Map.of("content","ممنوع")).andExpect(status().isForbidden());
  history("notes",id,owner,0);call(get("/api/record-edits/notes/"+id),other).andExpect(status().isForbidden());
  call(patch("/api/notes/"+id),owner,Map.of("content","اصلاح نویسنده")).andExpect(status().isNoContent());
  call(patch("/api/notes/"+id),admin,Map.of("content","اصلاح مدیر")).andExpect(status().isNoContent());
  assertEquals(created,notes.findById(id).orElseThrow().getCreatedAt());assertEquals(owner.getUsername(),n.getCreatedByUsername());history("notes",id,owner,2);
  var all=events.findAll();assertTrue(all.stream().anyMatch(e->e.getTargetId().equals(id)&&e.getBeforeState().contains("متن اولیه")));
 }
 @Test void blankNotesAndArchivedEditsAreRejectedWithoutHistory()throws Exception{
  var p=piece();var n=new PieceNote();n.setPiece(p);n.setContent("قدیمی");notes.saveAndFlush(n);
  call(patch("/api/notes/"+n.getId()),admin,Map.of("content","")).andExpect(status().isBadRequest());
  p.setArchivedAt(LocalDateTime.now());pieceRepository.saveAndFlush(p);
  call(patch("/api/notes/"+n.getId()),admin,Map.of("content","جدید")).andExpect(status().isBadRequest());history("notes",n.getId(),admin,0);
 }
 @Test void markCorrectionsKeepIdentityAndCreationMetadata()throws Exception{
  var p=piece();var m=new WeldingMapMark();m.setPiece(p);m.setXPercent(.2);m.setYPercent(.4);m.setDefectType("CR");m.setCreatedBy("Synthetic creator");marks.saveAndFlush(m);var time=m.getCreatedAt();
  var body=Map.of("xPercent",.6,"yPercent",.7,"defectType","OTHER","defectTypeCustom","عیب آزمایشی","note","شرح جدید");
  call(patch("/api/welding-marks/"+m.getId()),worker,body).andExpect(status().isForbidden());
  call(patch("/api/welding-marks/"+m.getId()),editor(Permission.MANAGE_WELDING_MAP),body).andExpect(status().isNoContent());
  assertEquals(.6,m.getXPercent());assertEquals(time,m.getCreatedAt());assertEquals("Synthetic creator",m.getCreatedBy());history("welding-marks",m.getId(),admin,1);
  call(patch("/api/welding-marks/"+m.getId()),admin,Map.of("xPercent",2,"yPercent",.5,"defectType","CR")).andExpect(status().isBadRequest());history("welding-marks",m.getId(),admin,1);
 }
 @Test void heatCorrectionChangesOnlyThisPieceAndDoesNotMoveProduction()throws Exception{
  var p=piece();var b=new HeatTreatmentBatch();b.setBatchCode("EDIT-"+UUID.randomUUID());b.setTreatmentDate(LocalDate.of(2026,11,2));batches.saveAndFlush(b);
  var h=new PieceHeatTreatment();h.setPiece(p);h.setBatch(b);h.setTestPlateLabel("P1");heat.saveAndFlush(h);
  var stage=p.getCurrentStage();var date=p.getProcessDate();
  var body=Map.of("testPlateLabel","P2","testPlateHardnessHb",85,"details","اصلاح","treatmentStage","مرحله اول");
  call(patch("/api/heat-treatments/"+h.getId()),worker,body).andExpect(status().isForbidden());
  call(patch("/api/heat-treatments/"+h.getId()),editor(Permission.MANAGE_HEAT_TREATMENT_LOG),body).andExpect(status().isNoContent());
  assertEquals("P2",h.getTestPlateLabel());assertEquals(b.getId(),h.getBatch().getId());assertEquals(LocalDate.of(2026,11,2),b.getTreatmentDate());assertEquals(stage,p.getCurrentStage());assertEquals(date,p.getProcessDate());history("heat-treatments",h.getId(),admin,1);
  call(patch("/api/heat-treatments/"+h.getId()),admin,Map.of("testPlateHardnessHb",-1)).andExpect(status().isBadRequest());history("heat-treatments",h.getId(),admin,1);
 }
 @Test void materialCatalogRequiresExplicitPermissionAndRejectsDuplicateRename()throws Exception{
  var ed=editor(Permission.MANAGE_MELT_MATERIALS);var body=Map.of("name","ماده جدید","category","افزودنی");
  call(post("/api/melt-passports/materials"),worker,body).andExpect(status().isForbidden());
  var result=call(post("/api/melt-passports/materials"),ed,body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();UUID id=UUID.fromString(JsonPath.read(result,"$.id"));
  call(patch("/api/melt-passports/materials/"+id),worker,body).andExpect(status().isForbidden());
  call(patch("/api/melt-passports/materials/"+id),ed,Map.of("name","نام اصلاحی","category","دسته جدید")).andExpect(status().isNoContent());
  assertEquals("نام اصلاحی",materials.findById(id).orElseThrow().getName());history("materials",id,ed,1);
  call(post("/api/melt-passports/materials"),admin,Map.of("name","نام تکراری","category","دیگر")).andExpect(status().isOk());
  call(patch("/api/melt-passports/materials/"+id),ed,Map.of("name","نام تکراری","category","دیگر")).andExpect(status().isBadRequest());history("materials",id,ed,1);
  call(patch("/api/melt-passports/materials/"+id+"/active").param("active","false"),worker).andExpect(status().isForbidden());
  call(patch("/api/melt-passports/materials/"+id+"/active").param("active","false"),ed).andExpect(status().isOk());assertFalse(materials.findById(id).orElseThrow().isActive());
 }
 @Test void historySurvivesNoteDeletionAndCannotBeMutated()throws Exception{
  var p=piece();var n=new PieceNote();n.setPiece(p);n.setContent("قبل");notes.saveAndFlush(n);
  call(patch("/api/notes/"+n.getId()),admin,Map.of("content","بعد")).andExpect(status().isNoContent());
  call(delete("/api/notes/"+n.getId()),admin).andExpect(status().isNoContent());history("notes",n.getId(),admin,1);
  call(delete("/api/record-edits/notes/"+n.getId()),admin).andExpect(status().isMethodNotAllowed());
 }
}

package com.factory.productmanager.task;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:drawing_catalog;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","app.upload.dir=./target/test-uploads/drawing-catalog"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class DrawingCatalogIntegrationTest extends TaskTestBase {
 @Autowired DrawingRevisionRepository revisions;
 @Autowired DrawingHistoryService drawings;
 @Autowired DrawingStorageService storage;
 @Autowired WeldingMapMarkRepository marks;
 @Autowired PieceNoteRepository notes;
 @Autowired PlatformTransactionManager transactions;
 @Value("${app.upload.dir}") String uploadDir;
 Piece piece(){return newPiece("DRAW-"+UUID.randomUUID(),"قطعه آزمایشی",1);}
 UUID def(Piece p){return p.getPieceDefinition().getId();}
 MockMultipartFile image(String name,byte... data){return new MockMultipartFile("file",name,"image/png",data);}
 org.springframework.test.web.servlet.ResultActions uploadRequest(org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder builder,User user)throws Exception{return mvc.perform(builder.header("Authorization","Bearer "+token(user)));}
 void upload(UUID id,MockMultipartFile f)throws Exception{uploadRequest(multipart("/api/piece-definitions/"+id+"/drawing").file(f),admin).andExpect(status().isOk());}
 String current(UUID id)throws Exception{return JsonPath.read(call(get("/api/piece-definitions/"+id+"/drawing-current-id"),admin).andReturn().getResponse().getContentAsString(),"$.id");}
 String path(UUID id){return definitionRepository.findById(id).orElseThrow().getDrawingImagePath();}
 String token(UUID id,boolean project)throws Exception{return JsonPath.read(call(get("/api/"+(project?"projects":"piece-definitions")+"/"+id+"/deletion-preview"),admin).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"$.confirmationToken");}
 @Test void replacementRetainsBytesMetadataAndRestoreCreatesNewCurrentRevision()throws Exception{
  var p=piece();UUID id=def(p);upload(id,image("first.png",(byte)1));String first=current(id);String firstPath=path(id);
  upload(id,image("second.png",(byte)2));String second=current(id);assertNotEquals(first,second);assertNotEquals(firstPath,path(id));assertTrue(Files.exists(Path.of(uploadDir,firstPath)));
  call(get("/api/piece-definitions/"+id+"/drawing-revisions/"+first),worker).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1}));
  call(get("/api/piece-definitions/"+id+"/drawing-revisions"),worker).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].uploadedBy").value(admin.getUsername()));
  call(post("/api/piece-definitions/"+id+"/drawing-revisions/"+first+"/restore"),worker).andExpect(status().isForbidden());
  call(post("/api/piece-definitions/"+id+"/drawing-revisions/"+first+"/restore"),admin).andExpect(status().isOk());
  assertNotEquals(first,current(id));assertEquals(firstPath,path(id));
  call(get("/api/piece-definitions/"+id+"/drawing-revisions"),admin).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.items[0].sourceRevisionId").value(first));
 }
 @Test void invalidReplacementAndRemoveCurrentNeverDestroyHistoricalFile()throws Exception{
  var p=piece();UUID id=def(p);upload(id,image("first.png",(byte)1));String oldPath=path(id);String revision=current(id);
  uploadRequest(multipart("/api/piece-definitions/"+id+"/drawing").file(new MockMultipartFile("file","bad.txt","text/plain",new byte[]{9})),admin).andExpect(status().isBadRequest());assertEquals(oldPath,path(id));
  call(delete("/api/piece-definitions/"+id+"/drawing"),admin).andExpect(status().isOk());assertNull(path(id));assertTrue(Files.exists(Path.of(uploadDir,oldPath)));
  call(get("/api/piece-definitions/"+id+"/drawing-revisions/"+revision),worker).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1}));
 }
 @Test void revisionOwnershipIsCheckedAndDrawingReadsDoNotCreateHistory()throws Exception{
  var p=piece();var q=piece();upload(def(p),image("first.png",(byte)1));String revision=current(def(p));
  call(get("/api/piece-definitions/"+def(q)+"/drawing-revisions/"+revision),admin).andExpect(status().isNotFound());
  call(post("/api/piece-definitions/"+def(q)+"/drawing-revisions/"+revision+"/restore"),admin).andExpect(status().isNotFound());
  call(get("/api/piece-definitions/"+def(q)+"/drawing-revisions"),worker).andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void transactionRollbackCleansOnlyTheNewFile()throws Exception{
  var p=piece();UUID id=def(p);upload(id,image("first.png",(byte)1));String oldPath=path(id);String[] rolledPath={null};
  new TransactionTemplate(transactions).execute(status->{drawings.upload(id,image("rollback.png",(byte)9),admin.getUsername());rolledPath[0]=path(id);status.setRollbackOnly();return null;});
  assertEquals(oldPath,path(id));assertTrue(Files.exists(Path.of(uploadDir,oldPath)));assertFalse(Files.exists(Path.of(uploadDir,rolledPath[0])));
 }
 @Test void newMarksArePinnedAndStaleDrawingRequestsAreRejected()throws Exception{
  var p=piece();UUID id=def(p);upload(id,image("first.png",(byte)1));String first=current(id);
  var body=new HashMap<String,Object>();body.put("xPercent",.2);body.put("yPercent",.3);body.put("defectType","CR");body.put("drawingRevisionId",first);
  call(post("/api/pieces/"+p.getId()+"/welding-marks"),admin,body).andExpect(status().isCreated()).andExpect(jsonPath("$.drawingRevisionId").value(first));
  upload(id,image("second.png",(byte)2));call(post("/api/pieces/"+p.getId()+"/welding-marks"),admin,body).andExpect(status().isBadRequest());
  call(get("/api/pieces/"+p.getId()+"/welding-marks"),admin).andExpect(jsonPath("$[0].drawingRevisionId").value(first));
 }
 @Test void legacyCurrentIsCapturedWithUnknownAuthorAndMarksBeforeFirstReplacement()throws Exception{
  var p=piece();UUID id=def(p);var stored=storage.storeDefinitionDrawing(id,image("legacy.png",(byte)4));var d=definitionRepository.findById(id).orElseThrow();d.setDrawingImagePath(stored.relativePath());definitionRepository.saveAndFlush(d);
  var m=new WeldingMapMark();m.setPiece(p);m.setXPercent(.1);m.setYPercent(.1);m.setDefectType("CR");marks.saveAndFlush(m);
  call(get("/api/piece-definitions/"+id+"/drawing-revisions"),worker).andExpect(jsonPath("$.totalElements").value(0));
  upload(id,image("new.png",(byte)5));var rows=revisions.findByDefinitionIdOrderByRevisionNumberDesc(id);assertEquals(2,rows.size());assertNull(rows.get(1).getUploadedAt());assertNull(rows.get(1).getUploadedBy());assertEquals(rows.get(1).getId(),marks.findById(m.getId()).orElseThrow().getDrawingRevisionId());
  assertTrue(Files.exists(Path.of(uploadDir,stored.relativePath())));
 }
 @Test void deletionPreviewCountsArchivedPiecesAndRequiresPermissionAndFreshToken()throws Exception{
  var p=piece();UUID id=def(p);p.setArchivedAt(java.time.LocalDateTime.now());pieceRepository.saveAndFlush(p);upload(id,image("file.png",(byte)1));String before=token(id,false);
  call(get("/api/piece-definitions/"+id+"/deletion-preview"),worker).andExpect(status().isForbidden());
  call(get("/api/piece-definitions/"+id+"/deletion-preview"),admin).andExpect(jsonPath("$.impacts[?(@.key == 'Piece')].count").value(org.hamcrest.Matchers.contains(1)));
  var n=new PieceNote();n.setPiece(p);n.setContent("new note");notes.saveAndFlush(n);
  call(delete("/api/piece-definitions/"+id).param("confirmationToken",before),admin).andExpect(status().isConflict());
  call(delete("/api/piece-definitions/"+id),admin).andExpect(status().isBadRequest());assertTrue(definitionRepository.existsById(id));
 }
 @Test void blockedProjectDeletionPreservesFilesAndShowsTaskDependency()throws Exception{
  var p=piece();UUID id=def(p);upload(id,image("file.png",(byte)1));String filePath=path(id);UUID project=p.getPieceDefinition().getProject().getId();
  var body=taskBody("synthetic project task",worker,TaskPriority.MEDIUM,null);body.put("projectId",project);
  call(post("/api/tasks"),admin,body).andExpect(status().isCreated());
  call(get("/api/projects/"+project+"/deletion-preview"),admin).andExpect(jsonPath("$.canDelete").value(false)).andExpect(jsonPath("$.impacts[?(@.key == 'Task')].count").value(org.hamcrest.Matchers.contains(1)));
  call(delete("/api/projects/"+project).param("confirmationToken",token(project,true)),admin).andExpect(status().isConflict());assertTrue(Files.exists(Path.of(uploadDir,filePath)));
 }
 @Test void confirmedDeletionCleansFilesOnlyAfterCommitAndRemovesRevisions()throws Exception{
  var p=piece();UUID id=def(p);upload(id,image("first.png",(byte)1));String first=path(id);upload(id,image("second.png",(byte)2));String second=path(id);
  call(delete("/api/piece-definitions/"+id).param("confirmationToken",token(id,false)),admin).andExpect(status().isNoContent());
  assertFalse(definitionRepository.existsById(id));assertFalse(Files.exists(Path.of(uploadDir,first)));assertFalse(Files.exists(Path.of(uploadDir,second)));assertTrue(revisions.findByDefinitionIdOrderByRevisionNumberDesc(id).isEmpty());
 }
}

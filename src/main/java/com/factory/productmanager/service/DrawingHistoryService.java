package com.factory.productmanager.service;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.model.dto.PageResponse;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.exception.ResourceNotFoundException;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class DrawingHistoryService {
 private final EntityManager em;
 private final DrawingRevisionRepository revisions;
 private final DrawingStorageService storage;
 private final PermissionService permissions;
 public record View(UUID id,int revisionNumber,String originalName,LocalDateTime uploadedAt,LocalDateTime capturedAt,String uploadedBy,boolean current,UUID sourceRevisionId){}
 public PieceDefinition lock(UUID id){var d=em.find(PieceDefinition.class,id,LockModeType.PESSIMISTIC_WRITE);if(d==null)throw new ResourceNotFoundException("تعریف قطعه پیدا نشد");return d;}
 public DrawingRevision ensureCurrent(PieceDefinition d){
  if(d.getDrawingImagePath()==null||d.getDrawingImagePath().isBlank())return null;
  var existing=revisions.findFirstByDefinitionIdAndRelativePathOrderByRevisionNumberDesc(d.getId(),d.getDrawingImagePath());
  if(existing.isPresent())return existing.get();
  var legacy=new DrawingRevision();legacy.setDefinition(d);legacy.setRevisionNumber(revisions.findFirstByDefinitionIdOrderByRevisionNumberDesc(d.getId()).map(previous->previous.getRevisionNumber()+1).orElse(1));legacy.setRelativePath(d.getDrawingImagePath());legacy.setOriginalName("نقشه پیشین؛ زمان و ثبت‌کننده نامشخص");legacy.setCapturedAt(LocalDateTime.now());revisions.saveAndFlush(legacy);
  em.createQuery("UPDATE WeldingMapMark m SET m.drawingRevisionId=:revision WHERE m.piece.id IN (SELECT p.id FROM Piece p WHERE p.pieceDefinition.id=:definition) AND m.drawingRevisionId IS NULL").setParameter("revision",legacy.getId()).setParameter("definition",d.getId()).executeUpdate();
  return legacy;
 }
 private void require(String name){if(!permissions.hasPermission(name,Permission.MANAGE_PIECE_DEFINITIONS))throw new org.springframework.security.access.AccessDeniedException("مجوز مدیریت تعریف قطعه لازم است");}
 public PieceDefinition upload(UUID id,MultipartFile file,String actor){require(actor);var d=lock(id);ensureCurrent(d);var stored=storage.storeDefinitionDrawing(id,file);add(d,stored.relativePath(),file.getOriginalFilename(),actor,null);return d;}
 private void add(PieceDefinition d,String path,String filename,String actor,UUID source){
  var v=new DrawingRevision();v.setDefinition(d);v.setRevisionNumber(revisions.findFirstByDefinitionIdOrderByRevisionNumberDesc(d.getId()).map(previous->previous.getRevisionNumber()+1).orElse(1));v.setRelativePath(path);v.setOriginalName(filename==null?null:filename.substring(0,Math.min(255,filename.length())));v.setUploadedAt(LocalDateTime.now());v.setCapturedAt(v.getUploadedAt());v.setUploadedBy(actor);v.setSourceRevisionId(source);revisions.saveAndFlush(v);d.setDrawingImagePath(path);
 }
 public PieceDefinition restore(UUID definition,UUID revision,String actor){require(actor);var d=lock(definition);ensureCurrent(d);var old=owned(definition,revision);storage.loadAsResource(old.getRelativePath());add(d,old.getRelativePath(),old.getOriginalName(),actor,old.getId());return d;}
 public PieceDefinition removeCurrent(UUID id,String actor){require(actor);var d=lock(id);ensureCurrent(d);d.setDrawingImagePath(null);return d;}
 private DrawingRevision owned(UUID definition,UUID revision){return revisions.findByIdAndDefinitionId(revision,definition).orElseThrow(()->new ResourceNotFoundException("نسخه نقشه پیدا نشد"));}
 @Transactional(readOnly=true) public Resource resource(UUID definition,UUID revision){return storage.loadAsResource(owned(definition,revision).getRelativePath());}
 @Transactional(readOnly=true) public MediaType mediaType(UUID definition,UUID revision){return storage.mediaTypeFor(owned(definition,revision).getRelativePath());}
 @Transactional(readOnly=true) public PageResponse<View> list(UUID id,int page,int size){
  var d=em.find(PieceDefinition.class,id);if(d==null)throw new ResourceNotFoundException("تعریف قطعه پیدا نشد");
  var result=revisions.findByDefinitionId(id,PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size)),Sort.by(Sort.Direction.DESC,"revisionNumber")));
  // Legacy files are captured on the first explicit write, never by a GET.
  var currentRow=revisions.findFirstByDefinitionIdAndRelativePathOrderByRevisionNumberDesc(id,d.getDrawingImagePath()).map(DrawingRevision::getId).orElse(null);
  return PageResponse.from(result,v->new View(v.getId(),v.getRevisionNumber(),v.getOriginalName(),v.getUploadedAt(),v.getCapturedAt(),v.getUploadedBy(),Objects.equals(v.getId(),currentRow),v.getSourceRevisionId()));
 }
 @Transactional(readOnly=true) public UUID currentId(UUID id){var d=em.find(PieceDefinition.class,id);if(d==null)throw new ResourceNotFoundException("تعریف قطعه پیدا نشد");return revisions.findFirstByDefinitionIdAndRelativePathOrderByRevisionNumberDesc(id,d.getDrawingImagePath()).map(DrawingRevision::getId).orElse(null);}
}

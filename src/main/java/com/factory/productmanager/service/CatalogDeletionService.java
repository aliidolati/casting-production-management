package com.factory.productmanager.service;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.exception.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Service @RequiredArgsConstructor @Transactional
public class CatalogDeletionService {
 private final EntityManager em;
 private final DrawingStorageService storage;
 public record Impact(String key,String label,long count,boolean blocksDeletion){}
 public record Preview(String label,List<Impact> impacts,String confirmationToken,boolean canDelete){}
 private record Target(Object entity,String label,UUID projectId,UUID definitionId){}
 private Target target(UUID id,boolean project,boolean lock){
  if(project){var p=lock?em.find(Project.class,id,LockModeType.PESSIMISTIC_WRITE):em.find(Project.class,id);if(p==null)throw new ResourceNotFoundException("پروژه پیدا نشد");return new Target(p,p.getCode()+" — "+p.getName(),id,null);}
  var d=lock?em.find(PieceDefinition.class,id,LockModeType.PESSIMISTIC_WRITE):em.find(PieceDefinition.class,id);if(d==null)throw new ResourceNotFoundException("تعریف قطعه پیدا نشد");return new Target(d,d.getRowNum()+" — "+d.getPieceName(),null,id);
 }
 private String condition(String prefix,boolean project){return prefix+(project?".project.id=:id":".id=:id");}
 private List<String> ids(String entity,String where,UUID id,boolean lock){
  var q=em.createQuery("SELECT x.id FROM "+entity+" x WHERE "+where,UUID.class).setParameter("id",id);
  if(lock)q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
  return q.getResultList().stream().map(UUID::toString).sorted().toList();
 }
 private Preview inspect(UUID id,boolean project,boolean lock){
  Target target=target(id,project,lock);String d=condition("x.pieceDefinition",project);String p=condition("x.piece.pieceDefinition",project);
  var impacts=new ArrayList<Impact>();var fingerprint=new TreeMap<String,Object>();fingerprint.put("target",id.toString());fingerprint.put("label",target.label());
  class Section {void add(String entity,String label,String where,boolean blocked){var rows=ids(entity,where,id,lock);impacts.add(new Impact(entity,label,rows.size(),blocked));fingerprint.put(entity,rows);}}
  var s=new Section();
  s.add("PieceDefinition","تعریف قطعات",project?"x.project.id=:id":"x.id=:id",false);
  s.add("Piece","قطعات (فعال و بایگانی)",d,false);
  s.add("DrawingRevision","نسخه‌های نقشه",condition("x.definition",project),false);
  s.add("PieceStageHistory","تاریخچه مراحل",p,false);s.add("PieceSubStageHistory","تاریخچه زیرمرحله‌ها",p,false);
  s.add("PieceNote","یادداشت‌ها",p,false);s.add("WeldingMapMark","نشان‌های نقشه جوش",p,false);s.add("RadiographyDefect","عیوب رادیوگرافی",p,false);
  s.add("TensileTest","تست‌های کشش",p,false);s.add("PieceWeldingDispatch","اعزام‌های جوشکاری",p,false);s.add("PieceScrapDispatch","سوابق اسقاط",p,false);s.add("PieceStageSkip","سوابق قدیمی انتقال",p,false);
  s.add("RadiographyReportTemplate","قالب‌های گزارش رادیوگرافی",condition("x.pieceDefinition",project),true);
  s.add("RadiographyReport","گزارش‌های رادیوگرافی",p,true);s.add("RadiographyWorkflow","گردش‌کارهای رادیوگرافی",p,true);
  s.add("PieceHeatTreatment","سوابق عملیات حرارتی",p,true);s.add("MeltPassportPiece","ارتباط شناسنامه‌های ذوب",p,true);
  s.add("PieceStageProcessDate","تاریخ‌های فرایند مراحل",p,true);s.add("PieceSubStageProcessDate","تاریخ‌های فرایند زیرمرحله‌ها",p,true);
  s.add("Task","وظایف مرتبط",project?"x.project.id=:id OR EXISTS (SELECT p.id FROM Piece p WHERE p.id=x.part.id AND p.pieceDefinition.project.id=:id)":"x.part.pieceDefinition.id=:id",true);
  // Include mutable current paths so replacing/removing a legacy drawing invalidates an old preview.
  var definitions=em.createQuery("SELECT x FROM PieceDefinition x WHERE "+(project?"x.project.id=:id":"x.id=:id"),PieceDefinition.class).setParameter("id",id).getResultList();
  impacts.add(new Impact("CurrentDrawing","نقشه‌های جاری",definitions.stream().filter(x->x.getDrawingImagePath()!=null).count(),false));
  fingerprint.put("drawings",definitions.stream().map(x->x.getId()+":"+Objects.toString(x.getDrawingImagePath(),"")).sorted().toList());
  String token;try{token=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
  return new Preview(target.label(),impacts,token,impacts.stream().noneMatch(x->x.blocksDeletion()&&x.count()>0));
 }
 @Transactional(readOnly=true) public Preview preview(UUID id,boolean project){return inspect(id,project,false);}
 public void delete(UUID id,boolean project,String token){
  if(token==null||token.isBlank())throw new IllegalArgumentException("ابتدا اثر حذف را مرور و تأیید کنید");
  var preview=inspect(id,project,true);
  if(!MessageDigest.isEqual(preview.confirmationToken().getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))throw new DuplicateResourceException("وابستگی‌ها تغییر کرده‌اند؛ پیش‌نمایش حذف را دوباره مرور کنید");
  if(!preview.canDelete())throw new DuplicateResourceException("رکوردهای وابسته مانع حذف هستند؛ پیش‌نمایش را بررسی کنید");
  var definitions=em.createQuery("SELECT x FROM PieceDefinition x WHERE "+(project?"x.project.id=:id":"x.id=:id"),PieceDefinition.class).setParameter("id",id).getResultList();
  Set<String> paths=new HashSet<>();for(var def:definitions){if(def.getDrawingImagePath()!=null)paths.add(def.getDrawingImagePath());for(var v:def.getDrawingRevisions())paths.add(v.getRelativePath());}
  em.remove(target(id,project,true).entity());em.flush();
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){paths.forEach(storage::deleteIfExists);}});
 }
}

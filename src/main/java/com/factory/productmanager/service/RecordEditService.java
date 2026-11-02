package com.factory.productmanager.service;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.exception.ResourceNotFoundException;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class RecordEditService {
 private final EntityManager em;
 private final UserRepository users;
 private final PermissionService permissions;
 private final RecordEditEventRepository events;
 private final MeltMaterialRepository materials;
 private final ObjectMapper mapper;
 private User actor(String name) { return users.findByUsername(name).filter(u->!u.isDisabled()).orElseThrow(()->new AccessDeniedException("حساب فعال لازم است")); }
 private void require(User u, Permission p) { if(!permissions.hasPermission(u.getUsername(),p)) throw new AccessDeniedException("مجوز اصلاح این بخش را ندارید"); }
 private <T> T lock(Class<T> type, UUID id) { T e=em.find(type,id,LockModeType.PESSIMISTIC_WRITE);if(e==null)throw new ResourceNotFoundException("رکورد پیدا نشد");return e; }
 private void active(Piece p) { if(p.getArchivedAt()!=null)throw new IllegalStateException("ابتدا قطعه را از بایگانی بازگردانید"); }
 private String json(Object value) { return mapper.writeValueAsString(value); }
 private void audit(String kind,UUID id,User u,String before,Object after) {String next=json(after);if(!before.equals(next))events.save(new RecordEditEvent(kind,id,u,before,next));}
 private Map<String,Object> state(Object... pairs) {Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
 private String trim(String s){return s==null||s.isBlank()?null:s.trim();}
 private void noteOwner(PieceNote n,User u){if(!permissions.isManager(u.getUsername())) {require(u,Permission.MANAGE_PIECE_NOTES);if(!u.getUsername().equals(n.getCreatedByUsername()))throw new AccessDeniedException("فقط یادداشت خودتان قابل اصلاح است");}}
 public void note(UUID id,PieceNoteRequest r,String name){
  User u=actor(name);PieceNote n=lock(PieceNote.class,id);noteOwner(n,u);active(n.getPiece());
  String before=json(state("content",n.getContent()));n.setContent(r.getContent());audit("notes",id,u,before,state("content",n.getContent()));
 }
 private Map<String,Object> markState(WeldingMapMark m){return state("xPercent",m.getXPercent(),"yPercent",m.getYPercent(),"defectType",m.getDefectType(),"note",m.getNote());}
 public void mark(UUID id,WeldingMapMarkRequest r,String name){
  User u=actor(name);require(u,Permission.MANAGE_WELDING_MAP);WeldingMapMark m=lock(WeldingMapMark.class,id);active(m.getPiece());
  String before=json(markState(m));String type=DefectTypes.requireStoredType(r.getDefectType(),r.getDefectTypeCustom());
  m.setXPercent(r.getXPercent());m.setYPercent(r.getYPercent());m.setDefectType(DefectTypes.displayLabel(type,DefectTypes.storedCustom(type,r.getDefectTypeCustom())));m.setNote(trim(r.getNote()));audit("welding-marks",id,u,before,markState(m));
 }
 private Map<String,Object> heatState(PieceHeatTreatment r){return state("treatmentStage",r.getTreatmentStage(),"testPlateLabel",r.getTestPlateLabel(),"testPlateHardnessHb",r.getTestPlateHardnessHb(),"details",r.getDetails(),"tensileDispositionId",r.getTensileDisposition()==null?null:r.getTensileDisposition().getId());}
 public void heat(UUID id,UpdatePieceHeatTreatmentRequest r,String name){
  User u=actor(name);require(u,Permission.MANAGE_HEAT_TREATMENT_LOG);PieceHeatTreatment h=lock(PieceHeatTreatment.class,id);active(h.getPiece());
  TensileTestDisposition d=r.getTensileDispositionId()==null?null:em.find(TensileTestDisposition.class,r.getTensileDispositionId());
  if(r.getTensileDispositionId()!=null&&(d==null||!d.getTensileTest().getPiece().getId().equals(h.getPiece().getId())))throw new IllegalArgumentException("تعیین‌تکلیف باید مربوط به همین قطعه باشد");
  String before=json(heatState(h));h.setTreatmentStage(trim(r.getTreatmentStage()));h.setTestPlateLabel(trim(r.getTestPlateLabel()));h.setTestPlateHardnessHb(r.getTestPlateHardnessHb());h.setDetails(trim(r.getDetails()));h.setTensileDisposition(d);audit("heat-treatments",id,u,before,heatState(h));
 }
 private Map<String,Object> materialState(MeltMaterial m){return state("name",m.getName(),"category",m.getCategory(),"active",m.isActive());}
 public void material(UUID id,MeltMaterialRequest r,String name){
  User u=actor(name);require(u,Permission.MANAGE_MELT_MATERIALS);MeltMaterial m=lock(MeltMaterial.class,id);
  String normalized=normalize(r.name());materials.findByNormalizedName(normalized).filter(x->!x.getId().equals(id)).ifPresent(x->{throw new IllegalArgumentException("نام ماده تکراری است");});
  String before=json(materialState(m));m.setName(r.name().trim());m.setNormalizedName(normalized);m.setCategory(r.category().trim());audit("materials",id,u,before,materialState(m));
 }
 public MeltMaterialResponse activeMaterial(UUID id,boolean active,String name){
  User u=actor(name);require(u,Permission.MANAGE_MELT_MATERIALS);MeltMaterial m=lock(MeltMaterial.class,id);
  String before=json(materialState(m));m.setActive(active);audit("materials",id,u,before,materialState(m));
  return new MeltMaterialResponse(m.getId(),m.getName(),m.getCategory(),m.isActive());
 }
 public static String normalize(String name){String s=java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFKC).replace('ي','ی').replace('ك','ک').replace('\u200c',' ').trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);if(s.isBlank())throw new IllegalArgumentException("نام ماده لازم است");return s;}
 @Transactional(readOnly=true)
 public PageResponse<RecordEditEvent> history(String kind,UUID id,String name,int page,int size){
  User u=actor(name);
  if(!Set.of("notes","welding-marks","heat-treatments","materials").contains(kind))throw new IllegalArgumentException("بخش نامعتبر است");
  if(!permissions.isManager(name))switch(kind){
   case "notes" -> {PieceNote n=em.find(PieceNote.class,id);if(n==null)throw new ResourceNotFoundException("یادداشت پیدا نشد");noteOwner(n,u);}
   case "welding-marks" -> require(u,Permission.MANAGE_WELDING_MAP);
   case "heat-treatments" -> require(u,Permission.MANAGE_HEAT_TREATMENT_LOG);
   case "materials" -> require(u,Permission.MANAGE_MELT_MATERIALS);
   default -> throw new IllegalArgumentException();
  }
  var result=events.findByKindAndTargetId(kind,id,PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size)),Sort.by(Sort.Direction.DESC,"occurredAt","id")));
  return PageResponse.of(result,result.getContent());
 }
}

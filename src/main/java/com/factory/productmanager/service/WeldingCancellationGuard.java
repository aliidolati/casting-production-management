package com.factory.productmanager.service;
import com.factory.productmanager.model.entity.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class WeldingCancellationGuard {
 private final EntityManager em;
 public void assertNoLaterWork(PieceWeldingDispatch d){
  for(String entity:java.util.List.of("TensileTest","PieceHeatTreatment","RadiographyDefect","WeldingMapMark")) {
   Long count=em.createQuery("select count(x) from "+entity+" x where x.piece.id=:piece and x.createdAt>=:sent",Long.class).setParameter("piece",d.getPiece().getId()).setParameter("sent",d.getSentAt()).getSingleResult();
   if(count>0)throw new IllegalStateException("پس از اعزام، عملیات جدید ثبت شده است؛ لغو مجاز نیست");
  }
  Long reports=em.createQuery("select count(x) from RadiographyReport x where x.piece.id=:piece and x.updatedAt>=:sent",Long.class).setParameter("piece",d.getPiece().getId()).setParameter("sent",d.getSentAt()).getSingleResult();
  if(reports>0 || d.getDefects().stream().anyMatch(x->x.isWeldingConfirmed()||x.isWelded()))throw new IllegalStateException("پس از اعزام گزارش یا تأیید جوش ثبت شده است");
 }
}

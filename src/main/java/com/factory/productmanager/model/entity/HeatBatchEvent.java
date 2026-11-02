package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="heat_batch_events",indexes=@Index(name="idx_heat_batch_event_time",columnList="batch_id,occurred_at")) @Getter @NoArgsConstructor
public class HeatBatchEvent {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="batch_id",nullable=false) private UUID batchId;
 @Column(name="actor_id",nullable=false) private UUID actorId;
 @Column(name="actor_name",nullable=false) private String actorName;
 @Column(name="occurred_at",nullable=false) private LocalDateTime occurredAt;
 @Column(nullable=false,length=2000) private String reason;
 @Column(name="before_state",nullable=false,columnDefinition="LONGTEXT") private String beforeState;
 @Column(name="after_state",nullable=false,columnDefinition="LONGTEXT") private String afterState;
 @Column(name="affected_pieces",nullable=false,columnDefinition="LONGTEXT") private String affectedPieces;
 public HeatBatchEvent(UUID batch,User actor,String reason,String before,String after,String affected){
  batchId=batch;actorId=actor.getId();actorName=actor.getFullName()==null?actor.getUsername():actor.getFullName();occurredAt=LocalDateTime.now();this.reason=reason;beforeState=before;afterState=after;affectedPieces=affected;
 }
}

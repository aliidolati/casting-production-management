package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="record_edit_events", indexes=@Index(name="idx_record_edit_target",columnList="kind,target_id,occurred_at"))
@Getter @NoArgsConstructor
public class RecordEditEvent {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=30) private String kind;
 @Column(name="target_id",nullable=false) private UUID targetId;
 @Column(name="actor_id",nullable=false) private UUID actorId;
 @Column(name="actor_username",nullable=false) private String actorUsername;
 @Column(name="occurred_at",nullable=false) private LocalDateTime occurredAt;
 @Column(name="before_state",nullable=false,columnDefinition="LONGTEXT") private String beforeState;
 @Column(name="after_state",nullable=false,columnDefinition="LONGTEXT") private String afterState;
 public RecordEditEvent(String kind, UUID target, User actor, String before, String after) {
  this.kind=kind;this.targetId=target;this.actorId=actor.getId();this.actorUsername=actor.getUsername();
  this.beforeState=before;this.afterState=after;this.occurredAt=LocalDateTime.now();
 }
}

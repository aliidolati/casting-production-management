package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity @Table(name="melt_passport_events", indexes={@Index(name="idx_melt_event_passport_time",columnList="passport_id,occurredAt")}) @Getter @Setter
public class MeltPassportEvent {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;

 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="passport_id",nullable=false) private MeltPassport passport;
 @Column(nullable=false,length=32) private String action;
 private UUID actorId;
 @Column(nullable=false,length=255) private String actorName;
 @Column(nullable=false) private LocalDateTime occurredAt;
 @Column(length=2000) private String reason;
 @Column(name="old_values",columnDefinition="MEDIUMTEXT") private String oldValues;
 @Column(name="new_values",columnDefinition="MEDIUMTEXT") private String newValues;

}

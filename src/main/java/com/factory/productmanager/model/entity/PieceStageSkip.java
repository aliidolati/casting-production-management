package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Getter @NoArgsConstructor
@Table(name="piece_stage_skips", indexes=@Index(name="idx_stage_skip_piece_time",columnList="piece_id,occurred_at"))
public class PieceStageSkip {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="piece_id",nullable=false) private Piece piece;
    @Column(name="actor_id",nullable=false) private UUID actorId;
    @Column(name="actor_username",nullable=false) private String actorUsername;
    @Column(name="actor_name",nullable=false) private String actorName;
    @Column(name="from_stage",nullable=false,length=32) private String fromStage;
    @Column(name="from_sub_stage",length=64) private String fromSubStage;
    @Column(name="to_stage",nullable=false,length=32) private String toStage;
    @Column(name="to_sub_stage",length=64) private String toSubStage;
    @Column(nullable=false,length=1000) private String reason;
    @Column(name="occurred_at",nullable=false) private LocalDateTime occurredAt;
    public PieceStageSkip(Piece piece, User actor, String fromStage, String fromSub, String toStage, String toSub, String reason, LocalDateTime at) {
        this.piece=piece; this.actorId=actor.getId(); this.actorUsername=actor.getUsername();
        this.actorName=actor.getFullName() == null || actor.getFullName().isBlank() ? actor.getUsername() : actor.getFullName();
        this.fromStage=fromStage; this.fromSubStage=fromSub; this.toStage=toStage; this.toSubStage=toSub;
        this.reason=reason; this.occurredAt=at;
    }
}

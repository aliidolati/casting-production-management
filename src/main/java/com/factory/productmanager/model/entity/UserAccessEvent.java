package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/** Append-only snapshots: scalar identities deliberately survive account deletion. Never stores credentials. */
@Entity
@Table(name = "user_access_events", indexes = @Index(name = "idx_user_access_target_time", columnList = "target_id,occurred_at"))
@Getter
@NoArgsConstructor
public class UserAccessEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "actor_id") private UUID actorId;
    @Column(name = "actor_username", nullable = false) private String actorUsername;
    @Column(name = "target_id", nullable = false) private UUID targetId;
    @Column(nullable = false, length = 40) private String action;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;
    @Column(name = "before_state", columnDefinition = "TEXT", nullable = false) private String beforeState;
    @Column(name = "after_state", columnDefinition = "TEXT", nullable = false) private String afterState;

    public UserAccessEvent(User actor, User target, String action, String before, String after, LocalDateTime at) {
        this.actorId = actor == null ? null : actor.getId();
        this.actorUsername = actor == null ? "SYSTEM_INITIALIZATION" : actor.getUsername();
        this.targetId = target.getId(); this.action = action;
        this.beforeState = before; this.afterState = after; this.occurredAt = at;
    }
}

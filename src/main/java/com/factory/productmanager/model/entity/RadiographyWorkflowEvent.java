package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** رویداد ممیزی گردش‌کار تفسیر رادیوگرافی (فقط افزودنی). */
@Entity
@Table(
        name = "radiography_workflow_events",
        indexes = {
                @Index(name = "idx_rg_event_workflow_created", columnList = "workflow_id, created_at"),
                @Index(name = "idx_rg_event_piece", columnList = "piece_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class RadiographyWorkflowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RadiographyWorkflow workflow;

    @Column(name = "piece_id", nullable = false, updatable = false)
    private UUID pieceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 48, columnDefinition = "varchar(48)")
    private RadiographyWorkflowAction action;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_name", length = 255, updatable = false)
    private String actorName;

    @Column(name = "old_value", length = 255, updatable = false)
    private String oldValue;

    @Column(name = "new_value", length = 255, updatable = false)
    private String newValue;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

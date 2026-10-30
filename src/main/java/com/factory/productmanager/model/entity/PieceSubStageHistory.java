package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "piece_sub_stage_history")
@Getter
@Setter
public class PieceSubStageHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference
    private Piece piece;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32, columnDefinition = "varchar(32)")
    private SubStageKind kind;

    @Column(name = "sub_stage", nullable = false, length = 64)
    private String subStage;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by", updatable = false)
    private String changedBy;

    @PrePersist
    protected void onCreate() {
        this.changedAt = LocalDateTime.now();
    }

}

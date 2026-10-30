package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;
@Entity
@Table(name = "piece_stage_history")
@Getter
@Setter
public class PieceStageHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference
    private Piece piece;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 32)
    private PieceStage stage;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    // نام کاربری که این تغییر وضعیت را ثبت کرده (برای رکوردهای قدیمی‌تر از این ویژگی، مقدارش null است)
    @Column(name = "changed_by", updatable = false)
    private String changedBy;

    @PrePersist
    protected void onCreate() {
        this.changedAt = LocalDateTime.now();
    }
}

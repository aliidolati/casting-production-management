package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * تاریخ انجام فرآیند به‌ازای هر زیرمرحله (رادیوگرافی، عملیات حرارتی، ماشین‌کاری خشن) برای هر قطعه.
 */
@Entity
@Table(
        name = "piece_sub_stage_process_dates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_piece_sub_stage_process_date",
                columnNames = {"piece_id", "kind", "sub_stage"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class PieceSubStageProcessDate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    private Piece piece;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32, columnDefinition = "varchar(32)")
    private SubStageKind kind;

    @Column(name = "sub_stage", nullable = false, length = 64)
    private String subStage;

    @Column(name = "process_date", nullable = false)
    private LocalDate processDate;
}

package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "piece_stage_process_dates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_piece_stage_process_date",
                columnNames = {"piece_id", "stage"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class PieceStageProcessDate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    private Piece piece;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 32)
    private PieceStage stage;

    @Column(name = "process_date", nullable = false)
    private LocalDate processDate;
}

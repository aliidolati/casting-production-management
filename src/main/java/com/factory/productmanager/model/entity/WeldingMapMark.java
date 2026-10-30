package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * یک نقطه جوش ثبت‌شده روی نقشه دو‌بعدی قطعه‌ی خاص.
 * مختصات نسبی‌اند (۰ تا ۱) تا با تغییر اندازه نمایش جابه‌جا نشوند.
 */
@Entity
@Table(name = "welding_map_marks")
@Getter
@Setter
@NoArgsConstructor
public class WeldingMapMark {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference
    private Piece piece;

    @Column(name = "x_percent", nullable = false)
    private Double xPercent;

    @Column(name = "y_percent", nullable = false)
    private Double yPercent;

    @Column(name = "defect_type", nullable = false)
    private String defectType;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

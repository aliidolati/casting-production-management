package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * یک نوبت ارسال قطعه به ایستگاه جوشکاری. جوشکاری مرحله‌ی خط تولید نیست؛
 * بعضی قطعات به‌خاطر عیب به این ایستگاه می‌روند و بعد از اتمام کار
 * به شوت، تفسیر، یا (اگر هنوز به شوت نرسیده باشند) همان مرحلهٔ قبل برمی‌گردند.
 */
@Entity
@Table(name = "piece_welding_dispatches")
@Getter
@Setter
@NoArgsConstructor
public class PieceWeldingDispatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference
    private Piece piece;

    @Column(name = "defect_type", nullable = false, length = 255)
    private String defectType;

    @Column(name = "defect_type_custom")
    private String defectTypeCustom;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "sent_by", updatable = false)
    private String sentBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by")
    private String completedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_stage", length = 32)
    private PieceStage originStage;

    @Convert(converter = RadiographySubStageConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "origin_radiography_sub_stage", length = 32)
    private RadiographySubStage originRadiographySubStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_heat_treatment_sub_stage", length = 32)
    private HeatTreatmentSubStage originHeatTreatmentSubStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_rough_machining_sub_stage", length = 32)
    private RoughMachiningSubStage originRoughMachiningSubStage;

    @ManyToMany
    @JoinTable(
            name = "piece_welding_dispatch_defects",
            joinColumns = @JoinColumn(name = "dispatch_id"),
            inverseJoinColumns = @JoinColumn(name = "defect_id")
    )
    private Set<RadiographyDefect> defects = new LinkedHashSet<>();

    @PrePersist
    protected void onCreate() {
        if (this.sentAt == null) {
            this.sentAt = LocalDateTime.now();
        }
    }

    @Column(name="origin_process_date") private java.time.LocalDate originProcessDate;
    @Column(name="dispatch_piece_version") private Long dispatchPieceVersion;
    @Column(name="cancelled_at") private LocalDateTime cancelledAt;
    @Column(name="cancelled_by") private String cancelledBy;
    @Column(name="cancellation_reason",length=2000) private String cancellationReason;

    public boolean isOpen() {
        return completedAt == null;
    }
}

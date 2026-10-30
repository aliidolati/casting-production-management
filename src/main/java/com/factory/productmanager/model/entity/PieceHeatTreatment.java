package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * حضور یک قطعه‌ی مشخص در یک محموله‌ی کوره؛ یعنی «این عملیات حرارتی روی این قطعه انجام شد».
 *
 * این جدول مستقل از piece_sub_stage_history است: آن جدول می‌گوید قطعه کِی بین
 * زیرمرحله‌ها جابه‌جا شده، این جدول می‌گوید واقعاً چه عملیاتی روی قطعه اجرا شده.
 */
@Entity
@Table(
        name = "piece_heat_treatments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_piece_heat_treatment",
                columnNames = {"batch_id", "piece_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class PieceHeatTreatment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference
    private Piece piece;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private HeatTreatmentBatch batch;

    /**
     * اگر این عملیات، اجرای تجویزِ یک تعیین‌تکلیف کشش باشد، به آن وصل می‌شود.
     * خالی یعنی عملیات عادی مسیر تولید، نه عملیات مجدد تجویزشده.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tensile_disposition_id")
    private TensileTestDisposition tensileDisposition;

    /**
     * چندمین بار است که روی همین قطعه عملیات حرارتی انجام می‌شود (مرحله اول،
     * دوم و ...). مخصوص قطعه است نه محموله؛ دو قطعه‌ی یک محموله می‌توانند در
     * مرحله‌های متفاوتی باشند. با PieceStage (مرحله‌ی قطعه در خط تولید) بی‌ربط است.
     */
    @Column(name = "treatment_stage", length = 50)
    private String treatmentStage;

    /** شماره تست پلیتی که همراه این قطعه عملیات شده، مثلاً «C4» */
    @Column(name = "test_plate_label", length = 20)
    private String testPlateLabel;

    /** سختی تست پلیت بر حسب HB، اندازه‌گیری‌شده پس از عملیات */
    @Column(name = "test_plate_hardness_hb", precision = 10, scale = 2)
    private BigDecimal testPlateHardnessHb;

    /** توضیحات آزاد اختیاری */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

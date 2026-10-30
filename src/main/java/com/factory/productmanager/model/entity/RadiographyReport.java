package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * گزارش رادیوگرافی یک قطعه‌ی مشخص بر اساس یکی از قالب‌های ردیف آن قطعه.
 * هر قطعه به‌ازای هر قالب فقط یک گزارش دارد؛ ثبت مجدد، همان گزارش را ویرایش می‌کند.
 */
@Entity
@Table(
        name = "radiography_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rg_report_piece_template",
                columnNames = {"piece_id", "template_id"}
        )
)
@Getter
@Setter
public class RadiographyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    private Piece piece;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private RadiographyReportTemplate template;

    /** تاریخ آزمون که اپراتور ثبت می‌کند */
    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "report_number", length = 80)
    private String reportNumber;

    @Column(name = "request_number", length = 80)
    private String requestNumber;

    /** اسنپ‌شات نام قطعه در زمان ایجاد گزارش */
    @Column(name = "part_name", length = 255)
    private String partName;

    /** اسنپ‌شات کد پروژه در زمان ایجاد گزارش */
    @Column(name = "project_code", length = 255)
    private String projectCode;

    @Column(name = "reference_standard", length = 255)
    private String referenceStandard;

    /**
     * DRAFT ساختار اولیه است، IN_INTERPRETATION یعنی تفسیر شروع شده،
     * COMPLETED یعنی گزارش برای صدور تکمیل شده است.
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RadiographyReportLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null || this.status.isBlank()) {
            this.status = "DRAFT";
        }
    }
}

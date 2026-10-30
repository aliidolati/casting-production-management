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

/**
 * نسخه‌ی تغییرناپذیر (snapshot) از همه‌ی گزارش‌های رادیوگرافی یک قطعه در یک لحظه‌ی گردش‌کار.
 * گزارش‌های زنده (RadiographyReport) دست‌نخورده می‌مانند؛ نسخه‌ها فقط اضافه می‌شوند.
 * SHA-256 محتوا برای اثبات عدم دستکاری ذخیره می‌شود.
 *
 * <p>پروژه کتابخانه‌ی PDF سمت سرور ندارد؛ «فایل نهایی» همین snapshot است که همراه تأییدها و امضاها
 * در مرورگر به شکل برگه‌ی قابل چاپ/ذخیره‌ی PDF نمایش داده می‌شود.
 */
@Entity
@Table(
        name = "radiography_report_versions",
        uniqueConstraints = @UniqueConstraint(name = "uk_rg_version_piece_no", columnNames = {"piece_id", "version_no"}),
        indexes = @Index(name = "idx_rg_version_workflow", columnList = "workflow_id")
)
@Getter
@Setter
@NoArgsConstructor
public class RadiographyReportVersion {

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

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 32, columnDefinition = "varchar(32)")
    private RadiographyReportVersionKind kind;

    /** JSON لیست RadiographyReportResponse در لحظه‌ی ثبت نسخه */
    @Column(nullable = false, updatable = false, length = 8_000_000)
    private String snapshot;

    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;

    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

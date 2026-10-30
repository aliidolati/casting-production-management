package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * یک کورس از قالب گزارش رادیوگرافی.
 * LINE NO در فرم همان courseCode است (C1، C2، ...).
 * این مقادیر هنگام تعریف ردیف قطعه ثبت می‌شوند و بعد از ساخت قالب عوض نمی‌شوند.
 * شماره ردیف NO فقط در UI ساخته می‌شود و اینجا ذخیره نمی‌شود.
 */
@Entity
@Table(
        name = "radiography_report_template_lines",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rg_template_line_no",
                columnNames = {"template_id", "course_code"}
        )
)
@Getter
@Setter
public class RadiographyReportTemplateLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private RadiographyReportTemplate template;

    /** ترتیب نمایش در جدول؛ مستقل از ستون NO که در دیتابیس ذخیره نمی‌شود */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** ستون LINE NO؛ مثلاً C1 */
    @Column(name = "course_code", nullable = false, length = 50)
    private String courseCode;

    /** ستون Number / image؛ تعداد تصاویر، نه شناسه تصویر */
    @Column(name = "image_count", length = 50)
    private String imageCount;

    /** ستون IQI NO. زیرخانه‌ی ABS */
    @Column(name = "iqi_abs", length = 50)
    private String iqiAbs;

    /** ستون A.C.C Criteria */
    @Column(name = "acc_criteria", length = 50)
    private String accCriteria;

    /** ستون TH */
    @Column(name = "th", length = 50)
    private String th;
}

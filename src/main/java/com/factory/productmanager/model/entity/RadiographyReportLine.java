package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * مقادیر تفسیر یک کورس، که هنگام صدور/تکمیل گزارش پر می‌شوند.
 * هندسه کورس روی قالب است. عیوب این کورس در radiography_defects با report_line_id وصل می‌شوند.
 */
@Entity
@Table(
        name = "radiography_report_lines",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rg_report_line",
                columnNames = {"report_id", "template_line_id"}
        )
)
@Getter
@Setter
public class RadiographyReportLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private RadiographyReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_line_id", nullable = false)
    private RadiographyReportTemplateLine templateLine;

    /** ستون IQI NO. زیرخانه‌ی REAL؛ هنگام تفسیر وارد می‌شود و می‌تواند خالی بماند */
    @Column(name = "iqi_real", length = 50)
    private String iqiReal;

    /**
     * ستون RESULT؛ فقط یکی از RS / RT / RP / RJ / A.C.C.
     * خالی یعنی هنوز نتیجه‌ای انتخاب نشده است.
     */
    @Column(name = "result", length = 20)
    private String result;
}

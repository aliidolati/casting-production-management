package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * قالب گزارش رادیوگرافی یک «ردیف قطعه».
 * هندسه‌ی آزمون (کورس‌ها، ضخامت هر بخش، IQI انتظاری) بین همه‌ی قطعات آن ردیف
 * مشترک است، پس یک بار اینجا تعریف می‌شود و هر قطعه فقط مقادیر واقعی خودش را پر می‌کند.
 * یک ردیف می‌تواند چند قالب با نام‌های متفاوت داشته باشد.
 */
@Entity
@Table(
        name = "radiography_report_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rg_template_name",
                columnNames = {"piece_definition_id", "name"}
        )
)
@Getter
@Setter
public class RadiographyReportTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_definition_id", nullable = false)
    private PieceDefinition pieceDefinition;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<RadiographyReportTemplateLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

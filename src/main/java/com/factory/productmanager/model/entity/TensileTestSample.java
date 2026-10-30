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
 * یک نمونه از یک تست کشش. فعلاً فرم ثبت دقیقاً دو نمونه می‌گیرد، ولی جدول
 * جداگانه است تا تغییر تعداد نمونه در آینده نیاز به تغییر ساختار نداشته باشد.
 */
@Entity
@Table(
        name = "tensile_test_samples",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tensile_sample_index",
                columnNames = {"tensile_test_id", "sample_index"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class TensileTestSample {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tensile_test_id", nullable = false)
    @JsonBackReference
    private TensileTest tensileTest;

    /** شماره ترتیبی نمونه داخل تست؛ از ۱ شروع می‌شود */
    @Column(name = "sample_index", nullable = false)
    private Integer sampleIndex;

    /** شماره نمونه‌ای که اپراتور وارد می‌کند، مثلاً "C4" */
    @Column(name = "sample_label", length = 20)
    private String sampleLabel;

    @Column(name = "uts", precision = 10, scale = 2)
    private BigDecimal uts;

    @Column(name = "ys", precision = 10, scale = 2)
    private BigDecimal ys;

    @Column(name = "el", precision = 10, scale = 2)
    private BigDecimal el;

    @Column(name = "hb", precision = 10, scale = 2)
    private BigDecimal hb;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

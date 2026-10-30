package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * جدول تعیین‌تکلیف روی کل تست کشش (نه روی هر نمونه). هر تست دقیقاً یک رکورد
 * تعیین‌تکلیف دارد؛ ویرایش مجدد همین رکورد را بازنویسی می‌کند.
 * فیلدهای تعداد/مدت/دما پارامترهای عملیات حرارتی مجددی هستند که مسئول تجویز می‌کند.
 */
@Entity
@Table(
        name = "tensile_test_dispositions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tensile_disposition_test",
                columnNames = {"tensile_test_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class TensileTestDisposition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tensile_test_id", nullable = false)
    @JsonBackReference
    private TensileTest tensileTest;

    @Column(name = "disposition_date", nullable = false)
    private LocalDate dispositionDate;

    /** تعداد قطعه‌ای که عملیات مجدد روی آن انجام می‌شود */
    @Column(name = "quantity")
    private Integer quantity;

    /** مدت زمان کوره بر حسب ساعت */
    @Column(name = "duration_hours", precision = 6, scale = 2)
    private BigDecimal durationHours;

    /** دمای کوره بر حسب درجه سانتی‌گراد */
    @Column(name = "temperature_c", precision = 7, scale = 2)
    private BigDecimal temperatureC;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_stage", nullable = false, length = 32)
    private TensileNextStage nextStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_result", nullable = false, length = 16)
    private TensileInspectionResult inspectionResult;

    /**
     * مسئولی که تعیین‌تکلیف به نام او ثبت شده. در تاییدهای خودکار خالی می‌ماند و
     * به‌جای آن systemGenerated روشن می‌شود.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_user_id")
    private User responsibleUser;

    /** تعیین‌تکلیف بدون دخالت کاربر و صرفاً از روی آستانه‌ها ثبت شده است */
    @Column(name = "system_generated", nullable = false)
    private boolean systemGenerated;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

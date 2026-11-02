package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * یک محموله‌ی کوره: چند قطعه که با هم یک سیکل عملیات حرارتی را می‌گذرانند.
 * تاریخ، دما، زمان و سایز کوره خصوصیت مشترک کل سیکل‌اند و اینجا ذخیره می‌شوند؛
 * داده‌های مخصوص هر قطعه (تست پلیت) در PieceHeatTreatment می‌نشینند.
 */
@Entity
@Table(
        name = "heat_treatment_batches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_heat_treatment_batch_code",
                columnNames = {"batch_code"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class HeatTreatmentBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Version @Column(nullable=false,columnDefinition="bigint not null default 0")
    private long version;

    /** شناسه‌ی محموله که کاربر وارد می‌کند، مثلاً «HT-1404-012» */
    @Column(name = "batch_code", nullable = false, length = 50)
    private String batchCode;

    /** تاریخی که اپراتور برای انجام عملیات ثبت می‌کند؛ مبنای گزارش‌های بازه‌ای */
    @Column(name = "treatment_date", nullable = false)
    private LocalDate treatmentDate;

    /**
     * دمای اجراشده بر حسب سانتی‌گراد. هنگام ثبت با مقدار تجویزشده در تعیین‌تکلیف
     * کشش پیش‌پر می‌شود ولی مستقل از آن ذخیره می‌ماند.
     */
    @Column(name = "temperature_c", precision = 7, scale = 2)
    private BigDecimal temperatureC;

    /** مدت اجراشده بر حسب ساعت؛ مثل دما از تجویز کشش پیش‌پر می‌شود */
    @Column(name = "duration_hours", precision = 6, scale = 2)
    private BigDecimal durationHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "furnace_size", length = 32)
    private FurnaceSize furnaceSize;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

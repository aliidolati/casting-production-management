package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * یک یادداشت زمان‌دار که کاربر برای یک قطعه‌ی خاص ثبت می‌کند.
 * برخلاف فیلد قدیمی description (که فقط یک متن بود و با هر ذخیره جایگزین می‌شد)،
 * هر قطعه می‌تواند چند یادداشت جدا با تاریخ و نویسنده‌ی مشخص داشته باشد.
 */
@Entity
@Table(name = "piece_notes")
@Getter
@Setter
public class PieceNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference
    private Piece piece;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // نام قابل‌نمایش کاربری که این یادداشت را ثبت کرده (برای نمایش در UI)
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    // نام کاربری واقعی نویسنده (برای تشخیص مالکیت هنگام حذف)؛ برای یادداشت‌های مهاجرت‌شده از توضیحات قدیمی خالی است
    @Column(name = "created_by_username", updatable = false)
    private String createdByUsername;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

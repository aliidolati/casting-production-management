package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * یک تست کشش روی یک قطعه. هر قطعه می‌تواند چند تست داشته باشد و هر تست
 * از چند نمونه تشکیل می‌شود که با هم تعیین‌تکلیف می‌شوند.
 */
@Entity
@Table(
        name = "tensile_tests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tensile_piece_index",
                columnNames = {"piece_id", "test_index"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class TensileTest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference
    private Piece piece;

    /** شماره ترتیبی تست برای همان قطعه؛ از ۱ شروع می‌شود */
    @Column(name = "test_index", nullable = false)
    private Integer testIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TensileTestStatus status;

    /** هنگام ثبت تست به‌صورت خودکار محاسبه می‌شود: آیا مقداری زیر آستانه‌ی تعریف قطعه هست؟ */
    @Column(name = "below_threshold", nullable = false)
    private boolean belowThreshold = false;

    /** فهرست پارامترهای مردود جداشده با کاما، مثلاً "UTS,EL"؛ برای گزارش‌های تجمیعی */
    @Column(name = "threshold_failures", length = 64)
    private String thresholdFailures;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    /** زمان ثبت تعیین‌تکلیف؛ تا وقتی مسئول بررسی نکرده null است */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @OneToMany(mappedBy = "tensileTest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sampleIndex ASC")
    @JsonManagedReference
    private List<TensileTestSample> samples = new ArrayList<>();

    @OneToOne(mappedBy = "tensileTest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private TensileTestDisposition disposition;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = TensileTestStatus.PENDING_REVIEW;
        }
    }
}

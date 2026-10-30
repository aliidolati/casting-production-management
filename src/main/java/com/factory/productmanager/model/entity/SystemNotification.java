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
 * یک رویداد سیستمی در صندوق اعلان‌ها. دو نوع دارد:
 * <ul>
 *   <li>همگانی (recipient = null): مثل تایید خودکار کشش که برای همهٔ دارندگان دسترسی تعیین‌تکلیف کشش مشترک است.</li>
 *   <li>شخصی (recipient != null): مثل اعلان‌های وظایف که فقط برای همان کاربر دیده می‌شود.</li>
 * </ul>
 * متن و مشخصات در لحظهٔ وقوع ذخیره می‌شوند تا اگر موجودیت اصلی بعداً عوض شد
 * پیام اعلان همان بماند. وضعیت خوانده‌شدن به‌ازای هر کاربر در SystemNotificationRead است.
 */
@Entity
@Table(
        name = "system_notifications",
        indexes = {
                @Index(name = "idx_system_notifications_recipient_created", columnList = "recipient_id, created_at"),
                @Index(name = "idx_system_notifications_task", columnList = "task_id"),
                @Index(name = "idx_system_notifications_dedupe", columnList = "recipient_id, dedupe_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SystemNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // رشته (نه ENUM دیتابیس) تا افزودن نوع جدید اعلان نیاز به تغییر ستون نداشته باشد
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 64, columnDefinition = "varchar(64)")
    private NotificationType type;

    @Column(nullable = false, length = 512)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id")
    private Piece piece;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tensile_test_id", unique = true)
    private TensileTest tensileTest;

    /** فقط برای اعلان‌های کشش پر می‌شود */
    @Column(name = "piece_name")
    private String pieceName;

    /** فقط برای اعلان‌های کشش پر می‌شود */
    @Column(name = "piece_number")
    private Integer pieceNumber;

    @Column(name = "project_code")
    private String projectCode;

    /** گیرنده‌ی اعلان شخصی؛ null یعنی اعلان همگانی */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User recipient;

    /** وظیفه‌ی مرتبط (برای اعلان‌های وظایف) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Task task;

    /** کلید جلوگیری از اعلان تکراری (مثلاً یادآوری «امروز مهلت دارد» در یک روز)؛ با recipient یکتاست */
    @Column(name = "dedupe_key", length = 128)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}

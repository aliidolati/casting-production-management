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
 * یک دور گردش‌کار تفسیر رادیوگرافی برای یک قطعه. این موجودیت «ماشین حالت موازی» برای مرحله‌ی قطعه نیست؛
 * مرحله‌ی قطعه همچنان در Piece (RADIOGRAPHY + زیرمرحله‌ی INTERPRETATION) نگه‌داری می‌شود و
 * این‌جا فقط مسئول تفسیر، وظایف مرتبط، وضعیت بازبینی و رد/تأیید ثبت می‌شود.
 *
 * <p>برای هر قطعه حداکثر یک گردش‌کار «باز» وجود دارد. این قانون در سطح دیتابیس هم اعمال می‌شود:
 * {@code openKey} تا وقتی گردش‌کار باز است برابر شناسه‌ی قطعه است و با بسته‌شدن null می‌شود؛
 * ستون یکتا اجازه‌ی دو ردیف باز برای یک قطعه را نمی‌دهد (چند NULL مجازند).
 */
@Entity
@Table(
        name = "radiography_workflows",
        uniqueConstraints = @UniqueConstraint(name = "uk_rg_workflow_open_piece", columnNames = "open_key"),
        indexes = {
                @Index(name = "idx_rg_workflow_piece", columnList = "piece_id"),
                @Index(name = "idx_rg_workflow_interpreter", columnList = "interpreter_id"),
                @Index(name = "idx_rg_workflow_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class RadiographyWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "piece_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Piece piece;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private RadiographyWorkflowStatus status = RadiographyWorkflowStatus.INTERPRETER_ASSIGNED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interpreter_id", nullable = false)
    private User interpreter;

    /** مدیری که مفسر را تخصیص داده؛ وظیفه‌ی بررسی نهایی پیش‌فرض برای او ساخته می‌شود */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_id", nullable = false)
    private User assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interpreter_task_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Task interpreterTask;

    /** وظیفه‌ی بررسی مدیر برای دور جاری؛ بعد از تأیید یا رد، بسته می‌شود ولی ارجاع برای تاریخچه می‌ماند */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_task_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Task managerTask;

    /** شماره‌ی دور تفسیر؛ با هر رد مدیر یکی زیاد می‌شود */
    @Column(nullable = false)
    private int round = 1;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejected_by", length = 255)
    private String rejectedBy;

    /** برابر شناسه‌ی قطعه تا وقتی گردش‌کار باز است؛ پس از پایان null */
    @Column(name = "open_key", length = 36)
    private String openKey;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }
}

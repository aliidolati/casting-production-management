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
 * ثبت تغییرناپذیر یک تأیید (یا ردِ) مفسر/مدیر. هویت تأییدکننده، نقش و زمان در همان لحظه کپی می‌شوند
 * (نه ارجاع زنده به کاربر) و تصویر امضا از پروفایل «همان کاربر احراز هویت‌شده» برداشته و به‌صورت
 * فایل جدا برای همین تأیید نگه‌داری می‌شود؛ پس تغییر بعدی امضای پروفایل، تأییدهای قبلی را عوض نمی‌کند.
 * هیچ مسیری برای ویرایش یا حذف رکورد وجود ندارد.
 */
@Entity
@Table(
        name = "radiography_workflow_approvals",
        indexes = {
                @Index(name = "idx_rg_approval_workflow", columnList = "workflow_id"),
                @Index(name = "idx_rg_approval_piece", columnList = "piece_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class RadiographyWorkflowApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RadiographyWorkflow workflow;

    /** برای ممیزی: حتی اگر قطعه/گردش‌کار بعداً تغییر کند، این شناسه‌ها همان لحظه را نشان می‌دهند */
    @Column(name = "piece_id", nullable = false, updatable = false)
    private UUID pieceId;

    /** فقط یک بار (null → شناسه‌ی نسخه) توسط سرویس گردش‌کار در همان تراکنش تأیید مقداردهی می‌شود */
    @Column(name = "report_version_id")
    private UUID reportVersionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "approval_type", nullable = false, updatable = false, length = 16, columnDefinition = "varchar(16)")
    private RadiographyApprovalType type;

    /** APPROVED یا REJECTED */
    @Column(nullable = false, updatable = false, length = 16)
    private String decision;

    @Column(nullable = false, updatable = false)
    private int round;

    @Column(name = "approver_user_id", nullable = false, updatable = false)
    private UUID approverUserId;

    @Column(name = "approver_username", nullable = false, updatable = false, length = 255)
    private String approverUsername;

    @Column(name = "approver_name", nullable = false, updatable = false, length = 255)
    private String approverName;

    /** نقش سیستمی کاربر در لحظه‌ی تأیید (ADMIN / OPERATOR) */
    @Column(name = "approver_role", nullable = false, updatable = false, length = 32)
    private String approverRole;

    @Column(name = "approved_at", nullable = false, updatable = false)
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    /** مسیر نسبی کپی امضا؛ فقط برای تأییدها (نه رد) */
    @Column(name = "signature_path", length = 255, updatable = false)
    private String signaturePath;

    @Column(name = "signature_content_type", length = 64, updatable = false)
    private String signatureContentType;
}

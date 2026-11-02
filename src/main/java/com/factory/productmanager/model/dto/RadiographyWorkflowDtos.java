package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.RadiographyApprovalType;
import com.factory.productmanager.model.entity.RadiographyReportVersionKind;
import com.factory.productmanager.model.entity.RadiographyWorkflowAction;
import com.factory.productmanager.model.entity.RadiographyWorkflowStatus;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** قراردادهای API گردش‌کار تفسیر رادیوگرافی. Entity هرگز مستقیم بیرون داده نمی‌شود. */
public final class RadiographyWorkflowDtos {

    private RadiographyWorkflowDtos() {
    }

    // ------------------------------------------------------------ requests

    public record AssignRequest(@NotNull(message = "مفسر انتخاب نشده است") UUID interpreterId) {
    }

    /** تأیید صریح کاربر (پنجره‌ی تأیید در UI)؛ سرور بدون confirmed=true هیچ تأییدی ثبت نمی‌کند */
    public record ConfirmRequest(boolean confirmed) {
    }

    public record ReasonRequest(String reason) {
    }

    // ------------------------------------------------------------ responses

    public record ApprovalResponse(
            UUID id,
            RadiographyApprovalType type,
            String decision,
            int round,
            UUID approverUserId,
            String approverName,
            String approverUsername,
            String approverRole,
            LocalDateTime approvedAt,
            boolean hasSignature,
            String reason,
            UUID reportVersionId
    ) {
    }

    public record VersionResponse(
            UUID id,
            int versionNo,
            RadiographyReportVersionKind kind,
            String contentHash,
            String createdBy,
            LocalDateTime createdAt
    ) {
    }

    public record EventResponse(
            UUID id,
            RadiographyWorkflowAction action,
            UUID actorUserId,
            String actorName,
            String oldValue,
            String newValue,
            String reason,
            LocalDateTime createdAt
    ) {
    }

    /** state: DONE / CURRENT / PENDING */
    public record StepResponse(String key, String label, String state) {
    }

    public record WorkflowPermissions(
            boolean canEditReport,
            boolean canIssue,
            boolean canReview,
            boolean canReassign,
            boolean canCancel,
            boolean viewerHasSignature,
            boolean canApprove
    ) {
    }

    public record WorkflowResponse(
            UUID id,
            UUID pieceId,
            String partLabel,
            String projectCode,
            RadiographyWorkflowStatus status,
            boolean open,
            int round,
            UserRefResponse interpreter,
            UserRefResponse assignedBy,
            LocalDateTime assignedAt,
            UUID interpreterTaskId,
            UUID managerTaskId,
            String rejectionReason,
            LocalDateTime rejectedAt,
            String rejectedBy,
            LocalDateTime completedAt,
            ApprovalResponse interpreterApproval,
            ApprovalResponse managerApproval,
            List<ApprovalResponse> approvals,
            List<VersionResponse> versions,
            List<EventResponse> events,
            List<StepResponse> steps,
            WorkflowPermissions permissions
    ) {
    }

    /** وضعیت گردش‌کار یک قطعه برای صفحه‌ی قطعه؛ workflow ممکن است null باشد (هیچ‌وقت شروع نشده) */
    public record PieceStateResponse(
            UUID pieceId,
            boolean inInterpretation,
            boolean canAssign,
            WorkflowResponse workflow
    ) {
    }

    public record QueueItemResponse(
            UUID pieceId,
            Integer pieceNumber,
            String pieceName,
            String projectCode,
            String partLabel,
            UUID workflowId,
            RadiographyWorkflowStatus workflowStatus,
            UserRefResponse interpreter
    ) {
    }

    public record DocumentApprovalResponse(
            RadiographyApprovalType type,
            String decision,
            String approverName,
            String approverUsername,
            String approverRole,
            LocalDateTime approvedAt,
            String signatureDataUrl
    ) {
    }

    /** نسخه‌ی تغییرناپذیر گزارش به‌همراه تأییدها و امضاها؛ مبنای نمایش و چاپ مهر */
    public record DocumentResponse(
            UUID workflowId,
            UUID versionId,
            int versionNo,
            RadiographyReportVersionKind kind,
            String contentHash,
            LocalDateTime createdAt,
            String createdBy,
            UUID pieceId,
            Integer pieceNumber,
            String partLabel,
            String projectCode,
            JsonNode reports,
            List<DocumentApprovalResponse> approvals
    ) {
    }

    public record SignatureStatusResponse(boolean present, String dataUrl, LocalDateTime updatedAt) {
    }
}

package com.factory.productmanager.service;

import com.factory.productmanager.exception.ForbiddenOperationException;
import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.RadiographyReportLineResponse;
import com.factory.productmanager.model.dto.RadiographyReportResponse;
import com.factory.productmanager.model.dto.RadiographyWorkflowDtos.*;
import com.factory.productmanager.model.dto.PageResponse;
import com.factory.productmanager.model.dto.UserRefResponse;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * گردش‌کار تفسیر و تأیید گزارش رادیوگرافی. تمام قوانین کسب‌وکار این‌جاست (نه در UI):
 *
 * <ol>
 *   <li>مدیر مفسر مجاز را به قطعه‌ای که در «تفسیر رادیوگرافی» است تخصیص می‌دهد → وظیفه‌ی
 *       RADIOGRAPHY_INTERPRETATION برای مفسر ساخته می‌شود؛</li>
 *   <li>مفسر گزارش را کامل و «صدور و تأیید» می‌کند → امضا از پروفایل خودش برداشته می‌شود، وظیفه‌ی مفسر
 *       تکمیل و در همان تراکنش وظیفه‌ی RADIOGRAPHY_MANAGER_APPROVAL برای مدیر ساخته می‌شود؛</li>
 *   <li>مدیر تأیید می‌کند (ثبت تأیید → نهایی‌سازی گزارش → انتقال قطعه به VT → تکمیل وظیفه → ممیزی → اعلان،
 *       همه در یک تراکنش) یا با ذکر دلیل رد می‌کند (وظیفه‌ی مفسر بازگشایی و قطعه در تفسیر می‌ماند).</li>
 * </ol>
 *
 * هر متدِ تغییردهنده ابتدا ردیف گردش‌کار را قفل می‌کند؛ پس دو درخواست هم‌زمان (مثلاً دو بار «تأیید») پشت سر هم
 * اجرا می‌شوند و دومی با «وضعیت نامعتبر» رد می‌شود. شناسه‌ی گزارش/قطعه هرگز از کلاینت پذیرفته نمی‌شود؛
 * همیشه از خود گردش‌کار استخراج می‌شود.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RadiographyWorkflowService {

    static final String WORKFLOW_NOT_FOUND = "گردش‌کار رادیوگرافی پیدا نشد";
    private static final String NEXT_STAGE_LABEL = "VT";
    private static final int MAX_REASON = 2000;

    private final RadiographyWorkflowRepository workflowRepository;
    private final RadiographyWorkflowApprovalRepository approvalRepository;
    private final RadiographyReportVersionRepository versionRepository;
    private final RadiographyWorkflowEventRepository eventRepository;
    private final RadiographyReportRepository reportRepository;
    private final RadiographyReportService reportService;
    private final PieceService pieceService;
    private final UserRepository userRepository;
    private final TaskAccessPolicy policy;
    private final WorkflowTaskOperations taskOperations;
    private final RadiographyWorkflowEventRecorder eventRecorder;
    private final TaskNotificationService notifications;
    private final SignatureStorageService signatureStorage;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // =====================================================================
    // 1) تخصیص مفسر
    // =====================================================================

    public WorkflowResponse assignInterpreter(UUID pieceId, UUID interpreterId, String username) {
        User actor = actor(username);
        requireReviewer(actor);

        Piece piece = pieceService.findPieceOrThrow(pieceId);
        if (piece.getArchivedAt() != null) {
            throw new IllegalStateException("قطعه بایگانی‌شده قابل تخصیص مفسر نیست");
        }
        if (!isInInterpretation(piece)) {
            throw new IllegalStateException("مفسر فقط برای قطعه‌ای تخصیص داده می‌شود که در مرحله‌ی «تفسیر رادیوگرافی» است");
        }
        if (workflowRepository.existsByOpenKey(pieceId.toString())) {
            throw new IllegalStateException("برای این قطعه قبلاً مفسر تخصیص داده شده و گردش‌کار تفسیر باز است");
        }
        User interpreter = resolveInterpreter(interpreterId);

        OperationClock op = new OperationClock(clock);
        RadiographyWorkflow workflow = new RadiographyWorkflow();
        workflow.setPiece(piece);
        workflow.setStatus(RadiographyWorkflowStatus.INTERPRETER_ASSIGNED);
        workflow.setInterpreter(interpreter);
        workflow.setAssignedBy(actor);
        workflow.setAssignedAt(op.base());
        workflow.setRound(1);
        workflow.setOpenKey(pieceId.toString());
        workflow.setCreatedAt(op.base());
        workflow.setUpdatedAt(op.base());
        try {
            // flush تا قید یکتای open_key هم‌زمانی دو تخصیص را همین‌جا رد کند
            workflow = workflowRepository.saveAndFlush(workflow);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("برای این قطعه قبلاً مفسر تخصیص داده شده و گردش‌کار تفسیر باز است");
        }

        String label = partLabel(piece);
        Task task = taskOperations.create(
                TaskType.RADIOGRAPHY_INTERPRETATION,
                "تفسیر گزارش رادیوگرافی — " + label,
                "تکمیل، صدور و تأیید گزارش رادیوگرافی قطعه «" + label + "» (پروژه " + projectCode(piece) + ")",
                actor, interpreter, piece, workflow.getId(), op);
        workflow.setInterpreterTask(task);

        createVersion(workflow, RadiographyReportVersionKind.ORIGINAL, snapshotJson(pieceId), actor, op.base());
        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_INTERPRETER_ASSIGNED, actor,
                null, WorkflowTaskOperations.name(interpreter), null, op.tick());

        notifications.sendWorkflow(interpreter, actor, task, NotificationType.RADIOGRAPHY_ASSIGNED,
                "گزارش رادیوگرافی قطعه «" + label + "» برای تفسیر به شما تخصیص داده شد");

        return toResponse(workflowRepository.save(workflow), actor);
    }

    public WorkflowResponse reassignInterpreter(UUID workflowId, UUID interpreterId, String username) {
        User actor = actor(username);
        requireReviewer(actor);
        RadiographyWorkflow workflow = lockOpen(workflowId);
        if (!workflow.getStatus().isInterpreterEditable()) {
            throw new IllegalStateException("پس از تأیید مفسر، تا تصمیم مدیر نمی‌توان مفسر را عوض کرد");
        }
        User next = resolveInterpreter(interpreterId);
        User previous = workflow.getInterpreter();
        if (previous.getId().equals(next.getId())) {
            throw new IllegalArgumentException("همین مفسر از قبل مسئول این گزارش است");
        }

        OperationClock op = new OperationClock(clock);
        workflow.setInterpreter(next);
        workflow.setUpdatedAt(op.base());
        Task task = workflow.getInterpreterTask();
        if (task != null) {
            taskOperations.reassign(task, next, actor, op);
        }
        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_INTERPRETER_REASSIGNED, actor,
                WorkflowTaskOperations.name(previous), WorkflowTaskOperations.name(next), null, op.tick());

        String label = partLabel(workflow.getPiece());
        notifications.sendWorkflow(next, actor, task, NotificationType.RADIOGRAPHY_ASSIGNED,
                "گزارش رادیوگرافی قطعه «" + label + "» برای تفسیر به شما تخصیص داده شد");
        notifications.sendWorkflow(previous, actor, task, NotificationType.TASK_UPDATED,
                "تفسیر گزارش رادیوگرافی قطعه «" + label + "» از شما به " + WorkflowTaskOperations.name(next) + " واگذار شد");
        return toResponse(workflowRepository.save(workflow), actor);
    }

    public WorkflowResponse cancelWorkflow(UUID workflowId, String reason, String username) {
        User actor = actor(username);
        requireReviewer(actor);
        RadiographyWorkflow workflow = lockOpen(workflowId);
        String cleanReason = requireReason(reason);

        OperationClock op = new OperationClock(clock);
        RadiographyWorkflowStatus previous = workflow.getStatus();
        for (Task task : new Task[]{workflow.getInterpreterTask(), workflow.getManagerTask()}) {
            if (task != null) {
                taskOperations.cancel(task, actor, cleanReason, op);
            }
        }
        // گزارش‌ها به وضعیت قابل‌ویرایش برمی‌گردند (قفل «در انتظار مدیر» برداشته می‌شود)
        setReportStatuses(workflow.getPiece().getId(), RadiographyReportService.STATUS_IN_INTERPRETATION, false);

        close(workflow, RadiographyWorkflowStatus.CANCELLED, op.base());
        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_WORKFLOW_CANCELLED, actor,
                previous.name(), RadiographyWorkflowStatus.CANCELLED.name(), cleanReason, op.tick());
        notifications.sendWorkflow(workflow.getInterpreter(), actor, workflow.getInterpreterTask(),
                NotificationType.TASK_UPDATED,
                "گردش‌کار تفسیر رادیوگرافی قطعه «" + partLabel(workflow.getPiece()) + "» لغو شد");
        return toResponse(workflowRepository.save(workflow), actor);
    }

    // =====================================================================
    // 2) صدور و تأیید مفسر
    // =====================================================================

    public WorkflowResponse issueAndApproveByInterpreter(UUID workflowId, boolean confirmed, String username) {
        User actor = actor(username);
        requireConfirmed(confirmed);
        RadiographyWorkflow workflow = lockForUpdate(workflowId);
        if (!workflow.getInterpreter().getId().equals(actor.getId())) {
            // کاربری که مفسر مسئول نیست: بدون افشا (کاربر عادی ۴۰۴، مدیر ۴۰۳)
            if (isReviewer(actor)) {
                throw new ForbiddenOperationException("فقط مفسر مسئول می‌تواند این گزارش را صادر و تأیید کند");
            }
            throw new ResourceNotFoundException(WORKFLOW_NOT_FOUND);
        }
        if (!workflow.getStatus().isInterpreterEditable()) {
            throw new IllegalStateException("این گزارش در وضعیت فعلی قابل صدور نیست (قبلاً تأیید شده یا بسته شده است)");
        }
        if (!canInterpret(actor)) {
            throw new ForbiddenOperationException("شما دیگر اجازه‌ی تفسیر و تأیید گزارش رادیوگرافی را ندارید");
        }
        Piece piece = workflow.getPiece();
        requireStillInInterpretation(piece);
        validateReportsReadyToIssue(piece.getId());
        StoredApprovalSignature signature = requireSignature(actor);

        OperationClock op = new OperationClock(clock);
        int round = workflow.getRound();

        // گزارش‌ها «در انتظار تأیید مدیر» می‌شوند و قفل‌اند
        setReportStatuses(piece.getId(), RadiographyReportService.STATUS_PENDING_MANAGER_APPROVAL, false);

        RadiographyWorkflowApproval approval = newApproval(workflow, RadiographyApprovalType.INTERPRETER,
                "APPROVED", round, actor, null, signature, op.base());
        approval = approvalRepository.save(approval);

        RadiographyReportVersion version = createVersion(workflow, RadiographyReportVersionKind.INTERPRETER_APPROVED,
                snapshotJson(piece.getId()), actor, op.base());
        approval.setReportVersionId(version.getId());

        workflow.setStatus(RadiographyWorkflowStatus.MANAGER_REVIEW);
        workflow.setUpdatedAt(op.base());

        Task interpreterTask = workflow.getInterpreterTask();
        if (interpreterTask != null) {
            taskOperations.complete(interpreterTask, actor, "گزارش توسط مفسر صادر و تأیید شد", op);
        }

        // وظیفه‌ی تأیید مدیر؛ دوباره‌کاری ممنوع: وظیفه‌ی باز قبلی نباید وجود داشته باشد
        Task previousManagerTask = workflow.getManagerTask();
        if (previousManagerTask != null && previousManagerTask.getStatus().isOpen()) {
            throw new IllegalStateException("برای این گزارش از قبل وظیفه‌ی تأیید مدیر باز است");
        }
        User managerAssignee = resolveManagerAssignee(workflow);
        String label = partLabel(piece);
        Task managerTask = taskOperations.create(
                TaskType.RADIOGRAPHY_MANAGER_APPROVAL,
                "بررسی و تأیید نهایی گزارش رادیوگرافی — " + label,
                "گزارش رادیوگرافی قطعه «" + label + "» (پروژه " + projectCode(piece)
                        + ") توسط مفسر تأیید شده و منتظر تأیید نهایی مدیر است",
                actor, managerAssignee, piece, workflow.getId(), op);
        workflow.setManagerTask(managerTask);

        String actorName = WorkflowTaskOperations.name(actor);
        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_REPORT_ISSUED, actor,
                null, "v" + version.getVersionNo(), null, op.tick());
        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_INTERPRETER_APPROVED, actor,
                null, actorName, null, op.tick());
        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_MANAGER_REVIEW_STARTED, actor,
                null, WorkflowTaskOperations.name(managerAssignee), null, op.tick());

        String message = "گزارش رادیوگرافی قطعه «" + label + "» توسط " + actorName
                + " تأیید شد و منتظر تأیید نهایی شماست";
        notifications.sendWorkflow(managerAssignee, actor, managerTask,
                NotificationType.RADIOGRAPHY_READY_FOR_REVIEW, message);
        if (!workflow.getAssignedBy().getId().equals(managerAssignee.getId())) {
            notifications.sendWorkflow(workflow.getAssignedBy(), actor, managerTask,
                    NotificationType.RADIOGRAPHY_READY_FOR_REVIEW, message);
        }
        return toResponse(workflowRepository.save(workflow), actor);
    }

    // =====================================================================
    // 3) تصمیم مدیر
    // =====================================================================

    public WorkflowResponse approveByManager(UUID workflowId, boolean confirmed, String username) {
        User actor = actor(username);
        requireReviewer(actor);
        requireConfirmed(confirmed);
        RadiographyWorkflow workflow = lockForUpdate(workflowId);
        requireManagerReview(workflow);
        Piece piece = workflow.getPiece();
        requireStillInInterpretation(piece);
        StoredApprovalSignature signature = requireSignature(actor);

        OperationClock op = new OperationClock(clock);

        // (1) ثبت تأیید مدیر
        RadiographyWorkflowApproval approval = newApproval(workflow, RadiographyApprovalType.MANAGER,
                "APPROVED", workflow.getRound(), actor, null, signature, op.base());
        approval = approvalRepository.save(approval);

        // (2) نهایی‌سازی گزارش: وضعیت COMPLETED + نسخه‌ی تغییرناپذیر نهایی (اصل + مهر و امضای مفسر و مدیر)
        setReportStatuses(piece.getId(), RadiographyReportService.STATUS_COMPLETED, true);
        RadiographyReportVersion finalVersion = createVersion(workflow, RadiographyReportVersionKind.MANAGER_FINAL,
                snapshotJson(piece.getId()), actor, op.base());
        approval.setReportVersionId(finalVersion.getId());

        // (3) تغییر مرحله‌ی قطعه (فقط بک‌اند): تفسیر → VT
        pieceService.moveInterpretationToVtByWorkflow(piece.getId(), username);

        // (4) تکمیل وظیفه‌ی مدیر (از «فعال» خارج می‌شود، در تاریخچه می‌ماند، هرگز پاک نمی‌شود)
        Task managerTask = workflow.getManagerTask();
        if (managerTask != null) {
            taskOperations.complete(managerTask, actor, "گزارش توسط مدیر تأیید و نهایی شد", op);
        }

        // (5) ممیزی
        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_MANAGER_APPROVED, actor,
                RadiographyWorkflowStatus.MANAGER_REVIEW.name(), RadiographyWorkflowStatus.MANAGER_APPROVED.name(),
                null, op.tick());
        eventRecorder.record(workflow, RadiographyWorkflowAction.PART_STAGE_CHANGED, actor,
                "رادیوگرافی / تفسیر", "رادیوگرافی / " + NEXT_STAGE_LABEL, "Manager approved radiography report", op.tick());

        close(workflow, RadiographyWorkflowStatus.MANAGER_APPROVED, op.base());

        // (6) اعلان‌ها
        String label = partLabel(piece);
        String message = "گزارش رادیوگرافی قطعه «" + label + "» تأیید نهایی شد و قطعه به مرحله‌ی " + NEXT_STAGE_LABEL
                + " منتقل شد";
        notifications.sendWorkflow(workflow.getInterpreter(), actor, workflow.getInterpreterTask(),
                NotificationType.RADIOGRAPHY_APPROVED, message);
        if (!workflow.getAssignedBy().getId().equals(workflow.getInterpreter().getId())) {
            notifications.sendWorkflow(workflow.getAssignedBy(), actor, managerTask,
                    NotificationType.RADIOGRAPHY_APPROVED, message);
        }
        return toResponse(workflowRepository.save(workflow), actor);
    }

    public WorkflowResponse rejectByManager(UUID workflowId, String reason, String username) {
        User actor = actor(username);
        requireReviewer(actor);
        RadiographyWorkflow workflow = lockForUpdate(workflowId);
        requireManagerReview(workflow);
        String cleanReason = requireReason(reason);
        Piece piece = workflow.getPiece();

        OperationClock op = new OperationClock(clock);
        int round = workflow.getRound();

        RadiographyWorkflowApproval rejection = newApproval(workflow, RadiographyApprovalType.MANAGER,
                "REJECTED", round, actor, cleanReason, null, op.base());
        approvalRepository.save(rejection);

        setReportStatuses(piece.getId(), RadiographyReportService.STATUS_REINTERPRETATION_REQUIRED, false);

        workflow.setStatus(RadiographyWorkflowStatus.REINTERPRETATION_REQUIRED);
        workflow.setRound(round + 1);
        workflow.setRejectionReason(cleanReason);
        workflow.setRejectedAt(op.base());
        workflow.setRejectedBy(WorkflowTaskOperations.name(actor));
        workflow.setUpdatedAt(op.base());

        Task managerTask = workflow.getManagerTask();
        if (managerTask != null) {
            taskOperations.complete(managerTask, actor, "گزارش رد شد: " + cleanReason, op);
        }
        Task interpreterTask = workflow.getInterpreterTask();
        if (interpreterTask != null) {
            taskOperations.reopen(interpreterTask, actor, "گزارش توسط مدیر رد شد: " + cleanReason, op);
        }

        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_MANAGER_REJECTED, actor,
                RadiographyWorkflowStatus.MANAGER_REVIEW.name(), RadiographyWorkflowStatus.REINTERPRETATION_REQUIRED.name(),
                cleanReason, op.tick());
        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_REINTERPRETATION_REQUESTED, actor,
                null, WorkflowTaskOperations.name(workflow.getInterpreter()), cleanReason, op.tick());

        String message = "گزارش رادیوگرافی قطعه «" + partLabel(piece) + "» توسط مدیر رد شد. دلیل: " + cleanReason;
        notifications.sendWorkflow(workflow.getInterpreter(), actor, interpreterTask,
                NotificationType.RADIOGRAPHY_REJECTED, message);
        return toResponse(workflowRepository.save(workflow), actor);
    }

    // =====================================================================
    // 4) خواندن
    // =====================================================================

    @Transactional(readOnly = true)
    public WorkflowResponse get(UUID workflowId, String username) {
        User viewer = actor(username);
        RadiographyWorkflow workflow = visibleWorkflow(workflowId, viewer);
        return toResponse(workflow, viewer);
    }

    @Transactional(readOnly = true)
    public PieceStateResponse getPieceState(UUID pieceId, String username) {
        User viewer = actor(username);
        Piece piece = pieceService.findPieceOrThrow(pieceId);
        Optional<RadiographyWorkflow> latest = workflowRepository.findFirstByPieceIdOrderByCreatedAtDesc(pieceId);
        boolean inInterpretation = isInInterpretation(piece) && piece.getArchivedAt() == null;
        boolean open = latest.isPresent() && latest.get().getStatus().isOpen();
        boolean canAssign = inInterpretation && !open && isReviewer(viewer);
        return new PieceStateResponse(pieceId, inInterpretation, canAssign,
                latest.map(w -> toResponse(w, viewer)).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<UserRefResponse> listInterpreters(String username) {
        User viewer = actor(username);
        requireReviewer(viewer);
        return userRepository.findAssignableUsers(Permission.INTERPRET_RADIOGRAPHY);
    }

    /** صف قطعات در «تفسیر رادیوگرافی» برای مدیر: بدون مفسر / در جریان / منتظر تأیید */
    @Transactional(readOnly = true)
    public PageResponse<QueueItemResponse> queue(String search, int page, int size, String username) {
        User viewer = actor(username);
        requireReviewer(viewer);
        int safeSize = Math.max(1, Math.min(size, 100));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        String like = likePattern(search);
        Page<Piece> pieces = workflowRepository.findQueuePieces(RadiographySubStage.INTERPRETATION, like, pageable);

        Map<UUID, RadiographyWorkflow> openByPiece = new HashMap<>();
        if (!pieces.isEmpty()) {
            List<UUID> ids = pieces.getContent().stream().map(Piece::getId).toList();
            for (RadiographyWorkflow w : workflowRepository.findByPieceIdInAndOpenKeyIsNotNull(ids)) {
                openByPiece.put(w.getPiece().getId(), w);
            }
        }
        return PageResponse.from(pieces, piece -> {
            RadiographyWorkflow w = openByPiece.get(piece.getId());
            return new QueueItemResponse(
                    piece.getId(),
                    piece.getPieceNumber(),
                    piece.getPieceDefinition().getPieceName(),
                    projectCode(piece),
                    partLabel(piece),
                    w == null ? null : w.getId(),
                    w == null ? null : w.getStatus(),
                    w == null ? null : userRef(w.getInterpreter()));
        });
    }

    /** نسخه‌ی تغییرناپذیر گزارش + تأییدها و تصویر امضاها (برای نمایش مدیر و چاپ مهر) */
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID workflowId, UUID versionId, String username) {
        User viewer = actor(username);
        RadiographyWorkflow workflow = workflowRepository.findDetailedById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException(WORKFLOW_NOT_FOUND));
        RadiographyReportVersion version = versionRepository.findByIdAndWorkflowId(versionId, workflowId)
                .orElseThrow(() -> new ResourceNotFoundException(WORKFLOW_NOT_FOUND));
        // نسخه‌ی نهایی برای همه‌ی کاربران وارد‌شده (مثل خود گزارش) قابل مشاهده است؛ بقیه‌ی نسخه‌ها فقط برای دست‌اندرکاران
        boolean publicFinal = version.getKind() == RadiographyReportVersionKind.MANAGER_FINAL;
        if (!publicFinal && !canSeeWorkflow(viewer, workflow)) {
            throw new ResourceNotFoundException(WORKFLOW_NOT_FOUND);
        }

        List<RadiographyWorkflowApproval> all = approvalRepository.findByWorkflowIdOrderByApprovedAtAsc(workflowId);
        List<RadiographyWorkflowApproval> shown = new ArrayList<>();
        switch (version.getKind()) {
            case ORIGINAL -> {
            }
            case INTERPRETER_APPROVED -> all.stream()
                    .filter(a -> versionId.equals(a.getReportVersionId()))
                    .findFirst().ifPresent(shown::add);
            case MANAGER_FINAL -> {
                Optional<RadiographyWorkflowApproval> manager = all.stream()
                        .filter(a -> versionId.equals(a.getReportVersionId())).findFirst();
                manager.ifPresent(m -> all.stream()
                        .filter(a -> a.getType() == RadiographyApprovalType.INTERPRETER
                                && "APPROVED".equals(a.getDecision()) && a.getRound() == m.getRound())
                        .reduce((first, second) -> second)
                        .ifPresent(shown::add));
                manager.ifPresent(shown::add);
            }
        }
        List<DocumentApprovalResponse> approvals = shown.stream().map(a -> new DocumentApprovalResponse(
                a.getType(), a.getDecision(), a.getApproverName(), a.getApproverUsername(), a.getApproverRole(),
                a.getApprovedAt(), signatureStorage.loadAsDataUrl(a.getSignaturePath(), a.getSignatureContentType())
        )).toList();

        Piece piece = workflow.getPiece();
        return new DocumentResponse(
                workflowId, version.getId(), version.getVersionNo(), version.getKind(), version.getContentHash(),
                version.getCreatedAt(), version.getCreatedBy(), piece.getId(), piece.getPieceNumber(),
                partLabel(piece), projectCode(piece),
                objectMapper.readTree(version.getSnapshot()), approvals);
    }

    // =====================================================================
    // داخلی: دسترسی
    // =====================================================================

    private User actor(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر پیدا نشد"));
    }

    private boolean isReviewer(User user) {
        return !user.isDisabled() && (policy.isManager(user) || hasPermission(user, Permission.REVIEW_RADIOGRAPHY_REPORT));
    }

    private boolean canInterpret(User user) {
        return !user.isDisabled() && (policy.isManager(user) || hasPermission(user, Permission.INTERPRET_RADIOGRAPHY));
    }

    private static boolean hasPermission(User user, Permission permission) {
        return user.getPermissions() != null && user.getPermissions().contains(permission);
    }

    private void requireReviewer(User actor) {
        if (!isReviewer(actor)) {
            throw new ForbiddenOperationException("شما اجازه‌ی تخصیص مفسر یا تأیید نهایی گزارش رادیوگرافی را ندارید");
        }
    }

    private boolean canSeeWorkflow(User viewer, RadiographyWorkflow workflow) {
        return isReviewer(viewer) || workflow.getInterpreter().getId().equals(viewer.getId());
    }

    /** گردش‌کار برای کسی که حق دیدنش را دارد؛ وگرنه ۴۰۴ یکسان (ضد IDOR) */
    private RadiographyWorkflow visibleWorkflow(UUID workflowId, User viewer) {
        RadiographyWorkflow workflow = workflowRepository.findDetailedById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException(WORKFLOW_NOT_FOUND));
        if (!canSeeWorkflow(viewer, workflow)) {
            throw new ResourceNotFoundException(WORKFLOW_NOT_FOUND);
        }
        return workflow;
    }

    /** تخصیص‌پذیری مفسر؛ پیام عمداً برای همه‌ی دلایل یکی است (عدم افشای وضعیت حساب‌ها) */
    private User resolveInterpreter(UUID interpreterId) {
        return userRepository.findById(interpreterId)
                .filter(this::canInterpret)
                .orElseThrow(() -> new IllegalArgumentException("کاربر انتخاب‌شده مجاز به تفسیر رادیوگرافی نیست"));
    }

    private User resolveManagerAssignee(RadiographyWorkflow workflow) {
        User assigner = workflow.getAssignedBy();
        if (isReviewer(assigner)) {
            return assigner;
        }
        return userRepository.findAssignableUsers(Permission.REVIEW_RADIOGRAPHY_REPORT).stream()
                .findFirst()
                .flatMap(ref -> userRepository.findById(ref.id()))
                .orElseThrow(() -> new IllegalStateException(
                        "هیچ مدیر فعالی برای تأیید نهایی گزارش رادیوگرافی وجود ندارد"));
    }

    // =====================================================================
    // داخلی: قفل و وضعیت
    // =====================================================================

    private RadiographyWorkflow lockForUpdate(UUID workflowId) {
        return workflowRepository.findByIdForUpdate(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException(WORKFLOW_NOT_FOUND));
    }

    private RadiographyWorkflow lockOpen(UUID workflowId) {
        RadiographyWorkflow workflow = lockForUpdate(workflowId);
        if (!workflow.getStatus().isOpen()) {
            throw new IllegalStateException("این گردش‌کار قبلاً به پایان رسیده یا لغو شده است");
        }
        return workflow;
    }

    private void requireManagerReview(RadiographyWorkflow workflow) {
        if (workflow.getStatus() != RadiographyWorkflowStatus.MANAGER_REVIEW) {
            throw new IllegalStateException(
                    "این گزارش در انتظار تأیید مدیر نیست (قبلاً تصمیم‌گیری شده یا هنوز توسط مفسر تأیید نشده است)");
        }
        Task task = workflow.getManagerTask();
        if (task == null || task.getStatus().isTerminal()) {
            throw new IllegalStateException("وظیفه‌ی تأیید مدیر برای این گزارش باز نیست");
        }
    }

    private static void requireConfirmed(boolean confirmed) {
        if (!confirmed) {
            throw new IllegalArgumentException("تأیید صریح کاربر ارسال نشده است");
        }
    }

    private static String requireReason(String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("ذکر دلیل الزامی است");
        }
        if (clean.length() > MAX_REASON) {
            throw new IllegalArgumentException("دلیل نباید بیشتر از " + MAX_REASON + " نویسه باشد");
        }
        return clean;
    }

    private static boolean isInInterpretation(Piece piece) {
        return piece.getCurrentStage() == PieceStage.RADIOGRAPHY
                && piece.getRadiographySubStage() == RadiographySubStage.INTERPRETATION;
    }

    private static void requireStillInInterpretation(Piece piece) {
        if (piece.getArchivedAt() != null) {
            throw new IllegalStateException("قطعه بایگانی‌شده است؛ گردش‌کار را لغو یا قطعه را بازگردانی کنید");
        }
        if (!isInInterpretation(piece)) {
            throw new IllegalStateException(
                    "قطعه دیگر در مرحله‌ی تفسیر رادیوگرافی نیست؛ گردش‌کار را لغو کنید");
        }
    }

    private void close(RadiographyWorkflow workflow, RadiographyWorkflowStatus status, LocalDateTime at) {
        workflow.setStatus(status);
        workflow.setOpenKey(null);
        workflow.setCompletedAt(at);
        workflow.setUpdatedAt(at);
    }

    // =====================================================================
    // داخلی: گزارش، نسخه، امضا
    // =====================================================================

    /** مفسر فقط وقتی می‌تواند صادر کند که گزارش و نتیجه‌ی همه‌ی کورس‌ها ثبت شده باشد */
    private void validateReportsReadyToIssue(UUID pieceId) {
        List<RadiographyReportResponse> reports = reportService.getReportsByPiece(pieceId);
        if (reports.isEmpty()) {
            throw new IllegalStateException("پیش از صدور، باید حداقل یک گزارش رادیوگرافی برای این قطعه ثبت شده باشد");
        }
        for (RadiographyReportResponse report : reports) {
            List<RadiographyReportLineResponse> lines = report.getLines() == null ? List.of() : report.getLines();
            if (lines.isEmpty()) {
                throw new IllegalStateException("گزارش «" + report.getTemplateName() + "» هیچ کورسی ندارد");
            }
            for (RadiographyReportLineResponse line : lines) {
                if (line.getResult() == null || line.getResult().isBlank()) {
                    throw new IllegalStateException("نتیجه‌ی کورس «" + line.getCourseCode() + "» در گزارش «"
                            + report.getTemplateName() + "» هنوز ثبت نشده است");
                }
            }
        }
    }

    /**
     * وضعیت همه‌ی گزارش‌های قطعه را عوض می‌کند. اگر finalizing باشد، نشان می‌دهد گزارش نهایی می‌شود.
     * (flush تا snapshot بعدی وضعیت تازه را ببیند.)
     */
    private void setReportStatuses(UUID pieceId, String status, boolean finalizing) {
        List<RadiographyReport> reports = reportRepository.findByPieceIdOrderByCreatedAtAsc(pieceId);
        LocalDateTime now = LocalDateTime.now(clock);
        for (RadiographyReport report : reports) {
            report.setStatus(status);
            if (finalizing) {
                report.setUpdatedAt(now);
            }
        }
        reportRepository.saveAll(reports);
        reportRepository.flush();
    }

    private String snapshotJson(UUID pieceId) {
        return objectMapper.writeValueAsString(reportService.getReportsByPiece(pieceId));
    }

    private RadiographyReportVersion createVersion(
            RadiographyWorkflow workflow,
            RadiographyReportVersionKind kind,
            String snapshot,
            User actor,
            LocalDateTime at
    ) {
        RadiographyReportVersion version = new RadiographyReportVersion();
        version.setWorkflow(workflow);
        version.setPieceId(workflow.getPiece().getId());
        version.setVersionNo(versionRepository.maxVersionNo(workflow.getPiece().getId()) + 1);
        version.setKind(kind);
        version.setSnapshot(snapshot);
        version.setContentHash(sha256(snapshot));
        version.setCreatedBy(WorkflowTaskOperations.name(actor));
        version.setCreatedAt(at);
        return versionRepository.saveAndFlush(version);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 در دسترس نیست", e);
        }
    }

    private record StoredApprovalSignature(String path, String contentType) {
    }

    /**
     * امضا همیشه از پروفایل «همان کاربر احراز هویت‌شده» برداشته می‌شود؛ کلاینت نمی‌تواند امضای دیگری بفرستد.
     * هر تأیید کپی مستقل خودش را دارد. اگر تراکنش برگردد، فایل کپی هم پاک می‌شود.
     */
    private StoredApprovalSignature requireSignature(User actor) {
        if (actor.getSignaturePath() == null || actor.getSignaturePath().isBlank()) {
            throw new IllegalArgumentException("برای تأیید، ابتدا تصویر امضای خود را در پروفایل ثبت کنید");
        }
        SignatureStorageService.StoredSignature copy =
                signatureStorage.copyForApproval(actor.getSignaturePath(), actor.getSignatureContentType());
        if (copy == null) {
            throw new IllegalArgumentException("فایل امضای پروفایل پیدا نشد؛ امضای خود را دوباره ثبت کنید");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        signatureStorage.deleteIfExists(copy.relativePath());
                    }
                }
            });
        }
        return new StoredApprovalSignature(copy.relativePath(), copy.contentType());
    }

    private RadiographyWorkflowApproval newApproval(
            RadiographyWorkflow workflow,
            RadiographyApprovalType type,
            String decision,
            int round,
            User actor,
            String reason,
            StoredApprovalSignature signature,
            LocalDateTime at
    ) {
        RadiographyWorkflowApproval approval = new RadiographyWorkflowApproval();
        approval.setWorkflow(workflow);
        approval.setPieceId(workflow.getPiece().getId());
        approval.setType(type);
        approval.setDecision(decision);
        approval.setRound(round);
        approval.setApproverUserId(actor.getId());
        approval.setApproverUsername(actor.getUsername());
        approval.setApproverName(WorkflowTaskOperations.name(actor));
        approval.setApproverRole(actor.getRole().name());
        approval.setApprovedAt(at);
        approval.setReason(reason);
        if (signature != null) {
            approval.setSignaturePath(signature.path());
            approval.setSignatureContentType(signature.contentType());
        }
        return approval;
    }

    // =====================================================================
    // داخلی: تبدیل به DTO
    // =====================================================================

    private WorkflowResponse toResponse(RadiographyWorkflow workflow, User viewer) {
        Piece piece = workflow.getPiece();
        List<RadiographyWorkflowApproval> approvals =
                approvalRepository.findByWorkflowIdOrderByApprovedAtAsc(workflow.getId());
        List<ApprovalResponse> approvalDtos = approvals.stream().map(this::toApproval).toList();

        ApprovalResponse interpreterApproval = approvalDtos.stream()
                .filter(a -> a.type() == RadiographyApprovalType.INTERPRETER && "APPROVED".equals(a.decision()))
                .reduce((first, second) -> second).orElse(null);
        ApprovalResponse managerApproval = approvalDtos.stream()
                .filter(a -> a.type() == RadiographyApprovalType.MANAGER && "APPROVED".equals(a.decision()))
                .reduce((first, second) -> second).orElse(null);

        List<VersionResponse> versions = versionRepository.findByWorkflowIdOrderByVersionNoAsc(workflow.getId()).stream()
                .map(v -> new VersionResponse(v.getId(), v.getVersionNo(), v.getKind(), v.getContentHash(),
                        v.getCreatedBy(), v.getCreatedAt()))
                .toList();
        List<EventResponse> events = eventRepository.findByWorkflowIdOrderByCreatedAtAsc(workflow.getId()).stream()
                .map(e -> new EventResponse(e.getId(), e.getAction(), e.getActorUserId(), e.getActorName(),
                        e.getOldValue(), e.getNewValue(), e.getReason(), e.getCreatedAt()))
                .toList();

        RadiographyWorkflowStatus status = workflow.getStatus();
        boolean open = status.isOpen();
        boolean isInterpreter = workflow.getInterpreter().getId().equals(viewer.getId());
        boolean reviewer = isReviewer(viewer);
        WorkflowPermissions permissions = new WorkflowPermissions(
                open && status.isInterpreterEditable() && (isInterpreter || policy.isManager(viewer)),
                open && status.isInterpreterEditable() && isInterpreter && canInterpret(viewer),
                open && status == RadiographyWorkflowStatus.MANAGER_REVIEW && reviewer,
                open && status.isInterpreterEditable() && reviewer,
                open && reviewer,
                viewer.getSignaturePath() != null && !viewer.getSignaturePath().isBlank()
        );

        return new WorkflowResponse(
                workflow.getId(),
                piece.getId(),
                partLabel(piece),
                projectCode(piece),
                status,
                open,
                workflow.getRound(),
                userRef(workflow.getInterpreter()),
                userRef(workflow.getAssignedBy()),
                workflow.getAssignedAt(),
                workflow.getInterpreterTask() == null ? null : workflow.getInterpreterTask().getId(),
                workflow.getManagerTask() == null ? null : workflow.getManagerTask().getId(),
                workflow.getRejectionReason(),
                workflow.getRejectedAt(),
                workflow.getRejectedBy(),
                workflow.getCompletedAt(),
                interpreterApproval,
                managerApproval,
                approvalDtos,
                versions,
                events,
                steps(status),
                permissions
        );
    }

    private ApprovalResponse toApproval(RadiographyWorkflowApproval a) {
        return new ApprovalResponse(a.getId(), a.getType(), a.getDecision(), a.getRound(), a.getApproverUserId(),
                a.getApproverName(), a.getApproverUsername(), a.getApproverRole(), a.getApprovedAt(),
                a.getSignaturePath() != null, a.getReason(), a.getReportVersionId());
    }

    private static List<StepResponse> steps(RadiographyWorkflowStatus status) {
        String[][] def = {
                {"REPORT_ISSUED", "گزارش صادر شد"},
                {"INTERPRETER_APPROVED", "تأیید مفسر"},
                {"MANAGER_REVIEW", "در انتظار تأیید مدیر"},
                {"MANAGER_APPROVED", "تأیید مدیر"},
                {"NEXT_STAGE", "مرحله‌ی بعد: " + NEXT_STAGE_LABEL}
        };
        String[] states = switch (status) {
            case INTERPRETER_ASSIGNED, REPORT_IN_PROGRESS, REINTERPRETATION_REQUIRED ->
                    new String[]{"CURRENT", "PENDING", "PENDING", "PENDING", "PENDING"};
            case MANAGER_REVIEW -> new String[]{"DONE", "DONE", "CURRENT", "PENDING", "PENDING"};
            case MANAGER_APPROVED -> new String[]{"DONE", "DONE", "DONE", "DONE", "DONE"};
            case CANCELLED -> new String[]{"PENDING", "PENDING", "PENDING", "PENDING", "PENDING"};
        };
        List<StepResponse> result = new ArrayList<>();
        for (int i = 0; i < def.length; i++) {
            result.add(new StepResponse(def[i][0], def[i][1], states[i]));
        }
        return result;
    }

    private static UserRefResponse userRef(User user) {
        return user == null ? null
                : new UserRefResponse(user.getId(), user.getUsername(), user.getFullName(), user.getDepartment());
    }

    private static String partLabel(Piece piece) {
        return TaskMapper.partLabel(piece.getPieceDefinition().getPieceName(), piece.getPieceNumber());
    }

    private static String projectCode(Piece piece) {
        return piece.getPieceDefinition().getProject().getCode();
    }

    /** الگوی LIKE امن (نویسه‌های خاص escape می‌شوند)؛ جستجوی خالی = بدون فیلتر */
    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}

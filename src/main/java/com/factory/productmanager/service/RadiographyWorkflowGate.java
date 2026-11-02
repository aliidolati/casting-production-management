package com.factory.productmanager.service;

import com.factory.productmanager.exception.ForbiddenOperationException;
import com.factory.productmanager.model.entity.RadiographyWorkflow;
import com.factory.productmanager.model.entity.RadiographyWorkflowAction;
import com.factory.productmanager.model.entity.RadiographyWorkflowStatus;
import com.factory.productmanager.model.entity.Role;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.RadiographyWorkflowRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * نگهبان بین سرویس‌های موجود (گزارش/عیب/مرحله‌ی قطعه) و گردش‌کار تفسیر رادیوگرافی. فقط وابسته به
 * ریپازیتوری‌هاست تا چرخه‌ی وابستگی با سرویس‌هایی که خودِ گردش‌کار صدا می‌زند ایجاد نشود.
 *
 * <ul>
 *   <li>تا وقتی گردش‌کار تفسیر یک قطعه باز است، فقط مفسر مسئول (و مدیر) گزارش/عیوب را ویرایش می‌کند؛</li>
 *   <li>از لحظه‌ی تأیید مفسر تا تصمیم مدیر، گزارش و عیوب برای غیرمدیر قفل هستند؛</li>
 *   <li>تا پایان یا لغو گردش‌کار، مرحله‌ی قطعه را نمی‌شود دستی عوض کرد (جابه‌جایی فقط با تأیید مدیر).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RadiographyWorkflowGate {

    private final RadiographyWorkflowRepository workflowRepository;
    private final UserRepository userRepository;
    private final RadiographyWorkflowEventRecorder eventRecorder;
    private final WorkflowTaskOperations taskOperations;
    private final Clock clock;

    /** باید در تراکنش فراخواننده صدا زده شود؛ اولین ویرایش مفسر، گردش‌کار را به «در حال تفسیر» می‌برد */
    public void assertReportEditable(UUID pieceId) {
        Optional<RadiographyWorkflow> open = workflowRepository.findByOpenKeyForUpdate(pieceId.toString());
        if (open.isEmpty()) {
            return;
        }
        RadiographyWorkflow workflow = open.get();
        String username = currentUsername();
        User actor = username == null ? null : userRepository.findByUsername(username).orElse(null);
        // Only the full-access manager is exempt; ADMIN still follows the review lock.
        if (actor != null && !actor.isDisabled() && actor.getRole() == Role.MANAGER) return;
        if (!workflow.getStatus().isInterpreterEditable()) {
            throw new IllegalStateException("گزارش و عیوب تا تصمیم تأییدکننده قفل هستند؛ فقط مدیر می‌تواند اصلاح کند");
        }
        if (username == null) return; // existing trusted internal edits in interpreter-editable states
        if (actor == null || actor.isDisabled()) {
            throw new ForbiddenOperationException("شما اجازه ویرایش این گزارش را ندارید");
        }
        boolean assignedInterpreter = workflow.getInterpreter().getId().equals(actor.getId());
        if (!assignedInterpreter) {
            throw new ForbiddenOperationException(
                    "این گزارش در حال تفسیر توسط مفسر مسئول است و ویرایش آن برای شما مجاز نیست");
        }
        if (assignedInterpreter) {
            markStarted(workflow, actor);
        }
    }

    public boolean isPendingReview(UUID pieceId) {
        return workflowRepository.existsByOpenKeyAndStatus(pieceId.toString(), RadiographyWorkflowStatus.MANAGER_REVIEW);
    }

    /** A shared template must not indirectly mutate pending reports. The manager can override this lock. */
    public void assertTemplateEditable(UUID definitionId) {
        var pending = workflowRepository.findPendingByDefinitionForUpdate(definitionId);
        if (pending.isEmpty()) return;
        String username = currentUsername();
        User actor = username == null ? null : userRepository.findByUsername(username).orElse(null);
        if (actor == null || actor.isDisabled() || actor.getRole() != Role.MANAGER)
            throw new IllegalStateException("قالب دارای گزارش در انتظار تأیید است؛ فقط مدیر می‌تواند اصلاح کند");
    }

    /** قطعه‌ای که گردش‌کار باز دارد با انتقال دستی جابه‌جا نمی‌شود */
    public void assertPieceNotInWorkflow(UUID pieceId) {
        if (workflowRepository.existsByOpenKey(pieceId.toString())) {
            throw new IllegalStateException(
                    "برای این قطعه گردش‌کار تفسیر رادیوگرافی باز است؛ تا تأیید مدیر یا لغو گردش‌کار، مرحله‌ی قطعه قابل تغییر نیست");
        }
    }

    private void markStarted(RadiographyWorkflow workflow, User actor) {
        RadiographyWorkflowStatus status = workflow.getStatus();
        if (status == RadiographyWorkflowStatus.REPORT_IN_PROGRESS) {
            return;
        }
        OperationClock op = new OperationClock(clock);
        workflow.setStatus(RadiographyWorkflowStatus.REPORT_IN_PROGRESS);
        workflow.setUpdatedAt(op.base());
        workflowRepository.save(workflow);
        if (workflow.getInterpreterTask() != null) {
            taskOperations.markInProgress(workflow.getInterpreterTask(), actor, op);
        }
        eventRecorder.record(workflow, RadiographyWorkflowAction.RADIOGRAPHY_REPORT_STARTED, actor,
                status.name(), RadiographyWorkflowStatus.REPORT_IN_PROGRESS.name(), null, op.tick());
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }
}
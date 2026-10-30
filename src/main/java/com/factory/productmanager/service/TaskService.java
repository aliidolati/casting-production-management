package com.factory.productmanager.service;

import com.factory.productmanager.exception.ForbiddenOperationException;
import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.PieceRepository;
import com.factory.productmanager.repository.ProjectRepository;
import com.factory.productmanager.repository.TaskRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * عملیات نوشتنیِ وظایف. هر تغییر:
 * <ol>
 *   <li>سطح دسترسی را در سرور بررسی می‌کند (نه در UI)،</li>
 *   <li>ورودی و ارتباط‌ها (مسئول، پروژه، قطعه) را اعتبارسنجی می‌کند،</li>
 *   <li>یک رویداد ساخت‌یافته در تاریخچه ثبت می‌کند (چه کسی، چه چیزی، از چه به چه)،</li>
 *   <li>اعلان لازم را برای طرف‌های مرتبط می‌سازد،</li>
 * </ol>
 * و همه‌ی این‌ها در یک تراکنش‌اند؛ پس یا همه ثبت می‌شوند یا هیچ‌کدام.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaskService {

    private static final long PAST_TOLERANCE_MINUTES = 1;
    private static final long MAX_FUTURE_YEARS = 10;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final PieceRepository pieceRepository;
    private final TaskLookup lookup;
    private final TaskAccessPolicy policy;
    private final TaskActivityService activityService;
    private final TaskNotificationService notificationService;
    private final TaskChecklistService checklistService;
    private final TaskDetailAssembler assembler;
    private final Clock clock;

    // ------------------------------------------------------------------ create

    public TaskDetailResponse create(CreateTaskRequest request, String username) {
        User actor = lookup.actor(username);
        requireManagePermission(actor);

        OperationClock op = new OperationClock(clock);
        User assignee = resolveAssignee(request.assigneeId());
        Links links = resolveLinks(request.projectId(), request.partId(), null);
        LocalDateTime dueDate = normalizeDueDate(request.dueDate());
        validateNewDueDate(dueDate, op.base());

        Task task = new Task();
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setStatus(TaskStatus.TODO);
        task.setPriority(request.priority());
        task.setCreatedBy(actor);
        task.setAssignedTo(assignee);
        task.setDueDate(dueDate);
        task.setProject(links.project());
        task.setPart(links.part());
        task.setCreatedAt(op.base());
        task.setUpdatedAt(op.base());
        task = taskRepository.save(task);

        activityService.record(task, actor, TaskActivityAction.CREATED, null, task.getTitle(), null, op.tick());
        activityService.record(task, actor, TaskActivityAction.ASSIGNED, null, displayName(assignee), null, op.tick());
        checklistService.createInitialItems(task, request.checklist(), op);
        notificationService.onAssigned(task, actor);

        return assembler.assemble(task, actor);
    }

    // ------------------------------------------------------------------ update (full edit)

    public TaskDetailResponse update(UUID taskId, UpdateTaskRequest request, String username) {
        User actor = lookup.actor(username);
        requireManagePermission(actor);
        Task task = lookup.manageableTask(taskId, actor);
        requireOpen(task);

        OperationClock op = new OperationClock(clock);
        boolean changed = false;

        String newTitle = request.title().trim();
        if (!newTitle.equals(task.getTitle())) {
            activityService.record(task, actor, TaskActivityAction.TITLE_CHANGED, task.getTitle(), newTitle, null, op.tick());
            task.setTitle(newTitle);
            changed = true;
        }

        String newDescription = blankToNull(request.description());
        if (!Objects.equals(blankToNull(task.getDescription()), newDescription)) {
            activityService.record(task, actor, TaskActivityAction.DESCRIPTION_CHANGED,
                    task.getDescription(), newDescription, null, op.tick());
            task.setDescription(newDescription);
            changed = true;
        }

        changed |= applyLinks(task, request.projectId(), request.partId(), actor, op);
        changed |= applyPriority(task, request.priority(), actor, op);
        changed |= applyDeadline(task, request.dueDate(), actor, op);
        changed |= applyAssignee(task, request.assigneeId(), actor, op);

        if (changed) {
            task.setUpdatedAt(op.base());
        }
        return assembler.assemble(taskRepository.save(task), actor);
    }

    // ------------------------------------------------------------------ field operations

    public TaskDetailResponse changePriority(UUID taskId, ChangePriorityRequest request, String username) {
        User actor = lookup.actor(username);
        requireManagePermission(actor);
        Task task = lookup.manageableTask(taskId, actor);
        requireOpen(task);

        OperationClock op = new OperationClock(clock);
        if (applyPriority(task, request.priority(), actor, op)) {
            task.setUpdatedAt(op.base());
        }
        return assembler.assemble(task, actor);
    }

    public TaskDetailResponse changeDeadline(UUID taskId, ChangeDeadlineRequest request, String username) {
        User actor = lookup.actor(username);
        requireManagePermission(actor);
        Task task = lookup.manageableTask(taskId, actor);
        requireOpen(task);

        OperationClock op = new OperationClock(clock);
        if (applyDeadline(task, request.dueDate(), actor, op)) {
            task.setUpdatedAt(op.base());
        }
        return assembler.assemble(task, actor);
    }

    public TaskDetailResponse changeAssignee(UUID taskId, ChangeAssigneeRequest request, String username) {
        User actor = lookup.actor(username);
        requireManagePermission(actor);
        Task task = lookup.manageableTask(taskId, actor);
        requireOpen(task);

        OperationClock op = new OperationClock(clock);
        if (applyAssignee(task, request.assigneeId(), actor, op)) {
            task.setUpdatedAt(op.base());
        }
        return assembler.assemble(task, actor);
    }

    // ------------------------------------------------------------------ status

    public TaskDetailResponse changeStatus(UUID taskId, ChangeStatusRequest request, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.visibleTask(taskId, actor);
        requireNotWorkflowTask(task);

        OperationClock op = new OperationClock(clock);
        transition(task, actor, request.status(), blankToNull(request.reason()), op);
        return assembler.assemble(task, actor);
    }

    /**
     * «حذف» وظیفه = لغو آن. وظیفه و تاریخچه‌اش هرگز پاک نمی‌شوند (قابلیت ردیابی)؛
     * لغو یک وضعیت پایانی است که بعداً هم می‌شود بازگشایی‌اش کرد.
     */
    public TaskDetailResponse cancel(UUID taskId, String reason, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.manageableTask(taskId, actor);
        requireNotWorkflowTask(task);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateException("این وظیفه قبلاً لغو شده است");
        }
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new IllegalStateException("وظیفه‌ی تکمیل‌شده را نمی‌توان لغو کرد");
        }
        OperationClock op = new OperationClock(clock);
        transition(task, actor, TaskStatus.CANCELLED, blankToNull(reason), op);
        return assembler.assemble(task, actor);
    }

    private void transition(Task task, User actor, TaskStatus target, String reason, OperationClock op) {
        TaskStatus from = task.getStatus();
        if (from == target) {
            throw new IllegalStateException("وظیفه از قبل در همین وضعیت است");
        }
        if (!policy.allowedStatusTargets(actor, task).contains(target)) {
            throw new ForbiddenOperationException("شما اجازه‌ی تغییر وضعیت این وظیفه به این مقدار را ندارید");
        }

        task.setStatus(target);
        task.setCompletedAt(target == TaskStatus.COMPLETED ? op.base() : null);
        task.setUpdatedAt(op.base());

        boolean reopened = from.isTerminal() && target.isOpen();
        TaskActivityAction action = target == TaskStatus.COMPLETED ? TaskActivityAction.COMPLETED
                : target == TaskStatus.CANCELLED ? TaskActivityAction.CANCELLED
                : reopened ? TaskActivityAction.REOPENED
                : TaskActivityAction.STATUS_CHANGED;
        activityService.record(task, actor, action, from.name(), target.name(), reason, op.tick());

        if (target == TaskStatus.COMPLETED) {
            notificationService.onCompleted(task, actor);
        } else if (target == TaskStatus.CANCELLED) {
            notificationService.onCancelled(task, actor);
        } else if (reopened) {
            notificationService.onReopened(task, actor);
        }
    }

    // ------------------------------------------------------------------ field appliers (return true if changed)

    private boolean applyPriority(Task task, TaskPriority newPriority, User actor, OperationClock op) {
        if (newPriority == null || newPriority == task.getPriority()) {
            return false;
        }
        activityService.record(task, actor, TaskActivityAction.PRIORITY_CHANGED,
                task.getPriority().name(), newPriority.name(), null, op.tick());
        task.setPriority(newPriority);
        notificationService.onPriorityChanged(task, actor);
        return true;
    }

    private boolean applyDeadline(Task task, LocalDateTime requested, User actor, OperationClock op) {
        LocalDateTime newDue = normalizeDueDate(requested);
        if (Objects.equals(newDue, task.getDueDate())) {
            return false;
        }
        validateNewDueDate(newDue, op.base());
        activityService.record(task, actor, TaskActivityAction.DEADLINE_CHANGED,
                task.getDueDate() == null ? null : task.getDueDate().toString(),
                newDue == null ? null : newDue.toString(), null, op.tick());
        task.setDueDate(newDue);
        notificationService.onDeadlineChanged(task, actor);
        return true;
    }

    private boolean applyAssignee(Task task, UUID newAssigneeId, User actor, OperationClock op) {
        if (newAssigneeId == null || newAssigneeId.equals(task.getAssignedTo().getId())) {
            return false;
        }
        requireNotWorkflowTask(task);
        User previous = task.getAssignedTo();
        User next = resolveAssignee(newAssigneeId);
        activityService.record(task, actor, TaskActivityAction.ASSIGNEE_CHANGED,
                displayName(previous), displayName(next), null, op.tick());
        task.setAssignedTo(next);
        notificationService.onReassigned(task, actor, previous);
        return true;
    }

    private boolean applyLinks(Task task, UUID projectId, UUID partId, User actor, OperationClock op) {
        UUID currentProjectId = task.getProject() == null ? null : task.getProject().getId();
        UUID currentPartId = task.getPart() == null ? null : task.getPart().getId();

        Links links = resolveLinks(projectId, partId, currentPartId);
        UUID newProjectId = links.project() == null ? null : links.project().getId();
        UUID newPartId = links.part() == null ? null : links.part().getId();

        boolean changed = false;
        if (task.isWorkflowTask()
                && (!Objects.equals(currentProjectId, newProjectId) || !Objects.equals(currentPartId, newPartId))) {
            requireNotWorkflowTask(task);
        }
        if (!Objects.equals(currentProjectId, newProjectId)) {
            activityService.record(task, actor, TaskActivityAction.PROJECT_CHANGED,
                    task.getProject() == null ? null : task.getProject().getCode(),
                    links.project() == null ? null : links.project().getCode(), null, op.tick());
            task.setProject(links.project());
            changed = true;
        }
        if (!Objects.equals(currentPartId, newPartId)) {
            activityService.record(task, actor, TaskActivityAction.PART_CHANGED,
                    partText(task.getPart()), partText(links.part()), null, op.tick());
            task.setPart(links.part());
            changed = true;
        }
        return changed;
    }

    // ------------------------------------------------------------------ validation

    private void requireManagePermission(User actor) {
        if (!policy.hasManagePermission(actor)) {
            throw new ForbiddenOperationException("شما اجازه‌ی ساخت یا ویرایش وظیفه را ندارید");
        }
    }

    /**
     * وظایف گردش‌کار رادیوگرافی فقط توسط خود گردش‌کار بسته، بازگشایی، لغو یا واگذار می‌شوند؛
     * وگرنه با API عمومی وظایف می‌شد مرحله‌ی تأیید را دور زد (مثلاً «تکمیل» بدون تأیید واقعی).
     */
    private void requireNotWorkflowTask(Task task) {
        if (task.isWorkflowTask()) {
            throw new IllegalStateException(
                    "این وظیفه بخشی از گردش‌کار است و فقط از صفحه‌ی مربوط به آن قابل تغییر است");
        }
    }

    private void requireOpen(Task task) {
        if (task.getStatus().isTerminal()) {
            throw new IllegalStateException("این وظیفه بسته شده است؛ برای تغییر، ابتدا آن را بازگشایی کنید");
        }
    }

    /**
     * پیام عمداً برای «کاربر وجود ندارد»، «غیرفعال است» و «اجازه‌ی دریافت وظیفه ندارد» یکی است
     * تا از این مسیر نتوان وجود/وضعیت حساب‌ها را حدس زد.
     */
    private User resolveAssignee(UUID assigneeId) {
        return userRepository.findById(assigneeId)
                .filter(policy::canReceiveTasks)
                .orElseThrow(() -> new IllegalArgumentException("کاربر انتخاب‌شده قابل دریافت وظیفه نیست"));
    }

    private record Links(Project project, Piece part) {
    }

    /**
     * پروژه و قطعه‌ی اختیاری را با هم‌خوانی بررسی می‌کند:
     * قطعه همیشه پروژه‌ی خودش را تعیین می‌کند و اگر پروژه‌ی دیگری هم فرستاده شده باشد خطاست.
     * قطعه‌ی بایگانی‌شده فقط در صورتی که از قبل روی همین وظیفه بوده، می‌ماند.
     */
    private Links resolveLinks(UUID projectId, UUID partId, UUID currentPartId) {
        if (partId != null) {
            Piece piece = pieceRepository.findById(partId)
                    .orElseThrow(() -> new IllegalArgumentException("قطعه‌ی انتخاب‌شده پیدا نشد"));
            if (piece.getArchivedAt() != null && !partId.equals(currentPartId)) {
                throw new IllegalArgumentException("قطعه‌ی بایگانی‌شده را نمی‌توان به وظیفه متصل کرد");
            }
            Project owner = piece.getPieceDefinition().getProject();
            if (projectId != null && !projectId.equals(owner.getId())) {
                throw new IllegalArgumentException("قطعه‌ی انتخاب‌شده متعلق به این پروژه نیست");
            }
            return new Links(owner, piece);
        }
        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("پروژه‌ی انتخاب‌شده پیدا نشد"));
            return new Links(project, null);
        }
        return new Links(null, null);
    }

    /** ثانیه و کسر ثانیه‌ی مهلت حذف می‌شود تا مقایسه‌ی «تغییر کرده؟» با مقدار ذخیره‌شده دقیق باشد */
    private static LocalDateTime normalizeDueDate(LocalDateTime dueDate) {
        return dueDate == null ? null : dueDate.withSecond(0).withNano(0);
    }

    /** مهلت جدید نباید در گذشته باشد (با یک دقیقه تحمل) و نباید غیرمنطقی دور باشد */
    private void validateNewDueDate(LocalDateTime dueDate, LocalDateTime now) {
        if (dueDate == null) {
            return;
        }
        if (dueDate.isBefore(now.minusMinutes(PAST_TOLERANCE_MINUTES))) {
            throw new IllegalArgumentException("مهلت وظیفه نمی‌تواند در گذشته باشد");
        }
        if (dueDate.isAfter(now.plusYears(MAX_FUTURE_YEARS))) {
            throw new IllegalArgumentException("مهلت وظیفه بیش از حد دور است");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String partText(Piece piece) {
        if (piece == null) {
            return null;
        }
        return TaskMapper.partLabel(piece.getPieceDefinition().getPieceName(), piece.getPieceNumber());
    }

    private static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * تبدیل Entity به DTO. این کلاس هیچ کوئری اضافه‌ای نمی‌زند؛ همه‌ی ارتباط‌هایی که لمس می‌کند
 * (مسئول، سازنده، پروژه، قطعه) توسط EntityGraph همراه وظیفه بارگذاری شده‌اند.
 * Entity هرگز مستقیماً از API بیرون داده نمی‌شود.
 */
@Component
@RequiredArgsConstructor
public class TaskMapper {

    private final Clock clock;
    private final TaskAccessPolicy policy;

    public UserRefResponse toUserRef(User user) {
        if (user == null) {
            return null;
        }
        return new UserRefResponse(user.getId(), user.getUsername(), user.getFullName(), user.getDepartment());
    }

    public ProjectRefResponse toProjectRef(Project project) {
        if (project == null) {
            return null;
        }
        return new ProjectRefResponse(project.getId(), project.getCode(), project.getName());
    }

    public PartRefResponse toPartRef(Piece piece) {
        if (piece == null) {
            return null;
        }
        PieceDefinition definition = piece.getPieceDefinition();
        Project project = definition.getProject();
        return new PartRefResponse(
                piece.getId(),
                piece.getPieceNumber(),
                definition.getPieceName(),
                definition.getRowNum(),
                project.getId(),
                project.getCode(),
                partLabel(definition.getPieceName(), piece.getPieceNumber())
        );
    }

    public static String partLabel(String pieceName, Integer pieceNumber) {
        return pieceName + " — شماره " + pieceNumber;
    }

    public TaskSummaryResponse toSummary(Task task, ChecklistProgressResponse checklist) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new TaskSummaryResponse(
                task.getId(),
                task.getTitle(),
                task.getStatus(),
                task.getPriority(),
                assigneeRef(task),
                toUserRef(task.getCreatedBy()),
                task.getDueDate(),
                dueInMinutes(task, now),
                isOverdue(task, now),
                task.getCompletedAt(),
                toProjectRef(task.getProject()),
                toPartRef(task.getPart()),
                taskTypeOf(task),
                task.getWorkflowId(),
                checklist,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    public TaskDetailResponse toDetail(
            Task task,
            ChecklistProgressResponse checklist,
            long commentCount,
            User viewer
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new TaskDetailResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                assigneeRef(task),
                toUserRef(task.getCreatedBy()),
                task.getDueDate(),
                dueInMinutes(task, now),
                isOverdue(task, now),
                task.getCompletedAt(),
                toProjectRef(task.getProject()),
                toPartRef(task.getPart()),
                taskTypeOf(task),
                task.getWorkflowId(),
                checklist,
                commentCount,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                toPermissions(task, viewer)
        );
    }

    public TaskPermissionsResponse toPermissions(Task task, User viewer) {
        boolean manage = policy.canManage(viewer, task);
        boolean assignee = policy.isAssignee(viewer, task);
        boolean open = task.getStatus().isOpen();
        // وظایف گردش‌کار فقط از مسیر خود گردش‌کار تغییر وضعیت/مسئول می‌دهند؛ UI هیچ دکمه‌ای برایشان نشان نمی‌دهد
        boolean workflow = task.isWorkflowTask();
        List<TaskStatus> allowed = workflow ? List.<TaskStatus>of()
                : policy.allowedStatusTargets(viewer, task).stream().sorted().toList();
        return new TaskPermissionsResponse(
                manage && open && !workflow,
                manage && open && !workflow,
                true,
                manage && open,
                (manage || assignee) && open,
                allowed
        );
    }

    private UserRefResponse assigneeRef(Task task) {
        return task.getTaskType() == TaskType.MELT_PASSPORT_APPROVAL
                ? new UserRefResponse(null, null, "ادمین و تأییدکنندگان ذوب", null)
                : toUserRef(task.getAssignedTo());
    }

    /** ردیف‌های قدیمی (taskType = null) وظیفه‌ی عادی‌اند */
    static TaskType taskTypeOf(Task task) {
        return task.getTaskType() == null ? TaskType.GENERAL : task.getTaskType();
    }

    public TaskActivityResponse toActivity(TaskActivity activity) {
        return new TaskActivityResponse(
                activity.getId(),
                activity.getAction(),
                toUserRef(activity.getActor()),
                activity.getOldValue(),
                activity.getNewValue(),
                activity.getNote(),
                activity.getCreatedAt()
        );
    }

    public TaskCommentResponse toComment(TaskComment comment, User viewer, boolean viewerCanModerate) {
        boolean own = comment.getAuthor().getId().equals(viewer.getId());
        boolean edited = comment.getUpdatedAt() != null
                && comment.getCreatedAt() != null
                && comment.getUpdatedAt().isAfter(comment.getCreatedAt());
        return new TaskCommentResponse(
                comment.getId(),
                toUserRef(comment.getAuthor()),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                edited,
                own,
                own || viewerCanModerate
        );
    }

    public ChecklistItemResponse toChecklistItem(TaskChecklistItem item) {
        return new ChecklistItemResponse(
                item.getId(),
                item.getTitle(),
                item.isCompleted(),
                toUserRef(item.getCompletedBy()),
                item.getCompletedAt(),
                item.getSortOrder()
        );
    }

    /** تأخیر هرگز ذخیره نمی‌شود: وظیفه‌ی باز + مهلت گذشته */
    public static boolean isOverdue(Task task, LocalDateTime now) {
        return task.getStatus().isOpen()
                && task.getDueDate() != null
                && task.getDueDate().isBefore(now);
    }

    /** دقیقه‌ی باقی‌مانده تا مهلت (منفی = گذشته)؛ بدون مهلت null */
    public static Long dueInMinutes(Task task, LocalDateTime now) {
        if (task.getDueDate() == null) {
            return null;
        }
        return ChronoUnit.MINUTES.between(now, task.getDueDate());
    }
}

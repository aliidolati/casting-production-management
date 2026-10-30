package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * عملیات «سیستمی» روی وظایف گردش‌کار رادیوگرافی و شناسنامه ذوب؛
 * مسیرهای عمومی TaskService برای وظایف گردش‌کار بسته‌اند. همه‌ی عملیات در تراکنش فراخواننده اجرا می‌شوند
 * و رویداد تاریخچه‌ی وظیفه را هم ثبت می‌کنند. اعلان‌ها را خود گردش‌کار می‌فرستد (پیام اختصاصی دارند).
 */
@Component
@RequiredArgsConstructor
class WorkflowTaskOperations {

    private final TaskRepository taskRepository;
    private final TaskActivityService activityService;

    Task create(
            TaskType type,
            String title,
            String description,
            User creator,
            User assignee,
            Piece piece,
            UUID workflowId,
            OperationClock op
    ) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.HIGH);
        task.setTaskType(type);
        task.setWorkflowId(workflowId);
        task.setCreatedBy(creator);
        task.setAssignedTo(assignee);
        task.setProject(piece.getPieceDefinition().getProject());
        task.setPart(piece);
        task.setCreatedAt(op.base());
        task.setUpdatedAt(op.base());
        task = taskRepository.save(task);

        activityService.record(task, creator, TaskActivityAction.CREATED, null, task.getTitle(), null, op.tick());
        activityService.record(task, creator, TaskActivityAction.ASSIGNED, null, name(assignee), null, op.tick());
        return task;
    }

    void markInProgress(Task task, User actor, OperationClock op) {
        if (task.getStatus() != TaskStatus.TODO) {
            return;
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setUpdatedAt(op.base());
        activityService.record(task, actor, TaskActivityAction.STATUS_CHANGED,
                TaskStatus.TODO.name(), TaskStatus.IN_PROGRESS.name(), null, op.tick());
    }

    void complete(Task task, User actor, String note, OperationClock op) {
        if (task.getStatus().isTerminal()) {
            return;
        }
        TaskStatus from = task.getStatus();
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(op.base());
        task.setUpdatedAt(op.base());
        activityService.record(task, actor, TaskActivityAction.COMPLETED, from.name(), TaskStatus.COMPLETED.name(), note, op.tick());
    }

    /** بازگشایی همان وظیفه (دور جدید تفسیر پس از رد مدیر) */
    void reopen(Task task, User actor, String note, OperationClock op) {
        TaskStatus from = task.getStatus();
        task.setStatus(TaskStatus.TODO);
        task.setCompletedAt(null);
        task.setUpdatedAt(op.base());
        activityService.record(task, actor, TaskActivityAction.REOPENED, from.name(), TaskStatus.TODO.name(), note, op.tick());
    }

    void cancel(Task task, User actor, String note, OperationClock op) {
        if (task.getStatus().isTerminal()) {
            return;
        }
        TaskStatus from = task.getStatus();
        task.setStatus(TaskStatus.CANCELLED);
        task.setCompletedAt(null);
        task.setUpdatedAt(op.base());
        activityService.record(task, actor, TaskActivityAction.CANCELLED, from.name(), TaskStatus.CANCELLED.name(), note, op.tick());
    }

    void reassign(Task task, User next, User actor, OperationClock op) {
        User previous = task.getAssignedTo();
        task.setAssignedTo(next);
        task.setUpdatedAt(op.base());
        activityService.record(task, actor, TaskActivityAction.ASSIGNEE_CHANGED, name(previous), name(next), null, op.tick());
    }

    static String name(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }
}

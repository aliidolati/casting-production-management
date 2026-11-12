package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Clock;

/** Runs only inside the passport transaction/lock. Shared review access is checked live by TaskAccessPolicy. */
@Component
@RequiredArgsConstructor
class MeltWorkflowTasks {
    private final TaskRepository tasks;
    private final WorkflowTaskOperations operations;
    private final TaskNotificationService notifications;
    private final Clock clock;

    void assign(MeltPassport m, User assignee, User actor) {
        var op = new OperationClock(clock);
        var existing = tasks.findByWorkflowIdAndTaskType(m.getId(), TaskType.MELT_PASSPORT_ENTRY);
        if (existing.isEmpty()) {
            var task = operations.create(TaskType.MELT_PASSPORT_ENTRY, "تکمیل شناسنامه ذوب · " + m.getNumber(),
                    "تکمیل شناسنامه و ارسال برای تأیید؛ همه قطعات عضو پس از تأیید منتقل می‌شوند.", actor, assignee,
                    m.getPieces().get(0).getPiece(), m.getId(), op);
            notifications.sendWorkflow(assignee, actor, task, NotificationType.TASK_ASSIGNED, "شناسنامه ذوب برای تکمیل به شما تخصیص یافت");
        } else {
            var task = existing.get(0);
            if (!task.getAssignedTo().getId().equals(assignee.getId())) {
                operations.reassign(task, assignee, actor, op);
                notifications.sendWorkflow(assignee, actor, task, NotificationType.TASK_ASSIGNED, "شناسنامه ذوب به شما واگذار شد");
            }
            if (task.getStatus().isTerminal()) operations.reopen(task, actor, "تکمیل شناسنامه ذوب", op);
        }
    }

    void submitted(MeltPassport m, User actor) {
        var op = new OperationClock(clock);
        tasks.findByWorkflowIdAndTaskType(m.getId(), TaskType.MELT_PASSPORT_ENTRY)
                .forEach(t -> operations.complete(t, actor, "ارسال شناسنامه برای تأیید", op));
        // One shared task, visible to every active ADMIN/APPROVE_MELT_PASSPORT user, including future grants.
        operations.create(TaskType.MELT_PASSPORT_APPROVAL, "تأیید شناسنامه ذوب · " + m.getNumber(),
                "بررسی شناسنامه و تأیید انتقال همه قطعات عضو یا بازگشت برای اصلاح.", actor, actor,
                m.getPieces().get(0).getPiece(), m.getId(), op);
    }

    void decided(MeltPassport m, User actor, boolean approved) {
        var op = new OperationClock(clock);
        tasks.findByWorkflowIdAndTaskType(m.getId(), TaskType.MELT_PASSPORT_APPROVAL)
                .forEach(t -> operations.complete(t, actor, approved ? "تأیید شناسنامه و انتقال قطعات" : "بازگشت برای اصلاح", op));
        if (!approved) tasks.findByWorkflowIdAndTaskType(m.getId(), TaskType.MELT_PASSPORT_ENTRY)
                .forEach(t -> {
                    operations.reopen(t, actor, m.getCorrectionReason(), op);
                    notifications.sendWorkflow(t.getAssignedTo(), actor, t, NotificationType.TASK_REOPENED, "شناسنامه ذوب برای اصلاح بازگشت: " + m.getCorrectionReason());
                });
    }

    void cancelled(MeltPassport m, User actor, String reason) {
        var op=new OperationClock(clock);
        for(var type:java.util.List.of(TaskType.MELT_PASSPORT_ENTRY,TaskType.MELT_PASSPORT_APPROVAL))
            tasks.findByWorkflowIdAndTaskType(m.getId(),type).forEach(t->operations.cancel(t,actor,reason,op));
    }

    void delete(MeltPassport passport) {
        for (var type : java.util.List.of(TaskType.MELT_PASSPORT_ENTRY, TaskType.MELT_PASSPORT_APPROVAL)) {
            tasks.deleteAll(tasks.findByWorkflowIdAndTaskType(passport.getId(), type));
        }
    }

    void syncPiece(MeltPassport passport) {
        var piece = passport.getPieces().get(0).getPiece();
        for (var task : tasks.findByWorkflowIdAndTaskType(passport.getId(), TaskType.MELT_PASSPORT_ENTRY)) {
            task.setPart(piece);
            task.setProject(piece.getPieceDefinition().getProject());
        }
        for (var task : tasks.findByWorkflowIdAndTaskType(passport.getId(), TaskType.MELT_PASSPORT_APPROVAL)) {
            if (task.getStatus().isTerminal()) continue;
            task.setPart(piece);
            task.setProject(piece.getPieceDefinition().getProject());
        }
    }
}

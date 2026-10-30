package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.NotificationType;
import com.factory.productmanager.model.entity.SystemNotification;
import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.TaskPriority;
import com.factory.productmanager.model.entity.TaskType;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.SystemNotificationRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * اعلان‌های مرتبط با وظایف. از همان صندوق و همان زنگوله‌ی موجود (SystemNotification) استفاده می‌کند
 * و سیستم اعلان دوم نمی‌سازد.
 *
 * ضد اسپم:
 * <ul>
 *   <li>انجام‌دهنده‌ی تغییر هیچ‌وقت برای کار خودش اعلان نمی‌گیرد.</li>
 *   <li>اعلان‌های «تغییر» و «نظر جدید» تا وقتی اعلان هم‌نوعِ قبلی همان وظیفه خوانده نشده،
 *       ادغام می‌شوند (به‌جای اعلان تازه، همان اعلان به‌روز می‌شود).</li>
 *   <li>یادآوری‌های مهلت با dedupeKey فقط یک بار برای هر وظیفه/مهلت ساخته می‌شوند.</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaskNotificationService {

    private static final int MAX_MESSAGE = 512;

    private final SystemNotificationRepository notificationRepository;
    private final EntityManager entityManager;
    private final Clock clock;
    private final TaskAccessPolicy accessPolicy;

    public void onAssigned(Task task, User actor) {
        send(task.getAssignedTo(), actor, task, NotificationType.TASK_ASSIGNED,
                "وظیفه‌ی جدیدی به شما محول شد: «" + task.getTitle() + "»", false, null);
    }

    public void onReassigned(Task task, User actor, User previousAssignee) {
        onAssigned(task, actor);
        send(previousAssignee, actor, task, NotificationType.TASK_UPDATED,
                "وظیفه‌ی «" + task.getTitle() + "» از شما به "
                        + displayName(task.getAssignedTo()) + " واگذار شد", true, null);
        // سازنده فقط اگر خودش یکی از دو طرف واگذاری نیست (آن‌ها پیام اختصاصی خودشان را گرفته‌اند) مطلع می‌شود
        UUID creatorId = task.getCreatedBy().getId();
        if (!creatorId.equals(task.getAssignedTo().getId()) && !creatorId.equals(previousAssignee.getId())) {
            send(task.getCreatedBy(), actor, task, NotificationType.TASK_UPDATED,
                    "مسئول وظیفه‌ی «" + task.getTitle() + "» به " + displayName(task.getAssignedTo()) + " تغییر کرد",
                    true, null);
        }
    }

    public void onPriorityChanged(Task task, User actor) {
        notifyAssigneeAndCreator(task, actor, NotificationType.TASK_UPDATED,
                "اولویت وظیفه‌ی «" + task.getTitle() + "» به «" + priorityLabel(task.getPriority()) + "» تغییر کرد",
                true);
    }

    public void onDeadlineChanged(Task task, User actor) {
        notifyAssigneeAndCreator(task, actor, NotificationType.TASK_UPDATED,
                "مهلت وظیفه‌ی «" + task.getTitle() + "» تغییر کرد", true);
    }

    public void onCompleted(Task task, User actor) {
        notifyAssigneeAndCreator(task, actor, NotificationType.TASK_COMPLETED,
                "وظیفه‌ی «" + task.getTitle() + "» توسط " + displayName(actor) + " تکمیل شد", false);
    }

    public void onReopened(Task task, User actor) {
        notifyAssigneeAndCreator(task, actor, NotificationType.TASK_REOPENED,
                "وظیفه‌ی «" + task.getTitle() + "» دوباره باز شد", false);
    }

    public void onCancelled(Task task, User actor) {
        notifyAssigneeAndCreator(task, actor, NotificationType.TASK_UPDATED,
                "وظیفه‌ی «" + task.getTitle() + "» لغو شد", false);
    }

    public void onComment(Task task, User author) {
        notifyAssigneeAndCreator(task, author, NotificationType.TASK_COMMENT,
                "نظر جدیدی در وظیفه‌ی «" + task.getTitle() + "» ثبت شد", true);
    }

    /**
     * یادآوری مهلت برای مسئول وظیفه. اگر همین کلید قبلاً برای همین گیرنده ثبت شده باشد کاری نمی‌کند.
     *
     * @return true اگر اعلان تازه ساخته شد
     */
    public boolean sendDeadlineReminder(UUID recipientId, Task task, NotificationType type, String dedupeKey, String message) {
        // Shared approvals have no individual assignee/deadline reminder.
        if (task.getTaskType() == TaskType.MELT_PASSPORT_APPROVAL) return false;
        if (notificationRepository.existsByRecipientIdAndDedupeKey(recipientId, dedupeKey)) {
            return false;
        }
        SystemNotification notification = new SystemNotification();
        notification.setType(type);
        notification.setMessage(truncate(message));
        notification.setRecipient(entityManager.getReference(User.class, recipientId));
        notification.setTask(entityManager.getReference(Task.class, task.getId()));
        notification.setDedupeKey(dedupeKey);
        notification.setCreatedAt(LocalDateTime.now(clock));
        notificationRepository.save(notification);
        return true;
    }

    /** اعلان گردش‌کار رادیوگرافی (پیام را خود گردش‌کار می‌سازد). انجام‌دهنده برای کار خودش اعلان نمی‌گیرد. */
    public void sendWorkflow(User recipient, User actor, Task task, NotificationType type, String message) {
        send(recipient, actor, task, type, message, false, null);
    }

    // ---------------------------------------------------------------------

    private void notifyAssigneeAndCreator(Task task, User actor, NotificationType type, String message, boolean coalesce) {
        Map<UUID, User> recipients = new LinkedHashMap<>();
        recipients.put(task.getAssignedTo().getId(), task.getAssignedTo());
        recipients.putIfAbsent(task.getCreatedBy().getId(), task.getCreatedBy());
        for (User recipient : recipients.values()) {
            send(recipient, actor, task, type, message, coalesce, null);
        }
    }

    private void send(
            User recipient,
            User actor,
            Task task,
            NotificationType type,
            String message,
            boolean coalesce,
            String dedupeKey
    ) {
        if (recipient == null || (actor != null && recipient.getId().equals(actor.getId()))) {
            return;
        }
        // assignedTo/createdBy stores the submitter for a pooled approval; it does not grant review visibility.
        if (task.getTaskType() == TaskType.MELT_PASSPORT_APPROVAL && !accessPolicy.canReviewMelt(recipient)) return;
        LocalDateTime now = LocalDateTime.now(clock);

        if (coalesce) {
            List<SystemNotification> existing = notificationRepository.findUnreadByRecipientTaskAndType(
                    recipient.getId(), task.getId(), type, PageRequest.of(0, 1));
            if (!existing.isEmpty()) {
                SystemNotification current = existing.get(0);
                current.setMessage(truncate(message));
                current.setCreatedAt(now);
                notificationRepository.save(current);
                return;
            }
        }

        SystemNotification notification = new SystemNotification();
        notification.setType(type);
        notification.setMessage(truncate(message));
        notification.setRecipient(recipient);
        notification.setTask(task);
        notification.setDedupeKey(dedupeKey);
        notification.setCreatedAt(now);
        notificationRepository.save(notification);
    }

    private static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }

    static String priorityLabel(TaskPriority priority) {
        return switch (priority) {
            case LOW -> "کم";
            case MEDIUM -> "متوسط";
            case HIGH -> "زیاد";
            case CRITICAL -> "بحرانی";
        };
    }

    private static String truncate(String message) {
        return message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE - 1) + "…";
    }
}

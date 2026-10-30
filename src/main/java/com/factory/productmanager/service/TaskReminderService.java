package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.NotificationType;
import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.TaskStatus;
import com.factory.productmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * یادآوری مهلت: «امروز»، «فردا» و «گذشته» برای مسئول وظایف باز.
 * <p>
 * ضد اسپم بودن با «کلید یکتا» تضمین می‌شود: کلید شامل شناسه‌ی وظیفه، نوع یادآوری و خودِ مهلت است؛
 * پس هر یادآوری برای هر مهلت فقط یک بار ساخته می‌شود، اجرای مکرر job چیزی تکرار نمی‌کند، و اگر مهلت
 * عوض شود یادآوری‌های آن مهلت جدید دوباره فعال می‌شوند. وظیفه‌ی بسته‌شده هیچ یادآوری‌ای نمی‌گیرد.
 * <p>
 * فقط وظایفی بررسی می‌شوند که مهلتشان در بازه‌ی [۷ روز پیش، پس‌فردا) باشد و صفحه‌به‌صفحه خوانده می‌شوند.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskReminderService {

    private static final int PAGE_SIZE = 200;
    private static final int OVERDUE_LOOKBACK_DAYS = 7;

    private final TaskRepository taskRepository;
    private final TaskNotificationService notificationService;
    private final Clock clock;

    @Value("${app.tasks.reminders.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.tasks.reminders.cron:0 */30 * * * *}")
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        try {
            int created = sendReminders();
            if (created > 0) {
                log.info("Task reminders created: {}", created);
            }
        } catch (RuntimeException e) {
            log.warn("Task reminder job failed: {}", e.getMessage());
        }
    }

    /** @return تعداد اعلان‌های تازه‌ساخته‌شده */
    @Transactional
    public int sendReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime todayEnd = now.toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime tomorrowEnd = todayEnd.plusDays(1);
        LocalDateTime since = now.minusDays(OVERDUE_LOOKBACK_DAYS);

        int created = 0;
        int page = 0;
        Slice<Task> slice;
        do {
            slice = taskRepository.findOpenDueBetween(TaskStatus.OPEN, since, tomorrowEnd, PageRequest.of(page++, PAGE_SIZE));
            for (Task task : slice.getContent()) {
                if (remind(task, now, todayEnd)) {
                    created++;
                }
            }
        } while (slice.hasNext());
        return created;
    }

    private boolean remind(Task task, LocalDateTime now, LocalDateTime todayEnd) {
        LocalDateTime due = task.getDueDate();
        String base = "task:" + task.getId() + ":";
        String dueKey = due.toString();

        if (due.isBefore(now)) {
            return notificationService.sendDeadlineReminder(task.getAssignedTo().getId(), task,
                    NotificationType.TASK_OVERDUE, base + "overdue:" + dueKey,
                    "مهلت وظیفه‌ی «" + task.getTitle() + "» گذشته است");
        }
        if (due.isBefore(todayEnd)) {
            return notificationService.sendDeadlineReminder(task.getAssignedTo().getId(), task,
                    NotificationType.TASK_DUE_TODAY, base + "today:" + dueKey,
                    "مهلت وظیفه‌ی «" + task.getTitle() + "» امروز است");
        }
        return notificationService.sendDeadlineReminder(task.getAssignedTo().getId(), task,
                NotificationType.TASK_DUE_TOMORROW, base + "tomorrow:" + dueKey,
                "مهلت وظیفه‌ی «" + task.getTitle() + "» فردا است");
    }
}

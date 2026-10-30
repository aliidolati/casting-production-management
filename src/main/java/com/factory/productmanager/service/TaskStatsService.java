package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.TaskStatsResponse;
import com.factory.productmanager.model.entity.TaskStatus;
import com.factory.productmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * آمار وظایف یک کاربر با چند کوئری شمارش سبک (بدون بارگذاری خودِ وظایف).
 * <ul>
 *   <li>overdue: وظیفه‌ی باز با مهلتِ گذشته.</li>
 *   <li>dueToday: وظیفه‌ی باز که مهلتش از «همین الان» تا پایان امروز است؛ پس با overdue هم‌پوشانی ندارد.</li>
 *   <li>completionRate = تکمیل‌شده ÷ (تکمیل‌شده + باز). لغوشده‌ها در محاسبه نمی‌آیند و اگر کاربر
 *       هنوز هیچ وظیفه‌ای نداشته باشد null برگردانده می‌شود (نه ۰٪ و نه ۱۰۰٪).</li>
 * </ul>
 * هیچ «امتیاز عملکرد» ساخته نمی‌شود؛ فقط همین شمارش‌های شفاف.
 */
@Service
@RequiredArgsConstructor
public class TaskStatsService {

    private final TaskRepository taskRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TaskStatsResponse statsFor(UUID userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime tomorrowStart = now.toLocalDate().plusDays(1).atStartOfDay();

        Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
        for (Object[] row : taskRepository.countGroupedByStatus(userId)) {
            byStatus.put((TaskStatus) row[0], ((Number) row[1]).longValue());
        }

        long todo = byStatus.getOrDefault(TaskStatus.TODO, 0L);
        long inProgress = byStatus.getOrDefault(TaskStatus.IN_PROGRESS, 0L);
        long waiting = byStatus.getOrDefault(TaskStatus.WAITING, 0L);
        long completed = byStatus.getOrDefault(TaskStatus.COMPLETED, 0L);
        long open = todo + inProgress + waiting;

        long overdue = open == 0 ? 0 : taskRepository.countOverdue(userId, TaskStatus.OPEN, now);
        long dueToday = open == 0 ? 0 : taskRepository.countDueBetween(userId, TaskStatus.OPEN, now, tomorrowStart);

        Integer completionRate = (completed + open) == 0
                ? null
                : (int) Math.round(completed * 100.0 / (completed + open));

        return new TaskStatsResponse(open, inProgress, waiting, dueToday, overdue, completed, completionRate);
    }
}

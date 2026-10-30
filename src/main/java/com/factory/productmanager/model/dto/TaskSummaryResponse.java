package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TaskPriority;
import com.factory.productmanager.model.entity.TaskStatus;
import com.factory.productmanager.model.entity.TaskType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * نسخه‌ی سبک وظیفه برای لیست‌ها: بدون توضیحات، نظرات، تاریخچه و آیتم‌های چک‌لیست.
 *
 * overdue و dueInMinutes در سرور و با ساعت برنامه محاسبه می‌شوند تا نمایش «۳ روز مانده / ۱ روز تأخیر»
 * به منطقه‌ی زمانی مرورگر وابسته نباشد. dueInMinutes منفی یعنی از مهلت گذشته است.
 */
public record TaskSummaryResponse(
        UUID id,
        String title,
        TaskStatus status,
        TaskPriority priority,
        UserRefResponse assignee,
        UserRefResponse creator,
        LocalDateTime dueDate,
        Long dueInMinutes,
        boolean overdue,
        LocalDateTime completedAt,
        ProjectRefResponse project,
        PartRefResponse part,
        TaskType taskType,
        UUID workflowId,
        ChecklistProgressResponse checklist,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

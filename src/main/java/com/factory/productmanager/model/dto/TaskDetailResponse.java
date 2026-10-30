package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TaskPriority;
import com.factory.productmanager.model.entity.TaskStatus;
import com.factory.productmanager.model.entity.TaskType;

import java.time.LocalDateTime;
import java.util.UUID;

/** جزئیات یک وظیفه. نظرات، چک‌لیست و تاریخچه با درخواست‌های جداگانه بارگذاری می‌شوند. */
public record TaskDetailResponse(
        UUID id,
        String title,
        String description,
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
        long commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        TaskPermissionsResponse permissions
) {
}

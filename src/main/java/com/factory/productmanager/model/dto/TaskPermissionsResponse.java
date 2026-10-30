package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TaskStatus;

import java.util.List;

/**
 * قابلیت‌های کاربر جاری روی یک وظیفه، فقط برای تصمیم UI (نمایش/پنهان کردن دکمه‌ها).
 * همه‌ی این قوانین در سرور هم اعمال می‌شوند؛ پنهان‌کردن دکمه جایگزین کنترل دسترسی نیست.
 */
public record TaskPermissionsResponse(
        boolean canEdit,
        boolean canCancel,
        boolean canComment,
        boolean canManageChecklist,
        boolean canToggleChecklist,
        List<TaskStatus> allowedStatuses
) {
}

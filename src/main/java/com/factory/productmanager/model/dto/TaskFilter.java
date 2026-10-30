package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TaskPriority;
import com.factory.productmanager.model.entity.TaskStatus;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * پارامترهای فیلتر/مرتب‌سازی لیست وظایف. همه‌ی فیلترها در سطح دیتابیس اعمال می‌شوند.
 * فیلتر «دسترسی» (چه وظایفی را کاربر مجاز است ببیند) جداگانه و همیشه توسط سرویس اضافه می‌شود
 * و هیچ پارامتر کلاینتی نمی‌تواند آن را دور بزند.
 *
 * @param mine      فقط وظایف محول‌شده به خودِ کاربر جاری («وظایف من»)
 * @param dueDate   وظایفی که مهلتشان در همین روز است
 * @param dueFrom   مهلت از این روز (شامل) به بعد
 * @param dueTo     مهلت تا این روز (شامل)
 * @param overdue   true: فقط دارای تأخیر؛ false: فقط بدون تأخیر؛ null: بدون فیلتر
 * @param search    جستجو در عنوان، توضیحات، پروژه، قطعه و مسئول
 * @param sort      کلید مرتب‌سازی؛ null یعنی ترتیب پیش‌فرض هوشمند
 * @param ascending جهت مرتب‌سازی (برای ترتیب هوشمند نادیده گرفته می‌شود)
 */
public record TaskFilter(
        Set<TaskStatus> statuses,
        Set<TaskPriority> priorities,
        UUID assigneeId,
        UUID creatorId,
        UUID projectId,
        UUID partId,
        LocalDate dueDate,
        LocalDate dueFrom,
        LocalDate dueTo,
        Boolean overdue,
        String search,
        boolean mine,
        TaskSort sort,
        boolean ascending,
        int page,
        int size
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public TaskFilter withAssignee(UUID newAssigneeId) {
        return new TaskFilter(statuses, priorities, newAssigneeId, creatorId, projectId, partId,
                dueDate, dueFrom, dueTo, overdue, search, false, sort, ascending, page, size);
    }
}

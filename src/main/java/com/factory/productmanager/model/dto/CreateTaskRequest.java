package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * شناسه‌ها از سمت کلاینت قابل اعتماد نیستند؛ سرویس وجود، دسترسی و سازگاری همه‌ی آن‌ها را بررسی می‌کند.
 * dueDate، زمان محلی برنامه است (بدون منطقه‌ی زمانی).
 */
public record CreateTaskRequest(
        @NotBlank(message = "عنوان وظیفه الزامی است")
        @Size(max = 200, message = "عنوان وظیفه حداکثر ۲۰۰ کاراکتر باشد")
        String title,

        @Size(max = 5000, message = "توضیحات حداکثر ۵۰۰۰ کاراکتر باشد")
        String description,

        @NotNull(message = "مسئول وظیفه الزامی است")
        UUID assigneeId,

        @NotNull(message = "اولویت الزامی است")
        TaskPriority priority,

        LocalDateTime dueDate,

        UUID projectId,

        UUID partId,

        @Size(max = 50, message = "حداکثر ۵۰ آیتم چک‌لیست مجاز است")
        List<@NotBlank(message = "عنوان آیتم چک‌لیست نباید خالی باشد")
                @Size(max = 255, message = "عنوان آیتم چک‌لیست حداکثر ۲۵۵ کاراکتر باشد") String> checklist
) {
}

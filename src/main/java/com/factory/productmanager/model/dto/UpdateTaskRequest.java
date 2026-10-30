package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/** ویرایش کامل (PUT): فیلدهای اختیاری اگر null باشند پاک می‌شوند */
public record UpdateTaskRequest(
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

        UUID partId
) {
}

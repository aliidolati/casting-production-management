package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** reason اختیاری است و برای بازگشایی وظیفه در تاریخچه ثبت می‌شود */
public record ChangeStatusRequest(
        @NotNull(message = "وضعیت جدید الزامی است")
        TaskStatus status,

        @Size(max = 500, message = "دلیل حداکثر ۵۰۰ کاراکتر باشد")
        String reason
) {
}

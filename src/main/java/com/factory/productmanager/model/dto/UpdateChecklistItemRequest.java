package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.Size;

/** هر دو فیلد اختیاری‌اند؛ فقط فیلدهای غیر null اعمال می‌شوند */
public record UpdateChecklistItemRequest(
        Boolean completed,

        @Size(max = 255, message = "عنوان آیتم چک‌لیست حداکثر ۲۵۵ کاراکتر باشد")
        String title
) {
}

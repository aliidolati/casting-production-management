package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChecklistItemRequest(
        @NotBlank(message = "عنوان آیتم چک‌لیست الزامی است")
        @Size(max = 255, message = "عنوان آیتم چک‌لیست حداکثر ۲۵۵ کاراکتر باشد")
        String title
) {
}

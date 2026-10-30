package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskCommentRequest(
        @NotBlank(message = "متن نظر نمی‌تواند خالی باشد")
        @Size(max = 4000, message = "متن نظر حداکثر ۴۰۰۰ کاراکتر باشد")
        String content
) {
}

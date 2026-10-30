package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {
    @NotBlank(message = "رمز عبور فعلی نمی‌تواند خالی باشد")
    private String currentPassword;

    @NotBlank(message = "رمز عبور جدید نمی‌تواند خالی باشد")
    @Size(min = 6, message = "رمز عبور جدید باید حداقل ۶ کاراکتر باشد")
    private String newPassword;
}

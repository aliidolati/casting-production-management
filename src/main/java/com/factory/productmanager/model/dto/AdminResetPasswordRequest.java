package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** درخواست ادمین برای تنظیم رمز عبور یک کاربر دیگر (بدون نیاز به رمز فعلی) */
@Getter
@Setter
public class AdminResetPasswordRequest {
    @NotBlank(message = "رمز عبور جدید نمی‌تواند خالی باشد")
    @Size(min = 6, message = "رمز عبور جدید باید حداقل ۶ کاراکتر باشد")
    private String newPassword;
}

package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "نام کاربری نمی‌تواند خالی باشد")
    private String username;

    @NotBlank(message = "رمز عبور نمی‌تواند خالی باشد")
    private String password;
}

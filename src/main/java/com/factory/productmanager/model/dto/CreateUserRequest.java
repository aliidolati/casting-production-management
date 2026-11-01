package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CreateUserRequest {
    @NotBlank(message = "نام کاربری نمی‌تواند خالی باشد")
    private String username;

    @NotBlank(message = "رمز عبور نمی‌تواند خالی باشد")
    @Size(min = 6, message = "رمز عبور باید حداقل ۶ کاراکتر باشد")
    private String password;

    @NotBlank(message = "نام و نام خانوادگی نمی‌تواند خالی باشد")
    private String fullName;

    @NotNull(message = "نقش کاربر نمی‌تواند خالی باشد")
    private Role role;

    /** مجوزهای صریح ادمین و اپراتور؛ مدیر همه دسترسی‌ها را دارد */
    private Set<@jakarta.validation.constraints.NotNull Permission> permissions;
}

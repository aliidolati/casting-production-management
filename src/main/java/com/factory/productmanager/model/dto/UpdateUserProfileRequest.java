package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.Size;

/** ویرایش بخش و وضعیت حساب توسط ادمین؛ فیلدهای null تغییر نمی‌کنند (بخش خالی "" یعنی پاک شود) */
public record UpdateUserProfileRequest(
        @Size(max = 100, message = "نام بخش حداکثر ۱۰۰ کاراکتر باشد")
        String department,

        Boolean active
) {
}

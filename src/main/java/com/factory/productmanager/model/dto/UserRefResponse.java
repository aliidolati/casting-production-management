package com.factory.productmanager.model.dto;

import java.util.UUID;

/** مرجع سبک یک کاربر (برای نمایش مسئول/سازنده؛ بدون هیچ داده‌ی حساس) */
public record UserRefResponse(UUID id, String username, String fullName, String department) {

    /** نام قابل‌نمایش: نام کامل، و اگر نبود نام کاربری */
    public String displayName() {
        return fullName != null && !fullName.isBlank() ? fullName : username;
    }
}

package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * موقعیت متنی عیب. نام کلیدهای API برای سازگاری حفظ شده است.
 * پایان خالی یعنی نقطه‌ای، مثل (L4)؛ پایان متفاوت یعنی بازه، مثل (L4-L6).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DefectPositionDto {
    @NotBlank(message = "شروع موقعیت را وارد کنید")
    @Size(max = 255, message = "شروع موقعیت حداکثر ۲۵۵ کاراکتر است")
    private String startCm;

    @Size(max = 255, message = "پایان موقعیت حداکثر ۲۵۵ کاراکتر است")
    private String endCm;
}

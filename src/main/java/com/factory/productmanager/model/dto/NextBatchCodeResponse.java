package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * کد پیشنهادی برای محموله‌ی بعدی. اگر هنوز محموله‌ای ثبت نشده باشد یا کد آخرین
 * محموله به عدد ختم نشود، مقدار null برمی‌گردد و کاربر خودش کد را وارد می‌کند.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NextBatchCodeResponse {
    private String code;
}

package com.factory.productmanager.model.entity;

import lombok.Getter;

/** نتیجه بازرسی در جدول تعیین‌تکلیف؛ وضعیت نهایی تست کشش از روی همین مقدار تعیین می‌شود. */
@Getter
public enum TensileInspectionResult {
    ACCEPTED("قبول"),
    REJECTED("مردود");

    private final String label;

    TensileInspectionResult(String label) {
        this.label = label;
    }
}

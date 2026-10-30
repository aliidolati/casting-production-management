package com.factory.productmanager.model.entity;

import lombok.Getter;

/**
 * وضعیت یک تست کشش.
 * <p>
 * وضعیت پس از تعیین‌تکلیف مستقیماً از «نتیجه بازرسی» گرفته می‌شود:
 * ACCEPTED → APPROVED و REJECTED → REJECTED.
 * مقدار RETEST_REQUIRED فعلاً به‌صورت خودکار ست نمی‌شود و برای استفاده‌ی آینده رزرو شده است.
 */
@Getter
public enum TensileTestStatus {
    PENDING_REVIEW("در انتظار تعیین‌تکلیف"),
    APPROVED("تایید شده"),
    REJECTED("مردود"),
    RETEST_REQUIRED("نیازمند تکرار تست");

    private final String label;

    TensileTestStatus(String label) {
        this.label = label;
    }
}

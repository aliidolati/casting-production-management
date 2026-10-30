package com.factory.productmanager.model.entity;

import lombok.Getter;

/**
 * گزینه‌های «مرحله بعدی» که مسئول کشش در جدول تعیین‌تکلیف انتخاب می‌کند.
 * APPROVED همان گزینه‌ای است که سیستم هنگام تایید خودکار تست‌های واجد شرایط ثبت می‌کند.
 */
@Getter
public enum TensileNextStage {
    APPROVED("تایید"),
    RE_OPERATION("عملیات مجدد"),
    CONCESSION_APPROVAL("تایید ارفاقی"),
    AGING("پیرسازی"),
    NEXT_TENSILE("کشش بعدی"),
    SCRAP("اسقاط"),
    OTHER("سایر");

    private final String label;

    TensileNextStage(String label) {
        this.label = label;
    }
}

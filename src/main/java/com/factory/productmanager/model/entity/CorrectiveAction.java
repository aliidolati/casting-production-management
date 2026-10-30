package com.factory.productmanager.model.entity;

import java.util.List;

/**
 * اقدام اصلاحی بعد از هر عیب در زنجیره‌ی گزارش رادیوگرافی.
 * در دیتابیس فقط name() ذخیره می‌شود و برچسب فارسی برای نمایش است.
 */
public enum CorrectiveAction {
    IN_PLACE_MACHINING("درجا تراش"),
    INSIDE_HATCH("داخل دریچه"),
    OK("OK"),
    THICKNESS_MEASUREMENT("ضخامت مجاز"),
    PART_SHAPE("شکل قطعه"),
    RESOLVED("برطرف شد"),
    GRINDING("سنگ زنی");

    private final String label;

    CorrectiveAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static final List<CorrectiveAction> ALL = List.of(values());

    /** مقدار ورودی (نام enum) را اعتبارسنجی می‌کند؛ مقدار خالی یا ناشناخته خطا می‌دهد */
    public static CorrectiveAction require(String code, String fieldLabel) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + " را انتخاب کنید");
        }
        try {
            return CorrectiveAction.valueOf(code.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldLabel + " معتبر نیست");
        }
    }

    /** مثل require ولی اختیاری: مقدار خالی null می‌دهد و فقط مقدار ناشناخته خطا است */
    public static CorrectiveAction optional(String code, String fieldLabel) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return require(code, fieldLabel);
    }

    /** برچسب فارسی برای کد ذخیره‌شده؛ برای null یا مقدار ناشناخته null برمی‌گرداند */
    public static String labelOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return CorrectiveAction.valueOf(code.trim()).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

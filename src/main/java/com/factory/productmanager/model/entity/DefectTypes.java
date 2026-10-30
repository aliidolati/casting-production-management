package com.factory.productmanager.model.entity;

import java.util.List;
import java.util.Set;

/**
 * فهرست واحد نوع عیب برای رادیوگرافی، جوشکاری و نقشه جوش.
 */
public final class DefectTypes {
    public static final String OTHER = "OTHER";

    public static final List<String> STANDARD = List.of(
            "F.M.M",
            "F.M.L",
            "F.M",
            "S.P",
            "S.C",
            "CR",
            "G.H",
            "G.P",
            "VT",
            "C.S",
            "M.R",
            "ماسه ریزی",
            "ابعادی"
    );

    public static final Set<String> STANDARD_SET = Set.copyOf(STANDARD);

    private DefectTypes() {
    }

    public static String requireStoredType(String defectType, String custom) {
        String type = defectType == null ? "" : defectType.trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("نوع عیب را انتخاب کنید");
        }
        if (OTHER.equals(type)) {
            String detail = custom == null ? "" : custom.trim();
            if (detail.isEmpty()) {
                throw new IllegalArgumentException("برای گزینهٔ سایر، نوع عیب را بنویسید");
            }
            return OTHER;
        }
        if (!STANDARD_SET.contains(type)) {
            throw new IllegalArgumentException("نوع عیب انتخاب‌شده معتبر نیست");
        }
        return type;
    }

    public static String storedCustom(String storedType, String custom) {
        if (!OTHER.equals(storedType)) {
            return null;
        }
        String detail = custom == null ? "" : custom.trim();
        return detail.isEmpty() ? null : detail;
    }

    public static String displayLabel(String defectType, String custom) {
        if (OTHER.equals(defectType)) {
            if (custom != null && !custom.isBlank()) {
                return custom.trim();
            }
            return "سایر";
        }
        if ("UNSPECIFIED".equals(defectType)) {
            return "بدون عیب ثبت‌شده";
        }
        return defectType;
    }
}

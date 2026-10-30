package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * زیرمرحله‌های داخلی مرحله‌ی «رادیوگرافی».
 * ترتیب خطی: SHOT -> INTERPRETATION -> VT و پس از VT، ورود به عملیات حرارتی.
 * نام‌های قدیمی دیتابیس با {@link #fromStored(String)} به همین سه مقدار نگاشت می‌شوند.
 */
public enum RadiographySubStage {
    SHOT,
    INTERPRETATION,
    VT;

    @JsonCreator
    public static RadiographySubStage fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim()) {
            case "AWAITING_SHOT", "AWAITING_RESHOOT", "SHOT" -> SHOT;
            case "VT" -> VT;
            case "AWAITING_INTERPRETATION", "INTERPRETATION_REJECTED",
                 "INTERPRETATION_FINALIZED", "INTERPRETATION" -> INTERPRETATION;
            default -> SHOT;
        };
    }
}

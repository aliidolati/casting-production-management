package com.factory.productmanager.model.dto;

import java.util.UUID;

/** مرجع سبک یک پروژه؛ فقط برای نمایش و ناوبری به صفحه‌ی خود پروژه */
public record ProjectRefResponse(UUID id, String code, String name) {
}

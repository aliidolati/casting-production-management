package com.factory.productmanager.model.dto;

/**
 * مقصد خروج از ایستگاه جوشکاری.
 * SHOT: رادیوگرافی — شوت
 * INTERPRETATION: رادیوگرافی — تفسیر
 * PREVIOUS: همان مرحله/زیرمرحله‌ای که قطعه قبل از جوشکاری در آن بود
 *           (فقط وقتی مبدأ هنوز به شوت نرسیده باشد)
 */
public enum WeldingCompleteDestination {
    SHOT,
    INTERPRETATION,
    PREVIOUS
}

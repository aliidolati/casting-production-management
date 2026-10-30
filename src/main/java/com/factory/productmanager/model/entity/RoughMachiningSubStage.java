package com.factory.productmanager.model.entity;

/**
 * زیرمرحله‌های داخلی مرحله‌ی «خشن‌تراشی».
 *
 * برخلاف رادیوگرافی که وضعیت‌محور است، این سه زیرمرحله کاملاً خطی‌اند و ترتیب
 * تعریفشان معنادار است: منطق «فقط رو به جلو» در PieceService روی ordinal همین
 * enum کار می‌کند، پس ترتیب مقادیر را جابه‌جا نکنید.
 */
public enum RoughMachiningSubStage {
    DEBURRING,
    INITIAL_CORRECTION,
    MACHINING
}

package com.factory.productmanager.model.entity;

import lombok.Getter;

/**
 * دسترسی‌های جزئی اپراتور. کاربر با نقش MANAGER همیشه همه‌ی این دسترسی‌ها را دارد
 * (نیازی به ثبت در جدول user_permissions ندارد).
 */
@Getter
public enum Permission {
    VIEW_DASHBOARD("مشاهده داشبورد"),
    MANAGE_PROJECTS("مدیریت پروژه‌ها"),
    MANAGE_PIECE_DEFINITIONS("مدیریت تعریف قطعات"),
    MANAGE_PIECES("مدیریت قطعات"),
    MOVE_PIECE_STAGE("جابه‌جایی بین مراحل"),
    UPDATE_RADIOGRAPHY_SUBSTAGE("زیرمرحله رادیوگرافی"),
    UPDATE_HEAT_TREATMENT_SUBSTAGE("زیرمرحله عملیات حرارتی"),
    UPDATE_ROUGH_MACHINING_SUBSTAGE("زیرمرحله خشن‌تراشی"),
    MANAGE_RADIOGRAPHY_DEFECTS("مدیریت عیوب رادیوگرافی"),
    MANAGE_PIECE_NOTES("مدیریت یادداشت‌ها"),
    MANAGE_WELDING_MAP("مدیریت نقشه جوشکاری"),
    // نام این مقدار به‌دلیل رکوردهای موجود در جدول user_permissions حفظ شده،
    // ولی از این پس فقط «ثبت تست کشش» توسط اپراتور را کنترل می‌کند
    MANAGE_PIECE_ASSIGNMENT("ثبت تست کشش"),
    REVIEW_TENSILE_TEST("تعیین‌تکلیف تست کشش"),
    // جدا از UPDATE_HEAT_TREATMENT_SUBSTAGE: آن یکی جابه‌جایی زیرمرحله است،
    // این یکی ثبت خودِ عملیات انجام‌شده روی قطعه در یک محموله‌ی کوره
    MANAGE_HEAT_TREATMENT_LOG("ثبت عملیات حرارتی"),
    // «مدیر وظایف»: ساخت و واگذاری وظیفه و مدیریت وظایفی که خودش ساخته است.
    // ادمین همه‌ی وظایف را می‌بیند و مدیریت می‌کند.
    MANAGE_TASKS("مدیریت وظایف (ساخت و واگذاری)"),
    // امکان دریافت وظیفه: فقط کاربرانی که این دسترسی (یا نقش ادمین) را دارند قابل انتخاب به‌عنوان مسئول‌اند
    RECEIVE_TASKS("دریافت وظیفه"),
    // گردش‌کار تفسیر رادیوگرافی: «مفسر» گزارش را تکمیل و صادر می‌کند؛
    // «مدیر رادیوگرافی» مفسر را تخصیص می‌دهد و گزارش را تأیید یا رد می‌کند. ادمین هر دو را ضمنی دارد.
    INTERPRET_RADIOGRAPHY("تفسیر و صدور گزارش رادیوگرافی"),
    REVIEW_RADIOGRAPHY_REPORT("تخصیص مفسر و تأیید نهایی گزارش رادیوگرافی"),
    CANCEL_MELT_PASSPORT("لغو شناسنامه ذوب"),
    CANCEL_WELDING_DISPATCH("لغو اعزام جوشکاری"),
    EDIT_HEAT_TREATMENT_BATCH("اصلاح سیکل محموله کوره"),
    APPROVE_MELT_PASSPORT("تأیید شناسنامه ذوب");

    private final String label;

    Permission(String label) {
        this.label = label;
    }
}

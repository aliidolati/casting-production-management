package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * عیب دوم یک زنجیره: عیب 2 → تایپ 2 → اقدام اصلاحی 2.
 * عیب دوم موقعیت و جوشکاری ندارد و بعد از آن عیب دیگری وجود ندارد.
 * هم به‌عنوان بدنه‌ی درخواست (id خالی) و هم در پاسخ استفاده می‌شود.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiographySecondDefectDto {
    /** فقط در پاسخ پر می‌شود */
    private UUID id;

    @Size(max = 255, message = "نوع عیب دوم حداکثر ۲۵۵ کاراکتر است")
    private String defectType;

    @Size(max = 255, message = "نوع عیب دلخواه دوم حداکثر ۲۵۵ کاراکتر است")
    private String defectTypeCustom;

    @Size(max = 255, message = "تایپ عیب دوم حداکثر ۲۵۵ کاراکتر است")
    private String defectClass;

    /** نام یکی از مقادیر CorrectiveAction */
    private String correctiveAction;

    /** فقط در پاسخ: برچسب فارسی اقدام اصلاحی */
    private String correctiveActionLabel;
}

package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.DefectStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RadiographyDefectRequest {
    /**
     * اگر عیب از داخل جدول گزارش ثبت شود خالی می‌ماند و سرویس آن را از courseCode
     * همان سطر پر می‌کند. برای عیوب قدیمیِ خارج از گزارش هنوز دستی وارد می‌شود.
     */
    private String course;

    /**
     * اگر عیب از داخل جدول گزارش رادیوگرافی ثبت شود، شناسه‌ی کورس مربوطه اینجا می‌آید.
     */
    private UUID reportLineId;

    @NotBlank(message = "نوع عیب نمی‌تواند خالی باشد")
    @Size(max = 255, message = "نوع عیب حداکثر ۲۵۵ کاراکتر است")
    private String defectType;

    /** وقتی نوع عیب «سایر» است، شرحی که کاربر می‌نویسد */
    @Size(max = 255, message = "نوع عیب دلخواه حداکثر ۲۵۵ کاراکتر است")
    private String defectTypeCustom;

    /** کلاس عیب، مثلاً T1. اگر خالی باشد typeCode قدیمی استفاده می‌شود. */
    @Size(max = 255, message = "کلاس عیب حداکثر ۲۵۵ کاراکتر است")
    private String defectClass;

    /** نام قدیمی کلاس عیب؛ برای فرم‌هایی که هنوز typeCode می‌فرستند */
    private String typeCode;

    @Size(max = 255, message = "شروع موقعیت حداکثر ۲۵۵ کاراکتر است")
    private String startCm;

    @Size(max = 255, message = "پایان موقعیت حداکثر ۲۵۵ کاراکتر است")
    private String endCm;

    /** موقعیت تکی قدیمی. اگر startCm خالی باشد همین مقدار برای شروع و پایان استفاده می‌شود. */
    @Size(max = 255, message = "موقعیت عیب حداکثر ۲۵۵ کاراکتر است")
    private String locationCm;

    /** true یعنی عیب منجر به جوش شده است و وضعیت NEEDS_WELDING می‌شود */
    private Boolean leadsToWelding;

    @Size(max = 1000, message = "توضیح عیب حداکثر ۱۰۰۰ کاراکتر است")
    private String description;

    /* ---- زنجیره‌ی گزارش (فقط وقتی reportLineId دارد یا عیب متعلق به سطر گزارش است) ---- */

    /** موقعیت‌های عیب اول؛ حداقل یکی. هر کدام نقطه‌ای یا بازه‌ای */
    @Valid
    @Size(max = 50, message = "تعداد موقعیت‌ها بیش از حد مجاز است")
    private List<DefectPositionDto> positions;

    /** جوشکاری شده (true) یا نشده (false) برای عیب اول؛ الزامی در زنجیره */
    private Boolean welded;

    /** اقدام اصلاحی اول؛ نام یکی از مقادیر CorrectiveAction */
    private String correctiveAction;

    /** عیب دوم (اختیاری)؛ با reshootOk=true قابل ترکیب نیست. */
    @Valid
    private RadiographySecondDefectDto secondDefect;

    /** ریشوت → OK؛ فقط برای زنجیره گزارش و به جای عیب دوم. */
    private Boolean reshootOk;

    /** اگر leadsToWelding فرستاده نشود، وضعیت صریح قبلی هنوز پذیرفته می‌شود */
    private DefectStatus status;
}

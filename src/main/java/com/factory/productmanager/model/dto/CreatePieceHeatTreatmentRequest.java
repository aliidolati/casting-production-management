package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.FurnaceSize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ثبت یک عملیات حرارتی برای یک قطعه. اگر محموله‌ای با این کد وجود نداشته باشد
 * ساخته می‌شود؛ اگر وجود داشته باشد، قطعه به همان محموله اضافه می‌شود و
 * پارامترهای سیکل (تاریخ، دما، زمان، سایز کوره) از محموله‌ی موجود گرفته می‌شوند.
 */
@Getter
@Setter
public class CreatePieceHeatTreatmentRequest {
    @NotBlank(message = "شناسه محموله نمی‌تواند خالی باشد")
    @Size(max = 50, message = "شناسه محموله حداکثر ۵۰ کاراکتر است")
    private String batchCode;

    // ---- پارامترهای سیکل؛ فقط وقتی محموله تازه ساخته می‌شود استفاده می‌شوند ----

    @NotNull(message = "تاریخ عملیات نمی‌تواند خالی باشد")
    private LocalDate treatmentDate;

    private BigDecimal temperatureC;

    @DecimalMin(value = "0", message = "زمان نمی‌تواند منفی باشد")
    private BigDecimal durationHours;

    private FurnaceSize furnaceSize;

    private String batchNotes;

    // ---- داده‌های مخصوص همین قطعه ----

    /** چندمین بار عملیات روی همین قطعه؛ مثلاً «مرحله اول» */
    @Size(max = 50, message = "مرحله حداکثر ۵۰ کاراکتر است")
    private String treatmentStage;

    @Size(max = 20, message = "شماره تست پلیت حداکثر ۲۰ کاراکتر است")
    private String testPlateLabel;

    @DecimalMin(value = "0", message = "سختی تست پلیت نمی‌تواند منفی باشد")
    private BigDecimal testPlateHardnessHb;

    /** تعیین‌تکلیف کششی که این عملیات را تجویز کرده؛ اختیاری */
    private UUID tensileDispositionId;

    private String details;
}

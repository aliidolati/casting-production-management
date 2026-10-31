package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TensileInspectionResult;
import com.factory.productmanager.model.entity.TensileNextStage;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** ثبت یا ویرایش تعیین‌تکلیف یک تست کشش توسط مسئول کشش. */
@Getter
@Setter
public class SaveTensileDispositionRequest {
    private ScrapRequest scrap;
    @NotNull(message = "تاریخ تعیین‌تکلیف نمی‌تواند خالی باشد")
    private LocalDate dispositionDate;

    @Min(value = 0, message = "تعداد نمی‌تواند منفی باشد")
    private Integer quantity;

    @DecimalMin(value = "0", message = "مدت زمان نمی‌تواند منفی باشد")
    private BigDecimal durationHours;

    private BigDecimal temperatureC;

    @NotNull(message = "مرحله بعدی نمی‌تواند خالی باشد")
    private TensileNextStage nextStage;

    @NotNull(message = "نتیجه بازرسی نمی‌تواند خالی باشد")
    private TensileInspectionResult inspectionResult;

    /** مسئولی که تعیین‌تکلیف به نام او ثبت می‌شود؛ خالی یعنی خودِ کاربر جاری */
    private UUID responsibleUserId;

    private String notes;
}

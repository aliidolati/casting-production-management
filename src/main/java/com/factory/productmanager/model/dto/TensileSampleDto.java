package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** یک نمونه‌ی تست کشش، هم برای ورودی ثبت و هم برای خروجی نمایش. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TensileSampleDto {
    private Integer sampleIndex;

    @Size(max = 20, message = "شماره نمونه حداکثر ۲۰ کاراکتر است")
    private String sampleLabel;

    @DecimalMin(value = "0", message = "UTS نمی‌تواند منفی باشد")
    private BigDecimal uts;

    @DecimalMin(value = "0", message = "YS نمی‌تواند منفی باشد")
    private BigDecimal ys;

    @DecimalMin(value = "0", message = "EL نمی‌تواند منفی باشد")
    private BigDecimal el;

    @DecimalMin(value = "0", message = "HB نمی‌تواند منفی باشد")
    private BigDecimal hb;

    @Size(max = 500, message = "توضیحات حداکثر ۵۰۰ کاراکتر است")
    private String description;
}

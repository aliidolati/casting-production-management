package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WeldingMapMarkRequest {
    @NotNull(message = "مختصات افقی الزامی است")
    @DecimalMin(value = "0.0", message = "مختصات افقی باید بین ۰ و ۱ باشد")
    @DecimalMax(value = "1.0", message = "مختصات افقی باید بین ۰ و ۱ باشد")
    private Double xPercent;
    private java.util.UUID drawingRevisionId;

    @NotNull(message = "مختصات عمودی الزامی است")
    @DecimalMin(value = "0.0", message = "مختصات عمودی باید بین ۰ و ۱ باشد")
    @DecimalMax(value = "1.0", message = "مختصات عمودی باید بین ۰ و ۱ باشد")
    private Double yPercent;

    @NotBlank(message = "نوع عیب نمی‌تواند خالی باشد")
    private String defectType;

    private String defectTypeCustom;

    private String note;
}

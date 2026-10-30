package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdatePieceDefinitionRequest {
    @NotNull(message = "شماره ردیف نمی‌تواند خالی باشد")
    private Integer rowNum;

    @NotBlank(message = "نام قطعه نمی‌تواند خالی باشد")
    private String pieceName;

    @DecimalMin(value = "0", message = "حداقل UTS نمی‌تواند منفی باشد")
    private BigDecimal minUts;

    @DecimalMin(value = "0", message = "حداقل YS نمی‌تواند منفی باشد")
    private BigDecimal minYs;

    @DecimalMin(value = "0", message = "حداقل EL نمی‌تواند منفی باشد")
    private BigDecimal minEl;

    @DecimalMin(value = "0", message = "حداقل HB نمی‌تواند منفی باشد")
    private BigDecimal minHb;
}

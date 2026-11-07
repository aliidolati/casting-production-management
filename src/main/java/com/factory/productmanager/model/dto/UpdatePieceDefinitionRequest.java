package com.factory.productmanager.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

    @DecimalMin(value = "0", message = "زمان شوت نمی‌تواند منفی باشد")
    @Digits(integer = 8, fraction = 2, message = "زمان شوت حداکثر دو رقم اعشار می‌پذیرد")
    private BigDecimal radiographyShotHours;

    @DecimalMin(value = "0", message = "زمان تفسیر نمی‌تواند منفی باشد")
    @Digits(integer = 8, fraction = 2, message = "زمان تفسیر حداکثر دو رقم اعشار می‌پذیرد")
    private BigDecimal radiographyInterpretationHours;

    // Older clients omit these fields. An explicit null clears a saved duration.
    @JsonIgnore
    private boolean radiographyShotHoursSpecified;
    @JsonIgnore
    private boolean radiographyInterpretationHoursSpecified;

    public void setRadiographyShotHours(BigDecimal hours) {
        this.radiographyShotHours = hours;
        this.radiographyShotHoursSpecified = true;
    }

    public void setRadiographyInterpretationHours(BigDecimal hours) {
        this.radiographyInterpretationHours = hours;
        this.radiographyInterpretationHoursSpecified = true;
    }
}

package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.FurnaceSize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class UpdateHeatTreatmentBatchRequest {
    @NotNull(message = "تاریخ عملیات نمی‌تواند خالی باشد")
    private LocalDate treatmentDate;
    @NotNull private Long version;
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=2000) private String reason;
    @NotNull private java.util.Set<java.util.UUID> confirmedPieceIds;

    private BigDecimal temperatureC;

    @DecimalMin(value = "0", message = "زمان نمی‌تواند منفی باشد")
    private BigDecimal durationHours;

    private FurnaceSize furnaceSize;

    private String notes;
}

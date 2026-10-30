package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BulkCreatePieceRequest {
    @NotNull(message = "شناسه تعریف قطعه نمی‌تواند خالی باشد")
    private UUID pieceDefinitionId;

    @NotNull(message = "شماره شروع نمی‌تواند خالی باشد")
    @Min(value = 1, message = "شماره شروع باید حداقل ۱ باشد")
    private Integer startNumber;

    @NotNull(message = "تعداد نمی‌تواند خالی باشد")
    @Min(value = 1, message = "تعداد باید حداقل ۱ باشد")
    @Max(value = 1000, message = "در هر بار حداکثر ۱۰۰۰ قطعه قابل ساخت است")
    private Integer quantity;
}

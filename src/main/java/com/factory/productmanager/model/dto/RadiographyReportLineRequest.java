package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RadiographyReportLineRequest {
    @NotNull(message = "شناسه‌ی لاین قالب نمی‌تواند خالی باشد")
    private UUID templateLineId;

    @Size(max = 50, message = "IQI REAL حداکثر ۵۰ کاراکتر است")
    private String iqiReal;

    /** null یا خالی یعنی برای این کورس هنوز نتیجه‌ای انتخاب نشده است */
    @Size(max = 20, message = "RESULT حداکثر ۲۰ کاراکتر است")
    private String result;
}

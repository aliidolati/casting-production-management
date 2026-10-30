package com.factory.productmanager.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MeltPassportCorrectionRequest(
        @NotNull(message="اطلاعات شناسنامه الزامی است") @Valid MeltPassportRequest data,
        @Size(max=2000,message="دلیل اصلاح باید حداکثر ۲۰۰۰ نویسه باشد") String reason,
        boolean calculateMagnesium) {}

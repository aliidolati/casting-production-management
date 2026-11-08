package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MeltPassportCorrectionRequest(
        @NotNull(message="اطلاعات شناسنامه الزامی است") @Valid MeltPassportRequest data,
        @Size(max=2000,message="دلیل اصلاح باید حداکثر ۲۰۰۰ نویسه باشد") String reason,
        boolean calculateMagnesium,
        PieceStage addedPiecesStage,
        ScrapRequest scrap) {
    public MeltPassportCorrectionRequest(MeltPassportRequest data, String reason, boolean calculateMagnesium) {
        this(data, reason, calculateMagnesium, null, null);
    }
}

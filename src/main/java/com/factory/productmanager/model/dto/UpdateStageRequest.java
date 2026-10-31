package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStageRequest {
    private ScrapRequest scrap;
    @NotNull(message = "وضعیت جدید نمی‌تواند خالی باشد")
    private PieceStage newStage ;
}

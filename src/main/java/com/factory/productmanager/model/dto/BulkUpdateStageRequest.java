package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BulkUpdateStageRequest {
    @NotEmpty(message = "باید حداقل یک قطعه انتخاب شود")
    private List<UUID> pieceIds;

    @NotNull(message = "وضعیت جدید نمی‌تواند خالی باشد")
    private PieceStage newStage;
}

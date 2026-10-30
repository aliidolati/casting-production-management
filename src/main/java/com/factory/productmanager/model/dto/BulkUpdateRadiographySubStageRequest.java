package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.RadiographySubStage;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BulkUpdateRadiographySubStageRequest {
    @NotEmpty(message = "باید حداقل یک قطعه انتخاب شود")
    private List<UUID> pieceIds;

    @NotNull(message = "زیرمرحله‌ی جدید نمی‌تواند خالی باشد")
    private RadiographySubStage radiographySubStage;
}

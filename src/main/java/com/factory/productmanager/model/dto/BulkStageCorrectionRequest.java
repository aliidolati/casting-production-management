package com.factory.productmanager.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BulkStageCorrectionRequest(
        @NotEmpty(message = "باید حداقل یک قطعه انتخاب شود") List<@NotNull UUID> pieceIds,
        @NotNull @Valid StageCorrectionTarget target
) {}

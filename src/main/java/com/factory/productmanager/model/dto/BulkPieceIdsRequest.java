package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BulkPieceIdsRequest {
    @NotEmpty(message = "باید حداقل یک قطعه انتخاب شود")
    private List<UUID> pieceIds;
}

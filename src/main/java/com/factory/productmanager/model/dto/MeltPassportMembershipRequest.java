package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record MeltPassportMembershipRequest(
        @NotNull Long version,
        @NotEmpty(message = "حداقل یک قطعه باید در شناسنامه باقی بماند") List<@NotNull UUID> pieceIds,
        PieceStage addedPiecesStage,
        ScrapRequest scrap) {
}

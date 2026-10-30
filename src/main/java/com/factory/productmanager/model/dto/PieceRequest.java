package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PieceRequest {
    @NotNull(message = "شناسه تعریف قطعه نمی‌تواند خالی باشد")
    private UUID pieceDefinitionId;

    @NotNull(message = "شماره قطعه نمی‌تواند خالی باشد")
    private Integer pieceNumber;
}

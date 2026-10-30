package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePieceRequest {
    @NotNull(message = "شماره قطعه نمی‌تواند خالی باشد")
    private Integer pieceNumber;
}

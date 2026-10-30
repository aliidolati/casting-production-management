package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateStageProcessDateRequest {
    @NotNull
    private PieceStage stage;
    private LocalDate processDate;
}

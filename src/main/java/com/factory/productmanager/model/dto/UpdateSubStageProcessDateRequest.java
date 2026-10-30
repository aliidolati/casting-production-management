package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.SubStageKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateSubStageProcessDateRequest {
    @NotNull
    private SubStageKind kind;
    @NotBlank
    private String subStage;
    private LocalDate processDate;
}

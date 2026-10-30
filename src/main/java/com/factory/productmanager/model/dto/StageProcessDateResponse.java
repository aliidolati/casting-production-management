package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StageProcessDateResponse {
    private PieceStage stage;
    private LocalDate processDate;
}

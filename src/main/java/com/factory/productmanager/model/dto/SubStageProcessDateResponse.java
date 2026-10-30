package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.SubStageKind;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubStageProcessDateResponse {
    private SubStageKind kind;
    private String subStage;
    private LocalDate processDate;
}

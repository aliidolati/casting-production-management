package com.factory.productmanager.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateProcessDateRequest {
    private LocalDate processDate;
}

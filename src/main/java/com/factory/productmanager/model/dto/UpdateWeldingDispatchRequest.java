package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class UpdateWeldingDispatchRequest {
    @NotBlank(message = "نوع عیب را انتخاب کنید")
    private String defectType;
    private String defectTypeCustom;
    private String notes;
    private List<UUID> defectIds = new ArrayList<>();
}

package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteWeldingRequest {
    @NotNull(message = "مقصد پس از جوشکاری نمی‌تواند خالی باشد")
    private WeldingCompleteDestination destination;
}

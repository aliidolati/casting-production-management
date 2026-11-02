package com.factory.productmanager.model.dto;
import jakarta.validation.constraints.*;
public record CancelWeldingRequest(@NotBlank @Size(max=2000) String reason,@NotNull @AssertTrue Boolean noWorkConfirmed) {}

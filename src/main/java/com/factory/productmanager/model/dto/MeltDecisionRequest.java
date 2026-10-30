package com.factory.productmanager.model.dto;
import jakarta.validation.constraints.*;
public record MeltDecisionRequest(@NotNull(message="این مقدار الزامی است") Long version, @Size(max=2000,message="طول یا تعداد داده باید حداکثر {max} باشد") String reason) {}

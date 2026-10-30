package com.factory.productmanager.model.dto;
import jakarta.validation.constraints.*;
public record MeltMaterialRequest(@NotBlank(message="این فیلد نباید خالی باشد") @Size(max=160,message="طول یا تعداد داده باید حداکثر {max} باشد") String name, @NotBlank(message="این فیلد نباید خالی باشد") @Size(max=100,message="طول یا تعداد داده باید حداکثر {max} باشد") String category) {}

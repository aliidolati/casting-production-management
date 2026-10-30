package com.factory.productmanager.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** ثبت یک تست کشش جدید توسط اپراتور. */
@Getter
@Setter
public class CreateTensileTestRequest {
    @Valid
    @NotEmpty(message = "حداقل یک نمونه باید ثبت شود")
    @Size(max = 10, message = "حداکثر ۱۰ نمونه در هر تست قابل ثبت است")
    private List<TensileSampleDto> samples = new ArrayList<>();
}

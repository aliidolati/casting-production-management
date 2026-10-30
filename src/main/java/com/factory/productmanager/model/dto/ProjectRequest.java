package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectRequest {
    @NotBlank(message = "کد پروژه نمی‌تواند خالی باشد")
    private String code;

    @NotBlank(message = "نام پروژه نمی‌تواند خالی باشد")
    private String name;
}

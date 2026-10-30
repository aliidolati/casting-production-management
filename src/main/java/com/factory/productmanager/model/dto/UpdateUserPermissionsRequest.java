package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.Permission;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdateUserPermissionsRequest {
    @NotNull(message = "لیست دسترسی‌ها نمی‌تواند خالی باشد")
    private Set<Permission> permissions;
}

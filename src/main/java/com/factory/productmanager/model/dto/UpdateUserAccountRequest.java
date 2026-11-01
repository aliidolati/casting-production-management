package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.Role;
import com.factory.productmanager.model.entity.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Username is immutable. Role and explicit grants change atomically. */
public record UpdateUserAccountRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotNull Role role,
        @NotNull Set<@NotNull Permission> permissions) {}

package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.Role;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserDirectoryResponse(UUID id, String username, String fullName, Role role,
                                    LocalDateTime createdAt, boolean active) {}

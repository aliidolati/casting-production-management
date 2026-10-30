package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MeltAssignmentRequest(@NotNull UUID assigneeId, Long version) {}

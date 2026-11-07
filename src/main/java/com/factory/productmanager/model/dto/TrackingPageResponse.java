package com.factory.productmanager.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

public record TrackingPageResponse(
        List<PieceResponse> items,
        long totalElements,
        int page,
        int size,
        int totalPages,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal radiographyBacklogHours
) {}

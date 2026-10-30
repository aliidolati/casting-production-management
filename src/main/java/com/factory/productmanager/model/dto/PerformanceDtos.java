package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public final class PerformanceDtos {
    private PerformanceDtos() {}

    public record CreatePeriod(@NotBlank @Size(max = 160) String title) {}
    public record NextPeriod(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 160) String title) {}
    public record CriterionWrite(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal maximumScore,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal weight,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal score,
            @Size(max = 2000) String reason) {}
    public record CriterionDelete(@NotNull @PositiveOrZero Long version, @Size(max = 2000) String reason) {}
    public record Criterion(UUID id, String title, String description, BigDecimal maximumScore,
                            BigDecimal weight, BigDecimal score) {}
    public record PeriodSummary(UUID id, String title, String status, long version,
                                LocalDateTime createdAt, LocalDateTime closedAt) {}
    public record Period(UUID id, String title, String status, long version,
                         LocalDateTime createdAt, LocalDateTime closedAt, LocalDateTime updatedAt,
                         List<Criterion> criteria, BigDecimal weightedScore, BigDecimal weightedMaximum,
                         BigDecimal efficiency, boolean canManage, boolean canStartNext) {}
    public record Event(UUID id, UUID criterionId, String action, UUID actorId, String actorUsername,
                        String actorName, LocalDateTime occurredAt, String reason,
                        String oldValues, String newValues, BigDecimal oldEfficiency, BigDecimal newEfficiency) {}
}

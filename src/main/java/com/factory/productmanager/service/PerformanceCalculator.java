package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.PerformanceCriterion;
import java.math.*;
import java.util.List;

public final class PerformanceCalculator {
    private PerformanceCalculator() {}
    public record Result(BigDecimal weightedScore, BigDecimal weightedMaximum, BigDecimal efficiency) {}
    public static Result calculate(List<PerformanceCriterion> criteria) {
        BigDecimal earned = BigDecimal.ZERO, maximum = BigDecimal.ZERO;
        for (var c : criteria) {
            if (c.getDeletedAt() != null) continue;
            earned = earned.add(c.getScore().multiply(c.getWeight()));
            maximum = maximum.add(c.getMaximumScore().multiply(c.getWeight()));
        }
        return new Result(earned, maximum, maximum.signum() == 0 ? null : earned.divide(maximum, 4, RoundingMode.HALF_UP));
    }
}

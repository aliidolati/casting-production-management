package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.PerformanceCriterion;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceCalculatorTest {
    private PerformanceCriterion criterion(String maximum, String weight, String score) {
        var c = new PerformanceCriterion(); c.setMaximumScore(new BigDecimal(maximum));
        c.setWeight(new BigDecimal(weight)); c.setScore(new BigDecimal(score)); return c;
    }
    private void efficiency(String expected, PerformanceCriterion... rows) {
        assertEquals(new BigDecimal(expected), PerformanceCalculator.calculate(List.of(rows)).efficiency());
    }
    @Test void weightedRawPointsMatchAcceptedExample() { efficiency("0.8500", criterion("50", "3", "40"), criterion("50", "1", "50")); }
    @Test void singleCriterionWeightCancels() { efficiency("0.8000", criterion("50", "3", "40")); }
    @Test void differentMaximaAlsoAffectWeight() { efficiency("0.4000", criterion("100", "3", "40"), criterion("50", "1", "20")); }
    @Test void usesHalfUpOnlyOnFinalRatio() { efficiency("0.6667", criterion("3", "0.33", "2")); }
    @Test void decimalsAreMultipliedExactly() {
        var result = PerformanceCalculator.calculate(List.of(criterion("0.03", "0.03", "0.01")));
        assertEquals(new BigDecimal("0.0003"), result.weightedScore());
        assertEquals(new BigDecimal("0.0009"), result.weightedMaximum());
        assertEquals(new BigDecimal("0.3333"), result.efficiency());
    }
    @Test void handlesZeroFullEmptyAndDeleted() {
        efficiency("0.0000", criterion("50", "3", "0")); efficiency("1.0000", criterion("50", "3", "50"));
        assertNull(PerformanceCalculator.calculate(List.of()).efficiency());
        var deleted = criterion("50", "3", "50"); deleted.setDeletedAt(LocalDateTime.now());
        assertNull(PerformanceCalculator.calculate(List.of(deleted)).efficiency());
    }
}

package com.factory.productmanager.service.trend;

import com.factory.productmanager.model.entity.PieceDefinition;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * پارامترهای تست کشش که در گزارش روند تحلیل می‌شوند. افزودن پارامتر جدید (مثلاً HB)
 * فقط یک مقدار جدید در این enum می‌خواهد؛ منطق تحلیل برای همه یکی است.
 */
public enum TensileParameter {
    UTS("MPa", TensileTrendSampleRow::uts, PieceDefinition::getMinUts),
    YS("MPa", TensileTrendSampleRow::ys, PieceDefinition::getMinYs),
    EL("%", TensileTrendSampleRow::el, PieceDefinition::getMinEl);

    private final String unit;
    private final Function<TensileTrendSampleRow, BigDecimal> valueExtractor;
    private final Function<PieceDefinition, BigDecimal> minLimitExtractor;

    TensileParameter(
            String unit,
            Function<TensileTrendSampleRow, BigDecimal> valueExtractor,
            Function<PieceDefinition, BigDecimal> minLimitExtractor
    ) {
        this.unit = unit;
        this.valueExtractor = valueExtractor;
        this.minLimitExtractor = minLimitExtractor;
    }

    public String unit() {
        return unit;
    }

    public BigDecimal valueOf(TensileTrendSampleRow row) {
        return valueExtractor.apply(row);
    }

    public BigDecimal minLimitOf(PieceDefinition definition) {
        return minLimitExtractor.apply(definition);
    }
}

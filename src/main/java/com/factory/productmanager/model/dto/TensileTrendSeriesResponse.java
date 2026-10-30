package com.factory.productmanager.model.dto;

import com.factory.productmanager.service.trend.TrendConfidence;
import com.factory.productmanager.service.trend.TrendDirection;
import com.factory.productmanager.service.trend.TrendInsufficientReason;

import java.util.List;

/**
 * نتیجه‌ی تحلیل روند برای یک پارامتر (UTS / YS / EL). ساختار برای هر سه پارامتر یکسان است.
 *
 * @param slope                   شیب خط روند بر حسب «واحد پارامتر در روز» ({@code slopeUnit})
 * @param normalizedChangePercent تغییر کل خط روند در بازه به‌صورت درصد از میانگین؛ معیار تشخیص Stable
 * @param stableThresholdPercent  آستانه‌ی Stable که برای این محاسبه به‌کار رفته
 */
public record TensileTrendSeriesResponse(
        String parameter,
        String unit,
        int pointCount,
        List<TensileTrendPointResponse> points,
        Double average,
        Double firstValue,
        Double lastValue,
        Double percentageChange,
        Double slope,
        String slopeUnit,
        Double r2,
        Double normalizedChangePercent,
        double stableThresholdPercent,
        TrendDirection trend,
        TrendConfidence confidence,
        TrendInsufficientReason insufficientReason,
        int outlierCount,
        TensileTrendLimitsResponse limits
) {
}

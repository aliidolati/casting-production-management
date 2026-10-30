package com.factory.productmanager.config;

import com.factory.productmanager.service.trend.TrendConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * تنظیمات مرکزی گزارش «روند تست کشش» ({@code app.tensile-trend.*} در application.properties).
 * همه‌ی آستانه‌ها اینجا تعریف می‌شوند و در منطق تحلیل ثابت نیستند.
 */
@Component
@ConfigurationProperties(prefix = "app.tensile-trend")
@Getter
@Setter
public class TensileTrendProperties {

    /** آستانه‌ی Stable: تغییر کل خط روند در بازه (درصد از میانگین) کمتر از این یعنی روند ثابت */
    private double stableThresholdPercent = 2.0;

    /**
     * بازنویسی آستانه‌ی Stable برای هر پارامتر (کلید: UTS / YS / EL)؛ پارامتر بدون مقدار،
     * از {@link #stableThresholdPercent} استفاده می‌کند. EL معمولاً نوسان نسبی بیشتری دارد.
     */
    private Map<String, Double> stableThresholdPercentByParameter = new HashMap<>();

    private double strongR2 = 0.7;
    private double moderateR2 = 0.4;

    /** با داده‌ی کمتر از این تعداد، اطمینان به روند بالا/متوسط اعلام نمی‌شود */
    private int minReliablePoints = 5;

    private int outlierMinPoints = 5;
    private double outlierModifiedZ = 3.5;

    /** سقف تعداد نمونه در یک اجرای گزارش؛ بیشتر از آن باید بازه کوچک‌تر شود */
    private int maxPoints = 5000;

    /** پنجره‌ی پیش‌فرض میانگین متحرک */
    private int defaultMovingAverageWindow = 3;

    public TrendConfig toConfig(String parameterKey) {
        double threshold = stableThresholdPercentByParameter.getOrDefault(parameterKey, stableThresholdPercent);
        return new TrendConfig(threshold, strongR2, moderateR2, minReliablePoints, outlierMinPoints, outlierModifiedZ);
    }
}

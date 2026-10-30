package com.factory.productmanager.trend;

import com.factory.productmanager.service.trend.TrendAnalyzer;
import com.factory.productmanager.service.trend.TrendConfidence;
import com.factory.productmanager.service.trend.TrendConfig;
import com.factory.productmanager.service.trend.TrendDirection;
import com.factory.productmanager.service.trend.TrendInsufficientReason;
import com.factory.productmanager.service.trend.TrendPoint;
import com.factory.productmanager.service.trend.TrendResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** تست واحد منطق تحلیل روند (بدون Spring و دیتابیس). */
class TrendAnalyzerTest {

    private static final TrendConfig CONFIG = new TrendConfig(2.0, 0.7, 0.4, 5, 5, 3.5);

    private static List<TrendPoint> points(double[] days, double[] values) {
        List<TrendPoint> list = new ArrayList<>();
        for (int i = 0; i < days.length; i++) {
            list.add(new TrendPoint(days[i], values[i]));
        }
        return list;
    }

    /** روزهای متوالی ۰..n-1 */
    private static List<TrendPoint> daily(double... values) {
        double[] days = new double[values.length];
        for (int i = 0; i < days.length; i++) {
            days[i] = i;
        }
        return points(days, values);
    }

    // سناریو ۱
    @Test
    void clearlyIncreasingSeriesIsIncreasing() {
        TrendResult r = TrendAnalyzer.analyze(daily(300, 304, 309, 313, 318, 322, 327, 331), CONFIG);
        assertEquals(TrendDirection.INCREASING, r.direction());
        assertTrue(r.slopePerDay() > 0);
        assertTrue(r.r2() > 0.95);
        assertEquals(TrendConfidence.HIGH, r.confidence());
        assertEquals(300.0, r.firstValue());
        assertEquals(331.0, r.lastValue());
        assertEquals((331.0 - 300.0) / 300.0 * 100.0, r.percentageChange(), 1e-9);
    }

    // سناریو ۲
    @Test
    void clearlyDecreasingSeriesIsDecreasing() {
        TrendResult r = TrendAnalyzer.analyze(daily(331, 327, 322, 318, 313, 309, 304, 300), CONFIG);
        assertEquals(TrendDirection.DECREASING, r.direction());
        assertTrue(r.slopePerDay() < 0);
        assertTrue(r.percentageChange() < 0);
    }

    // سناریو ۳
    @Test
    void smallFluctuationsAroundConstantLevelAreStable() {
        TrendResult r = TrendAnalyzer.analyze(daily(300, 301, 299, 300.5, 299.5, 300, 301, 299.5), CONFIG);
        assertEquals(TrendDirection.STABLE, r.direction());
    }

    @Test
    void identicalValuesAreStableWithUndefinedR2() {
        TrendResult r = TrendAnalyzer.analyze(daily(5, 5, 5, 5), CONFIG);
        assertEquals(TrendDirection.STABLE, r.direction());
        assertEquals(0.0, r.slopePerDay(), 1e-12);
        assertNull(r.r2());
    }

    /** آخرین مقدار بیش از آستانه بالاتر از اولین است، ولی روند کلی ثابت است؛ جهت نباید فقط از first/last بیاید. */
    @Test
    void directionIsNotDerivedFromFirstAndLastValueOnly() {
        TrendResult r = TrendAnalyzer.analyze(daily(305, 300, 310, 295, 300, 305, 298, 312), CONFIG);
        assertTrue(r.percentageChange() > CONFIG.stableThresholdPercent());
        assertEquals(TrendDirection.STABLE, r.direction());
        assertTrue(r.r2() < 0.1);
    }

    // سناریو ۴
    @Test
    void singlePointIsInsufficient() {
        TrendResult r = TrendAnalyzer.analyze(daily(310), CONFIG);
        assertEquals(TrendDirection.INSUFFICIENT_DATA, r.direction());
        assertEquals(TrendInsufficientReason.SINGLE_POINT, r.insufficientReason());
        assertNull(r.slopePerDay());
        assertNull(r.r2());
        assertEquals(310.0, r.firstValue());
        assertEquals(310.0, r.lastValue());
        assertTrue(r.fittedValues().isEmpty());
    }

    @Test
    void emptySeriesIsInsufficient() {
        TrendResult r = TrendAnalyzer.analyze(List.of(), CONFIG);
        assertEquals(TrendDirection.INSUFFICIENT_DATA, r.direction());
        assertEquals(TrendInsufficientReason.NO_DATA, r.insufficientReason());
        assertEquals(0, r.count());
    }

    @Test
    void pointsAtSameInstantHaveNoTimeSpread() {
        TrendResult r = TrendAnalyzer.analyze(points(new double[]{7, 7}, new double[]{300, 320}), CONFIG);
        assertEquals(TrendDirection.INSUFFICIENT_DATA, r.direction());
        assertEquals(TrendInsufficientReason.NO_TIME_SPREAD, r.insufficientReason());
    }

    @Test
    void twoPointsGiveTrendWithLowConfidence() {
        TrendResult r = TrendAnalyzer.analyze(daily(300, 330), CONFIG);
        assertEquals(TrendDirection.INCREASING, r.direction());
        assertEquals(30.0, r.slopePerDay(), 1e-9);
        assertEquals(TrendConfidence.LOW, r.confidence());
    }

    // سناریو ۵
    @Test
    void slopeUsesRealDatesNotRowIndex() {
        // مقدار هر روز ۲ واحد زیاد می‌شود، ولی نمونه‌ها در روزهای نامنظم ثبت شده‌اند
        double[] days = {0, 1, 2, 10, 11, 30};
        double[] values = new double[days.length];
        for (int i = 0; i < days.length; i++) {
            values[i] = 300 + 2 * days[i];
        }
        TrendResult r = TrendAnalyzer.analyze(points(days, values), CONFIG);
        assertEquals(2.0, r.slopePerDay(), 1e-9);
        assertEquals(1.0, r.r2(), 1e-9);

        // اگر به‌جای تاریخ از شماره ردیف استفاده می‌شد، شیب چیز دیگری می‌شد
        TrendResult byIndex = TrendAnalyzer.analyze(daily(values), CONFIG);
        assertNotEquals(2.0, byIndex.slopePerDay(), 0.1);
    }

    // سناریو ۶
    @Test
    void parametersAreAnalysedIndependently() {
        TrendResult uts = TrendAnalyzer.analyze(daily(300, 304, 309, 313, 318, 322), CONFIG);
        TrendResult el = TrendAnalyzer.analyze(daily(9.0, 8.6, 8.1, 7.7, 7.2, 6.8), CONFIG);
        assertEquals(TrendDirection.INCREASING, uts.direction());
        assertEquals(TrendDirection.DECREASING, el.direction());
    }

    @Test
    void positiveSlopeWithLowR2DoesNotClaimStrongTrend() {
        // شیب مثبت دارد ولی نوسان بسیار زیاد است
        TrendResult r = TrendAnalyzer.analyze(daily(100, 160, 90, 170, 110, 180, 95, 175, 130, 190), CONFIG);
        assertEquals(TrendDirection.INCREASING, r.direction());
        assertTrue(r.r2() < 0.4);
        assertEquals(TrendConfidence.LOW, r.confidence());
    }

    @Test
    void stableThresholdIsConfigurable() {
        List<TrendPoint> pts = daily(100, 101, 102, 103, 104, 105); // ~5% تغییر خط روند
        assertEquals(TrendDirection.INCREASING, TrendAnalyzer.analyze(pts, CONFIG).direction());
        TrendConfig loose = CONFIG.withStableThresholdPercent(10.0);
        assertEquals(TrendDirection.STABLE, TrendAnalyzer.analyze(pts, loose).direction());
    }

    @Test
    void outliersAreFlaggedButNeverRemoved() {
        List<TrendPoint> pts = daily(300, 301, 300, 302, 301, 450, 300, 301, 302, 300);
        TrendResult r = TrendAnalyzer.analyze(pts, CONFIG);
        assertEquals(10, r.count());
        assertEquals(10, r.outliers().size());
        assertTrue(r.outliers().get(5));
        assertEquals(1, r.outliers().stream().filter(Boolean::booleanValue).count());
        // داده‌ی پرت در میانگین هم حساب شده است
        assertEquals((300 + 301 + 300 + 302 + 301 + 450 + 300 + 301 + 302 + 300) / 10.0, r.average(), 1e-9);
    }

    @Test
    void movingAverageIsTrailingAndNeedsFullWindow() {
        List<Double> ma = TrendAnalyzer.movingAverage(List.of(1.0, 2.0, 3.0, 4.0, 5.0), 3);
        assertNull(ma.get(0));
        assertNull(ma.get(1));
        assertEquals(2.0, ma.get(2), 1e-12);
        assertEquals(3.0, ma.get(3), 1e-12);
        assertEquals(4.0, ma.get(4), 1e-12);
    }

    @Test
    void movingAverageIsEmptyWhenDataIsShorterThanWindow() {
        List<Double> ma = TrendAnalyzer.movingAverage(List.of(1.0, 2.0), 3);
        assertEquals(2, ma.size());
        assertTrue(ma.stream().allMatch(v -> v == null));
    }
}

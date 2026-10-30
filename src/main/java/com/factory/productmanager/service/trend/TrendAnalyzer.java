package com.factory.productmanager.service.trend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * تحلیل روند عمومی برای هر سری زمانی عددی (UTS، YS، EL یا هر پارامتر دیگری در آینده).
 *
 * <ul>
 *   <li>روند با رگرسیون خطی کمترین مربعات روی «زمان واقعی» (نه شماره‌ی ردیف) محاسبه می‌شود؛
 *       پس داده‌های نامنظم از نظر تاریخ درست وزن می‌گیرند.</li>
 *   <li>جهت روند از «تغییر کل خط روند در طول بازه نسبت به میانگین» و آستانه‌ی Stable تعیین می‌شود،
 *       نه از مقایسه‌ی اولین و آخرین مقدار.</li>
 *   <li>داده‌ی پرت فقط علامت‌گذاری می‌شود؛ هیچ داده‌ای از محاسبات حذف نمی‌شود.</li>
 * </ul>
 *
 * کلاس بی‌حالت و مستقل از Spring/JPA است تا مستقیم قابل تست باشد.
 */
public final class TrendAnalyzer {

    /** کمتر از این پراکندگی زمانی (روز) یعنی همه‌ی نقطه‌ها عملاً در یک لحظه‌اند (~۰٫۰۸ ثانیه) */
    private static final double MIN_TIME_SPREAD_DAYS = 1e-6;
    /** ضریب تبدیل MAD به برآورد انحراف معیار در توزیع نرمال (Iglewicz–Hoaglin) */
    private static final double MODIFIED_Z_FACTOR = 0.6745;

    private TrendAnalyzer() {
    }

    /**
     * @param points مشاهده‌ها؛ باید به ترتیب زمان (صعودی) باشند
     */
    public static TrendResult analyze(List<TrendPoint> points, TrendConfig config) {
        int n = points.size();
        if (n == 0) {
            return new TrendResult(0, null, null, null, null, null, null, null,
                    TrendDirection.INSUFFICIENT_DATA, TrendConfidence.NONE,
                    TrendInsufficientReason.NO_DATA, List.of(), List.of());
        }

        double sumY = 0;
        for (TrendPoint p : points) {
            sumY += p.y();
        }
        double meanY = sumY / n;
        double first = points.get(0).y();
        double last = points.get(n - 1).y();
        Double percentageChange = first == 0 ? null : (last - first) / Math.abs(first) * 100.0;

        double xFirst = Double.POSITIVE_INFINITY;
        double xLast = Double.NEGATIVE_INFINITY;
        double sumX = 0;
        for (TrendPoint p : points) {
            xFirst = Math.min(xFirst, p.x());
            xLast = Math.max(xLast, p.x());
            sumX += p.x();
        }
        double spread = xLast - xFirst;

        if (n == 1 || spread < MIN_TIME_SPREAD_DAYS) {
            TrendInsufficientReason reason = n == 1
                    ? TrendInsufficientReason.SINGLE_POINT
                    : TrendInsufficientReason.NO_TIME_SPREAD;
            List<Boolean> outliers = detectOutliers(residualsAgainstMean(points, meanY), config);
            return new TrendResult(n, meanY, first, last, n == 1 ? null : percentageChange, null, null, null,
                    TrendDirection.INSUFFICIENT_DATA, TrendConfidence.NONE, reason, List.of(), outliers);
        }

        // رگرسیون خطی روی مختصات مرکزی‌شده (پایداری عددی بهتر با x بزرگ مثل epoch-day)
        double meanX = sumX / n;
        double sxx = 0;
        double sxy = 0;
        double syy = 0;
        for (TrendPoint p : points) {
            double dx = p.x() - meanX;
            double dy = p.y() - meanY;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
        }
        double slope = sxy / sxx;
        // اگر همه‌ی مقادیر برابرند، R² تعریف‌نشده است (تقسیم بر صفر)
        Double r2 = syy <= 0 ? null : Math.min(1.0, Math.max(0.0, (sxy * sxy) / (sxx * syy)));

        List<Double> fitted = new ArrayList<>(n);
        double[] residuals = new double[n];
        for (int i = 0; i < n; i++) {
            TrendPoint p = points.get(i);
            double f = meanY + slope * (p.x() - meanX);
            fitted.add(f);
            residuals[i] = p.y() - f;
        }

        Double normalizedChange = meanY == 0 ? null : slope * spread / Math.abs(meanY) * 100.0;
        TrendDirection direction = resolveDirection(slope, normalizedChange, config);
        TrendConfidence confidence = resolveConfidence(direction, n, r2, config);

        return new TrendResult(n, meanY, first, last, percentageChange, slope, r2, normalizedChange,
                direction, confidence, null, Collections.unmodifiableList(fitted),
                detectOutliers(residuals, config));
    }

    /**
     * میانگین متحرک دنباله‌ای (trailing). برای n مقدار اول که پنجره کامل نیست null برمی‌گردد.
     * اگر پنجره نامعتبر یا بزرگ‌تر از تعداد داده باشد، همه null هستند (داده کافی نیست).
     */
    public static List<Double> movingAverage(List<Double> values, int window) {
        List<Double> result = new ArrayList<>(Collections.nCopies(values.size(), null));
        if (window < 2 || values.size() < window) {
            return result;
        }
        double windowSum = 0;
        for (int i = 0; i < values.size(); i++) {
            windowSum += values.get(i);
            if (i >= window) {
                windowSum -= values.get(i - window);
            }
            if (i >= window - 1) {
                result.set(i, windowSum / window);
            }
        }
        return result;
    }

    private static TrendDirection resolveDirection(double slope, Double normalizedChange, TrendConfig config) {
        if (slope == 0) {
            return TrendDirection.STABLE;
        }
        if (normalizedChange != null && Math.abs(normalizedChange) < config.stableThresholdPercent()) {
            return TrendDirection.STABLE;
        }
        return slope > 0 ? TrendDirection.INCREASING : TrendDirection.DECREASING;
    }

    private static TrendConfidence resolveConfidence(TrendDirection direction, int n, Double r2, TrendConfig config) {
        if (n < config.minReliablePoints()) {
            return TrendConfidence.LOW;
        }
        if (direction == TrendDirection.STABLE) {
            // R² برای داده‌ی بدون روند طبیعتاً پایین است؛ فقط به حجم داده تکیه می‌کنیم
            return TrendConfidence.MEDIUM;
        }
        if (r2 == null || r2 < config.moderateR2()) {
            return TrendConfidence.LOW;
        }
        return r2 >= config.strongR2() ? TrendConfidence.HIGH : TrendConfidence.MEDIUM;
    }

    private static double[] residualsAgainstMean(List<TrendPoint> points, double mean) {
        double[] residuals = new double[points.size()];
        for (int i = 0; i < residuals.length; i++) {
            residuals[i] = points.get(i).y() - mean;
        }
        return residuals;
    }

    /**
     * داده‌ی پرت با modified z-score روی باقیمانده‌ها (نسبت به خط روند) شناسایی می‌شود.
     * استفاده از باقیمانده باعث می‌شود نقطه‌ی عادیِ یک روند شدید پرت حساب نشود.
     */
    private static List<Boolean> detectOutliers(double[] residuals, TrendConfig config) {
        int n = residuals.length;
        List<Boolean> flags = new ArrayList<>(Collections.nCopies(n, Boolean.FALSE));
        if (n < Math.max(config.outlierMinPoints(), 3)) {
            return flags;
        }
        double median = median(residuals);
        double[] deviations = new double[n];
        for (int i = 0; i < n; i++) {
            deviations[i] = Math.abs(residuals[i] - median);
        }
        double mad = median(deviations);
        if (mad <= 0) {
            return flags;
        }
        for (int i = 0; i < n; i++) {
            double modifiedZ = MODIFIED_Z_FACTOR * (residuals[i] - median) / mad;
            if (Math.abs(modifiedZ) > config.outlierModifiedZ()) {
                flags.set(i, Boolean.TRUE);
            }
        }
        return flags;
    }

    private static double median(double[] values) {
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[mid - 1] + sorted[mid]) / 2.0 : sorted[mid];
    }
}

package com.factory.productmanager.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Q = EL / (36 - 0.064 * YS)
 * آستانه سیستم Q = 0.25 با تلورانس 0.001 است.
 */
public final class TensileQualityIndex {

    public static final BigDecimal THRESHOLD = new BigDecimal("0.25");
    public static final BigDecimal TOLERANCE = new BigDecimal("0.001");

    private static final BigDecimal DENOMINATOR_BASE = new BigDecimal("36");
    private static final BigDecimal YS_COEFFICIENT = new BigDecimal("0.064");
    private static final int SCALE = 6;

    public enum Band {
        GREEN, YELLOW, RED, UNDEFINED
    }

    public record Evaluation(BigDecimal q, Band band) {
    }

    private TensileQualityIndex() {
    }

    /**
     * نمونه‌ای که EL یا YS ندارد قابل ارزیابی نیست و null برمی‌گردد.
     * اگر مخرج مثبت نباشد، Q تعریف نمی‌شود و باند UNDEFINED است.
     */
    public static Evaluation evaluate(BigDecimal el, BigDecimal ys) {
        if (el == null || ys == null) {
            return null;
        }
        BigDecimal denominator = DENOMINATOR_BASE.subtract(YS_COEFFICIENT.multiply(ys));
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return new Evaluation(null, Band.UNDEFINED);
        }
        BigDecimal q = el.divide(denominator, SCALE, RoundingMode.HALF_UP);
        BigDecimal upper = THRESHOLD.add(TOLERANCE);
        BigDecimal lower = THRESHOLD.subtract(TOLERANCE);
        Band band;
        if (q.compareTo(upper) > 0) {
            band = Band.GREEN;
        } else if (q.compareTo(lower) < 0) {
            band = Band.RED;
        } else {
            band = Band.YELLOW;
        }
        return new Evaluation(q, band);
    }
}

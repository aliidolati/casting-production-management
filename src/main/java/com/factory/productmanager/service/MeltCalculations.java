package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.MeltPassportRequest.MagnesiumInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

public final class MeltCalculations {
    private MeltCalculations() {}

    public static BigDecimal percent(BigDecimal weight, BigDecimal other) {
        BigDecimal sum = zero(weight).add(zero(other));
        return sum.signum() == 0 ? null : zero(weight).multiply(new BigDecimal("100")).divide(sum, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal magnesium(MagnesiumInput v) {
        if (v == null || Stream.of(v.getMg(), v.getRotary(), v.getAt610(), v.getAr(), v.getB(), v.getW()).anyMatch(x -> x == null)) {
            throw new IllegalArgumentException("هر شش ورودی محاسبه منیزیم را کامل کنید؛ ورودی خالی صفر نیست");
        }
        BigDecimal result = new BigDecimal("0.32").subtract(v.getMg())
                .add(v.getRotary().multiply(new BigDecimal("0.2")))
                .add(v.getAt610().add(v.getAr()).multiply(v.getB()))
                .multiply(v.getW()).multiply(BigDecimal.TEN).multiply(new BigDecimal("1.1"));
        if (result.precision() - result.scale() > 26) {
            throw new IllegalArgumentException("نتیجه منیزیم از محدوده ذخیره‌سازی بزرگ‌تر است");
        }
        return result.setScale(12, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}

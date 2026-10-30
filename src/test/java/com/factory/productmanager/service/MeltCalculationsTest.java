package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.MeltPassportRequest.MagnesiumInput;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MeltCalculationsTest {
    @Test void percentagesHandleDecimalWeightsMissingAndZeroTotal() {
        assertEquals(0,new BigDecimal("70").compareTo(MeltCalculations.percent(new BigDecimal("70"),new BigDecimal("30"))));
        assertEquals(0,new BigDecimal("25").compareTo(MeltCalculations.percent(new BigDecimal("0.125"),new BigDecimal("0.375"))));
        assertNull(MeltCalculations.percent(BigDecimal.ZERO,BigDecimal.ZERO));assertNull(MeltCalculations.percent(null,null));
    }
    @Test void magnesiumUsesExactConstantsAndDoesNotClampNegative() {
        var v=new MagnesiumInput(); v.setMg(new BigDecimal("0.1"));v.setRotary(new BigDecimal("2"));v.setAt610(new BigDecimal("3"));
        v.setAr(new BigDecimal("4"));v.setB(new BigDecimal("0.5"));v.setW(new BigDecimal("10"));
        assertEquals(0,new BigDecimal("453.2").compareTo(MeltCalculations.magnesium(v)));
        v.setMg(BigDecimal.TEN);assertTrue(MeltCalculations.magnesium(v).signum()<0);
        v.setAr(null);assertThrows(IllegalArgumentException.class,()->MeltCalculations.magnesium(v));
        v.setAr(BigDecimal.ZERO);assertDoesNotThrow(()->MeltCalculations.magnesium(v));
    }
}

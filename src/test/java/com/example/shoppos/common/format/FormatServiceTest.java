package com.example.shoppos.common.format;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatServiceTest {

    private final FormatService fmt = new FormatService("EGP");

    @Test
    void moneyUsesGroupingAndCurrencySuffix() {
        assertEquals("1,234.50 EGP", fmt.money(new BigDecimal("1234.50")));
        assertEquals("0.00 EGP", fmt.money(BigDecimal.ZERO));
        assertEquals("\u2014", fmt.money(null));
    }

    @Test
    void currencyIsExposedForFormLabels() {
        assertEquals("EGP", fmt.currency());
    }

    @Test
    void plainMoneyHasNoGrouping() {
        assertEquals("12345.60", fmt.moneyPlain(new BigDecimal("12345.60")));
    }
}

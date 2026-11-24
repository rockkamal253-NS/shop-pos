package com.example.shoppos.sale;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Pure money-math tests: totals, discounts, change calculation. No Spring context. */
class SalePricingTest {

    @Test
    void noDiscountMeansTotalEqualsSubtotal() {
        var totals = SalePricing.compute(new BigDecimal("100.00"), DiscountType.NONE, null, null);
        assertEquals("0.00", totals.discountAmount().toPlainString());
        assertEquals("100.00", totals.total().toPlainString());
        assertNull(totals.changeDue());
    }

    @Test
    void amountDiscountIsSubtracted() {
        var totals = SalePricing.compute(new BigDecimal("100.00"), DiscountType.AMOUNT, new BigDecimal("15.50"), null);
        assertEquals("15.50", totals.discountAmount().toPlainString());
        assertEquals("84.50", totals.total().toPlainString());
    }

    @Test
    void amountDiscountCannotExceedSubtotal() {
        var totals = SalePricing.compute(new BigDecimal("40.00"), DiscountType.AMOUNT, new BigDecimal("99.00"), null);
        assertEquals("40.00", totals.discountAmount().toPlainString());
        assertEquals("0.00", totals.total().toPlainString());
    }

    @Test
    void percentDiscountIsRoundedHalfUpToScale2() {
        // 100.00 * 12.5% = 12.50
        var totals = SalePricing.compute(new BigDecimal("100.00"), DiscountType.PERCENT, new BigDecimal("12.5"), null);
        assertEquals("12.50", totals.discountAmount().toPlainString());

        // 33.33 * 33% = 10.9989 -> 11.00
        var totals2 = SalePricing.compute(new BigDecimal("33.33"), DiscountType.PERCENT, new BigDecimal("33"), null);
        assertEquals("11.00", totals2.discountAmount().toPlainString());
        assertEquals("22.33", totals2.total().toPlainString());
    }

    @Test
    void changeIsTenderedMinusTotal() {
        var totals = SalePricing.compute(new BigDecimal("47.25"), DiscountType.NONE, null, new BigDecimal("50.00"));
        assertEquals("2.75", totals.changeDue().toPlainString());
    }

    @Test
    void negativeChangeMeansCustomerStillOwes() {
        var totals = SalePricing.compute(new BigDecimal("47.25"), DiscountType.NONE, null, new BigDecimal("40.00"));
        assertEquals("-7.25", totals.changeDue().toPlainString());
    }

    @Test
    void lineTotalIsUnitPriceTimesQuantity() {
        assertEquals("49.95", SalePricing.lineTotal(new BigDecimal("16.65"), 3).toPlainString());
        assertEquals("0.00", SalePricing.lineTotal(new BigDecimal("19.99"), 0).toPlainString());
    }

    @Test
    void nullDiscountValueIsTreatedAsZero() {
        var totals = SalePricing.compute(new BigDecimal("10.00"), DiscountType.AMOUNT, null, null);
        assertEquals("10.00", totals.total().toPlainString());
    }

    @Test
    void maxCashierDiscountCalculatesSubtotalTimesPercentDividedByHundred() {
        assertEquals("20.00", SalePricing.maxCashierDiscount(new BigDecimal("100.00"), 20).toPlainString());
        assertEquals("6.67", SalePricing.maxCashierDiscount(new BigDecimal("33.33"), 20).toPlainString());
        assertEquals("0.00", SalePricing.maxCashierDiscount(new BigDecimal("0"), 20).toPlainString());
    }
}

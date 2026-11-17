package com.example.shoppos.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure money math for sales. All amounts are BigDecimal scale 2 (HALF_UP) —
 * this class never touches the database, so it is trivially unit-testable.
 */
public final class SalePricing {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SalePricing() {
    }

    public record Totals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal total, BigDecimal changeDue) {
    }

    /**
     * @param subtotal sum of line totals
     * @param type     NONE / fixed AMOUNT / PERCENT
     * @param value    discount input (ignored for NONE)
     * @param tendered cash handed over (null for card payments)
     */
    public static Totals compute(BigDecimal subtotal, DiscountType type, BigDecimal value, BigDecimal tendered) {
        BigDecimal safeSubtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = (switch (type) {
            case NONE -> BigDecimal.ZERO;
            case AMOUNT -> clamp(value).min(safeSubtotal);
            case PERCENT -> safeSubtotal.multiply(clamp(value))
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP)
                    .min(safeSubtotal);
        }).setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = safeSubtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal change = tendered == null ? null
                : tendered.setScale(2, RoundingMode.HALF_UP).subtract(total);
        return new Totals(safeSubtotal, discount, total, change);
    }

    /** Line total: unit price x quantity, scale 2. */
    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal clamp(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.max(BigDecimal.ZERO);
    }
}

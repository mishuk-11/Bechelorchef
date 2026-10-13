package com.bachelorchef.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Linear portion and cost scaling. Stored values are never modified; results are computed from
 * the stored base values each time, and money is rounded once, to 2 decimals, for display.
 */
public final class PortionCalculator {

    public static final int MIN_SERVINGS = 1;
    public static final int MAX_SERVINGS = 20;
    private static final int QUANTITY_SCALE = 3;
    private static final int MONEY_SCALE = 2;

    private PortionCalculator() {
    }

    public static boolean isValidServings(Integer servings) {
        return servings != null && servings >= MIN_SERVINGS && servings <= MAX_SERVINGS;
    }

    /** quantity × target / base, at most 3 decimals, without trailing zeros. */
    public static BigDecimal scaleQuantity(BigDecimal quantity, int baseServings, int targetServings) {
        requireServings(baseServings, targetServings);
        BigDecimal scaled = quantity.multiply(BigDecimal.valueOf(targetServings))
                .divide(BigDecimal.valueOf(baseServings), QUANTITY_SCALE, RoundingMode.HALF_UP);
        return plain(scaled);
    }

    /** estimated_cost_bdt × n / base_servings, rounded half-up to 2 decimals. */
    public static BigDecimal scaledCost(BigDecimal baseCost, int baseServings, int targetServings) {
        requireServings(baseServings, targetServings);
        return baseCost.multiply(BigDecimal.valueOf(targetServings))
                .divide(BigDecimal.valueOf(baseServings), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Scaled total divided by the selected servings. Computed from the unrounded scaled total
     * (which simplifies to cost / base) so rounding is applied only once.
     */
    public static BigDecimal costPerServing(BigDecimal baseCost, int baseServings, int targetServings) {
        requireServings(baseServings, targetServings);
        BigDecimal unroundedTotal = baseCost.multiply(BigDecimal.valueOf(targetServings));
        return unroundedTotal.divide(BigDecimal.valueOf((long) baseServings * targetServings), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static void requireServings(int base, int target) {
        if (!isValidServings(base) || !isValidServings(target)) {
            throw new IllegalArgumentException("Servings must be between " + MIN_SERVINGS + " and " + MAX_SERVINGS);
        }
    }

    private static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}

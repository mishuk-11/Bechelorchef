package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bachelorchef.service.PortionCalculator;
import com.bachelorchef.service.TimeCategory;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TimeAndPortionTest {

    @ParameterizedTest(name = "total {0} -> {1}")
    @CsvSource({"1,EXPRESS", "15,EXPRESS", "16,BACHELOR_QUICK", "20,BACHELOR_QUICK", "21,EXTENDED", "60,EXTENDED"})
    @DisplayName("Time categories at the 15/16/20/21/60 boundaries")
    void categories(int total, TimeCategory expected) {
        assertThat(TimeCategory.of(total)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 61})
    void categoryRejectsOutOfRange(int total) {
        assertThatThrownBy(() -> TimeCategory.of(total)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labels() {
        assertThat(TimeCategory.BACHELOR_QUICK.label()).isEqualTo("Bachelor Quick");
    }

    @ParameterizedTest(name = "servings {0} valid={1}")
    @CsvSource({"0,false", "1,true", "20,true", "21,false"})
    @DisplayName("Portions BVA 0/1/20/21")
    void servingsBoundaries(int servings, boolean valid) {
        assertThat(PortionCalculator.isValidServings(servings)).isEqualTo(valid);
    }

    @Test
    void nullServingsInvalid() {
        assertThat(PortionCalculator.isValidServings(null)).isFalse();
    }

    @Test
    @DisplayName("scaled_cost = cost × n / base, rounded to 2 decimals")
    void scaledCost() {
        BigDecimal cost = new BigDecimal("80.00");
        assertThat(PortionCalculator.scaledCost(cost, 2, 4)).isEqualByComparingTo("160.00");
        assertThat(PortionCalculator.scaledCost(cost, 2, 1)).isEqualByComparingTo("40.00");
        assertThat(PortionCalculator.scaledCost(new BigDecimal("100.00"), 3, 1)).isEqualByComparingTo("33.33");
        assertThat(PortionCalculator.scaledCost(new BigDecimal("100.00"), 3, 2)).isEqualByComparingTo("66.67");
        assertThat(PortionCalculator.scaledCost(cost, 2, 4).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Per-serving cost is computed from the unrounded total (rounded once)")
    void perServing() {
        assertThat(PortionCalculator.costPerServing(new BigDecimal("100.00"), 3, 2)).isEqualByComparingTo("33.33");
        assertThat(PortionCalculator.costPerServing(new BigDecimal("80.00"), 2, 7)).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("Quantities scale linearly without trailing zeros or scientific notation")
    void quantities() {
        assertThat(PortionCalculator.scaleQuantity(new BigDecimal("2"), 2, 4).toPlainString()).isEqualTo("4");
        assertThat(PortionCalculator.scaleQuantity(new BigDecimal("0.5"), 2, 3).toPlainString()).isEqualTo("0.75");
        assertThat(PortionCalculator.scaleQuantity(new BigDecimal("1"), 3, 1).toPlainString()).isEqualTo("0.333");
        assertThat(PortionCalculator.scaleQuantity(new BigDecimal("50"), 1, 2).toPlainString()).isEqualTo("100");
    }

    @Test
    void storedValueUnchanged() {
        BigDecimal stored = new BigDecimal("80.00");
        PortionCalculator.scaledCost(stored, 2, 3);
        assertThat(stored).isEqualByComparingTo("80.00");
    }

    @Test
    void invalidServingsThrow() {
        assertThatThrownBy(() -> PortionCalculator.scaledCost(BigDecimal.TEN, 2, 21)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortionCalculator.scaleQuantity(BigDecimal.TEN, 0, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}

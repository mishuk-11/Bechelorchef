package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bachelorchef.entity.GoalCountMode;
import com.bachelorchef.service.GoalProgressCalculator;
import com.bachelorchef.service.GoalProgressCalculator.Criteria;
import com.bachelorchef.service.GoalProgressCalculator.HistoryEntry;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GoalProgressCalculatorTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private List<HistoryEntry> history;

    @BeforeEach
    void setUp() {
        history = new ArrayList<>(List.of(
                entry(1, "2026-10-02T12:00:00Z", 10, 2L),
                entry(1, "2026-10-05T12:00:00Z", 10, 2L),   // same recipe again
                entry(2, "2026-10-06T12:00:00Z", 25, 4L),
                entry(3, "2026-09-30T12:00:00Z", 10, 2L))); // previous month
    }

    static HistoryEntry entry(long recipe, String at, int time, Long tag) {
        return new HistoryEntry(recipe, Instant.parse(at), time, Set.of(tag));
    }

    @Test
    @DisplayName("UNIQUE_RECIPES counts distinct recipes; COOK_EVENTS counts every event")
    void modes() {
        assertThat(GoalProgressCalculator.progress(history, new Criteria(OCT, GoalCountMode.UNIQUE_RECIPES, null, null), DHAKA)).isEqualTo(2);
        assertThat(GoalProgressCalculator.progress(history, new Criteria(OCT, GoalCountMode.COOK_EVENTS, null, null), DHAKA)).isEqualTo(3);
    }

    @Test
    @DisplayName("Criteria: tag and max total time filters")
    void criteria() {
        assertThat(GoalProgressCalculator.progress(history, new Criteria(OCT, GoalCountMode.COOK_EVENTS, 2L, null), DHAKA)).isEqualTo(2);
        assertThat(GoalProgressCalculator.progress(history, new Criteria(OCT, GoalCountMode.COOK_EVENTS, null, 15), DHAKA)).isEqualTo(2);
        assertThat(GoalProgressCalculator.progress(history, new Criteria(OCT, GoalCountMode.UNIQUE_RECIPES, 4L, 15), DHAKA)).isZero();
    }

    @Test
    @DisplayName("Month boundaries are evaluated in Asia/Dhaka (UTC+6)")
    void monthScopeInLocalZone() {
        // 2026-09-30T18:30Z is 2026-10-01 00:30 in Dhaka -> counts for October
        // 2026-10-31T18:30Z is 2026-11-01 00:30 in Dhaka -> does not count for October
        List<HistoryEntry> edge = List.of(entry(7, "2026-09-30T18:30:00Z", 5, 1L), entry(8, "2026-10-31T18:30:00Z", 5, 1L),
                entry(9, "2026-09-30T17:59:59Z", 5, 1L));
        assertThat(GoalProgressCalculator.progress(edge, new Criteria(OCT, GoalCountMode.COOK_EVENTS, null, null), DHAKA)).isEqualTo(1);
    }

    @Test
    @DisplayName("Deleting or moving a history event changes progress (no drift)")
    void historyEditsAndDeletes() {
        Criteria c = new Criteria(OCT, GoalCountMode.UNIQUE_RECIPES, null, null);
        assertThat(GoalProgressCalculator.progress(history, c, DHAKA)).isEqualTo(2);
        history.remove(2); // delete the only recipe-2 event
        assertThat(GoalProgressCalculator.progress(history, c, DHAKA)).isEqualTo(1);
        history.set(0, entry(1, "2026-11-02T12:00:00Z", 10, 2L)); // move one recipe-1 event out of October
        assertThat(GoalProgressCalculator.progress(history, c, DHAKA)).isEqualTo(1); // second recipe-1 event remains
        history.remove(1);
        assertThat(GoalProgressCalculator.progress(history, c, DHAKA)).isZero();
    }

    @Test
    void completion() {
        assertThat(GoalProgressCalculator.isComplete(4, 5)).isFalse();
        assertThat(GoalProgressCalculator.isComplete(5, 5)).isTrue();
        assertThat(GoalProgressCalculator.isComplete(6, 5)).isTrue();
    }

    @ParameterizedTest(name = "target {0} valid={1}")
    @CsvSource({"0,false", "1,true", "30,true", "31,false"})
    @DisplayName("Goal target BVA 0/1/30/31")
    void target(int value, boolean valid) {
        assertThat(GoalProgressCalculator.isValidTarget(value)).isEqualTo(valid);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-00", "2026-1", "26-10", "2026/10", "October", ""})
    void invalidMonths(String month) {
        assertThat(GoalProgressCalculator.parseMonth(month)).isNull();
    }

    @Test
    void validMonth() {
        assertThat(GoalProgressCalculator.parseMonth("2026-10")).isEqualTo(OCT);
        assertThat(GoalProgressCalculator.parseMonth(null)).isNull();
    }
}

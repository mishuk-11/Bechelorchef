package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bachelorchef.dto.RecipeDtos;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.service.PantryMatcher;
import com.bachelorchef.service.PantryMatcher.Candidate;
import com.bachelorchef.service.PantryMatchService;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PantryMatcherTest {

    static Candidate c(long id, String title, int time, String rating, int required, int matched) {
        return new Candidate(id, title, time, new BigDecimal(rating), required, matched);
    }

    @Test
    @DisplayName("Strict mode returns only recipes with every required ingredient")
    void strict() {
        List<Candidate> ranked = PantryMatcher.rank(List.of(c(1, "Full", 10, "0", 3, 3), c(2, "Partial", 5, "5", 3, 2)), true);
        assertThat(ranked).extracting(Candidate::recipeId).containsExactly(1L);
    }

    @Test
    @DisplayName("Ranked mode includes partial matches but not 0% matches")
    void nonStrict() {
        List<Candidate> ranked = PantryMatcher.rank(List.of(c(1, "A", 10, "0", 3, 3), c(2, "B", 5, "5", 3, 2),
                c(3, "C", 5, "5", 3, 0)), false);
        assertThat(ranked).extracting(Candidate::recipeId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Recipes without required ingredients never appear")
    void noRequired() {
        assertThat(PantryMatcher.rank(List.of(c(1, "Only optional", 5, "0", 0, 0)), false)).isEmpty();
    }

    @Test
    @DisplayName("Order: ratio, then fewer missing, then shorter time, then rating, then title, then id")
    void tieBreakers() {
        List<Candidate> input = new ArrayList<>(List.of(
                c(10, "Half of four", 10, "4", 4, 2),   // 50%, missing 2
                c(11, "Half of two", 10, "4", 2, 1),    // 50%, missing 1 -> before 10
                c(12, "Full slow", 30, "1", 2, 2),      // 100%, 30 min
                c(13, "Full fast", 10, "1", 2, 2),      // 100%, 10 min -> first
                c(14, "Full fast b", 10, "4.5", 2, 2),  // same as 13 but higher rating -> before 13
                c(15, "alpha", 10, "4.5", 2, 2),        // same as 14 but title 'alpha' < 'full fast b'
                c(16, "alpha", 10, "4.5", 2, 2)));      // identical except id -> after 15
        Collections.shuffle(input);
        List<Long> ids = PantryMatcher.rank(input, false).stream().map(Candidate::recipeId).toList();
        assertThat(ids).containsExactly(15L, 16L, 14L, 13L, 12L, 11L, 10L);
    }

    @Test
    @DisplayName("Ordering is deterministic regardless of input order")
    void deterministic() {
        List<Candidate> a = Arrays.asList(c(1, "B", 10, "3", 3, 2), c(2, "A", 10, "3", 3, 2), c(3, "C", 5, "1", 2, 1));
        List<Candidate> b = new ArrayList<>(a);
        Collections.reverse(b);
        assertThat(PantryMatcher.rank(a, false)).isEqualTo(PantryMatcher.rank(b, false));
    }

    @ParameterizedTest(name = "{0}/{1} = {2}%")
    @CsvSource({"0,3,0", "1,3,33", "2,3,67", "3,3,100", "1,2,50", "5,6,83"})
    void percent(int matched, int required, int expected) {
        assertThat(PantryMatcher.matchPercent(matched, required)).isEqualTo(expected);
    }

    @Test
    void percentRejectsZeroRequired() {
        assertThatThrownBy(() -> PantryMatcher.matchPercent(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- request normalisation (PantryMatchService.normalise is package-private; accessed reflectively)

    private static Object normalise(RecipeDtos.MatchRequest request) throws Exception {
        Method m = PantryMatchService.class.getDeclaredMethod("normalise", RecipeDtos.MatchRequest.class);
        m.setAccessible(true);
        try {
            return m.invoke(null, request);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static RecipeDtos.MatchRequest req(List<Long> ids, Integer maxTime) {
        return new RecipeDtos.MatchRequest(ids, maxTime, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Empty pantry selection is rejected")
    void emptySelection() {
        assertThatThrownBy(() -> normalise(req(List.of(), null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> normalise(req(null, null))).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Duplicate selections are de-duplicated; default max time is 20")
    void duplicatesAndDefault() throws Exception {
        String s = normalise(req(Arrays.asList(1L, 1L, 2L, null), null)).toString();
        assertThat(s).contains("ingredientIds=[1, 2]").contains("maxTime=20");
    }

    @ParameterizedTest(name = "maxTime {0} valid={1}")
    @CsvSource({"0,false", "1,true", "20,true", "60,true", "61,false"})
    void maxTimeBounds(int maxTime, boolean valid) {
        if (valid) {
            org.assertj.core.api.Assertions.assertThatCode(() -> normalise(req(List.of(1L), maxTime))).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> normalise(req(List.of(1L), maxTime))).isInstanceOf(ApiException.class);
        }
    }
}

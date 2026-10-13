package com.bachelorchef.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Filtering and deterministic ranking of pantry-match candidates. Candidate counts come from a
 * single grouped SQL query ({@code PantryMatchRepository}); this class is pure so the ranking
 * rules can be unit tested exactly as they run in production.
 *
 * <p>Ranking: match ratio desc → missing required asc → total time asc → rating desc →
 * title (case-insensitive) asc → recipe id asc.
 */
public final class PantryMatcher {

    private PantryMatcher() {
    }

    public record Candidate(long recipeId, String title, int totalTimeMins, BigDecimal ratingAvg,
                            int requiredCount, int matchedRequiredCount) {

        public int missingCount() {
            return requiredCount - matchedRequiredCount;
        }
    }

    public static final Comparator<Candidate> RANKING = PantryMatcher::compare;

    public static List<Candidate> rank(List<Candidate> candidates, boolean strict) {
        return candidates.stream()
                .filter(c -> c.requiredCount() > 0)
                .filter(c -> strict ? c.missingCount() == 0 : c.matchedRequiredCount() > 0)
                .sorted(RANKING)
                .toList();
    }

    /** Whole-number percentage of required ingredients the user has (half-up). */
    public static int matchPercent(int matched, int required) {
        if (required <= 0) {
            throw new IllegalArgumentException("A recipe must have at least one required ingredient");
        }
        return BigDecimal.valueOf(matched * 100L)
                .divide(BigDecimal.valueOf(required), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static int compare(Candidate a, Candidate b) {
        // Exact ratio comparison via cross-multiplication: a.m/a.r vs b.m/b.r
        int byRatio = Long.compare((long) b.matchedRequiredCount() * a.requiredCount(),
                (long) a.matchedRequiredCount() * b.requiredCount());
        if (byRatio != 0) {
            return byRatio;
        }
        int byMissing = Integer.compare(a.missingCount(), b.missingCount());
        if (byMissing != 0) {
            return byMissing;
        }
        int byTime = Integer.compare(a.totalTimeMins(), b.totalTimeMins());
        if (byTime != 0) {
            return byTime;
        }
        int byRating = nz(b.ratingAvg()).compareTo(nz(a.ratingAvg()));
        if (byRating != 0) {
            return byRating;
        }
        int byTitle = a.title().toLowerCase(Locale.ROOT).compareTo(b.title().toLowerCase(Locale.ROOT));
        if (byTitle != 0) {
            return byTitle;
        }
        return Long.compare(a.recipeId(), b.recipeId());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

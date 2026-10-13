package com.bachelorchef.service;

import com.bachelorchef.entity.GoalCountMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Goal progress is always derived from cooked-history events, so it cannot drift when an event
 * is edited or deleted.
 *
 * <ul>
 *   <li>{@code UNIQUE_RECIPES}: number of distinct recipes cooked in the month that match the
 *       criteria ("new" means new within that month, not first-ever).</li>
 *   <li>{@code COOK_EVENTS}: number of matching cook events in the month.</li>
 * </ul>
 * The month is interpreted in the display time zone (Asia/Dhaka by default).
 */
public final class GoalProgressCalculator {

    public static final int TARGET_MIN = 1;
    public static final int TARGET_MAX = 30;
    private static final Pattern MONTH = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private GoalProgressCalculator() {
    }

    public record HistoryEntry(long recipeId, Instant cookedOn, int totalTimeMins, Set<Long> tagIds) {
    }

    public record Criteria(YearMonth month, GoalCountMode mode, Long tagId, Integer maxTotalTimeMins) {
    }

    public static boolean isValidTarget(Integer target) {
        return target != null && target >= TARGET_MIN && target <= TARGET_MAX;
    }

    /** Parses strict {@code YYYY-MM}; returns null for anything else (e.g. 2026-13, 2026-1, 26-01). */
    public static YearMonth parseMonth(String value) {
        if (value == null || !MONTH.matcher(value).matches()) {
            return null;
        }
        return YearMonth.parse(value);
    }

    public static Instant monthStart(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }

    public static Instant monthEnd(YearMonth month, ZoneId zone) {
        return month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
    }

    public static int progress(Collection<HistoryEntry> entries, Criteria criteria, ZoneId zone) {
        Instant from = monthStart(criteria.month(), zone);
        Instant to = monthEnd(criteria.month(), zone);
        List<HistoryEntry> qualifying = entries.stream()
                .filter(e -> !e.cookedOn().isBefore(from) && e.cookedOn().isBefore(to))
                .filter(e -> criteria.tagId() == null || e.tagIds().contains(criteria.tagId()))
                .filter(e -> criteria.maxTotalTimeMins() == null || e.totalTimeMins() <= criteria.maxTotalTimeMins())
                .toList();
        if (criteria.mode() == GoalCountMode.COOK_EVENTS) {
            return qualifying.size();
        }
        return (int) qualifying.stream().mapToLong(HistoryEntry::recipeId).distinct().count();
    }

    public static boolean isComplete(int progress, int target) {
        return progress >= target;
    }
}

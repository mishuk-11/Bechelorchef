package com.bachelorchef.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    /**
     * Dashboard metrics. "Range" metrics use [from, to) in the display time zone; "current"
     * metrics are a snapshot at request time. Each field documents which it is in docs/api.md.
     */
    public record DashboardSummary(
            LocalDate from, LocalDate to,
            long activeUsers,            // current: accounts with status ACTIVE
            long newUsers,               // range: accounts created
            long activeCooks,            // range: distinct users who logged a cook event
            long approvedRecipes,        // current
            long pendingRecipes,         // current
            long pendingReviews,         // current
            long openReports,            // current
            long cookEvents,             // range
            BigDecimal averageRating,    // current: mean of all approved review ratings
            long approvedReviewCount,    // current
            List<IngredientStat> popularIngredients,   // range
            List<RecipeStat> topRatedRecipes,          // current
            List<RecipeStat> mostCookedRecipes,        // range
            List<ImportDtos.JobView> recentImports) {
    }

    public record IngredientStat(Long ingredientId, String name, long cookCount, long recipeCount) {
    }

    public record RecipeStat(Long recipeId, String title, BigDecimal ratingAvg, long ratingCount, long cookCount) {
    }

    public record ActivityPoint(LocalDate date, long signups, long cookEvents, long reviews, long recipesSubmitted) {
    }

    public record RecipeAnalytics(LocalDate from, LocalDate to, List<RecipeStat> topRated, List<RecipeStat> mostCooked,
                                  List<ActivityPoint> activity) {
    }

    public record IngredientAnalytics(LocalDate from, LocalDate to, List<IngredientStat> popular) {
    }

    public record AuditView(Long id, Long actorId, String actorEmail, String action, String targetType, Long targetId,
                            String details, Instant createdAt) {
    }
}

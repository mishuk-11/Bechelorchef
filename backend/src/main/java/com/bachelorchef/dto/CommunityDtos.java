package com.bachelorchef.dto;

import com.bachelorchef.entity.GoalCountMode;
import com.bachelorchef.entity.ReportStatus;
import com.bachelorchef.entity.ReportTargetType;
import com.bachelorchef.entity.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CommunityDtos {

    private CommunityDtos() {
    }

    // ----- Reviews -----

    public record ReviewView(Long id, Long recipeId, int rating, String comment, String photoUrl,
                             ReviewStatus status, UserDtos.AuthorSummary author,
                             Instant createdAt, Instant updatedAt) {
    }

    /** Rating range is validated in the service so the message and rule live in one place. */
    public record ReviewRequest(
            Integer rating,
            String comment,
            String photoUrl,
            @Size(max = 255) String photoAssetId) {
    }

    public record MyReviewView(Long id, Long recipeId, String recipeTitle, int rating, String comment,
                               String photoUrl, ReviewStatus status, String moderationReason, Instant updatedAt) {
    }

    public record AdminReviewView(Long id, Long recipeId, String recipeTitle, int rating, String comment,
                                  String photoUrl, ReviewStatus status, String moderationReason,
                                  UserDtos.AuthorSummary author, String authorEmail, Instant createdAt) {
    }

    // ----- Saved / history / goals -----

    public record SavedRecipeView(RecipeDtos.RecipeSummary recipe, Instant savedAt) {
    }

    public record HistoryRecipe(Long id, String title, String imageUrl, int totalTimeMins, boolean available) {
    }

    public record HistoryView(Long id, HistoryRecipe recipe, Instant cookedOn, String notes, Instant createdAt) {
    }

    public record HistoryPage(PageResponse<HistoryView> page, long totalCookEvents, long distinctRecipes) {
    }

    public record CookRequest(Instant cookedOn, @Size(max = 1000, message = "Must be at most 1000 characters.") String notes) {
    }

    public record GoalView(Long id, String title, int targetCount, String month, GoalCountMode countMode,
                           CatalogDtos.TagView tag, Integer maxTotalTimeMins, int progress, int percent,
                           boolean completed, Instant completedAt, Instant createdAt) {
    }

    public record GoalRequest(
            String title,
            Integer targetCount,
            String month,
            GoalCountMode countMode,
            Long tagId,
            Integer maxTotalTimeMins) {
    }

    // ----- Reports -----

    public record ReportRequest(
            @NotNull(message = "Target type is required.") ReportTargetType targetType,
            @NotNull(message = "Target is required.") Long targetId,
            @NotBlank(message = "Please describe the problem.") @Size(max = 500, message = "Must be at most 500 characters.") String reason) {
    }

    public record ReportView(Long id, ReportTargetType targetType, Long targetId, String targetSummary,
                             Long recipeId, String reason, ReportStatus status,
                             UserDtos.AuthorSummary reporter, Instant createdAt) {
    }

    public enum ReportAction { DISMISS, REMOVE_CONTENT }

    public record ResolveReportRequest(
            @NotNull(message = "Action is required.") ReportAction action,
            @NotBlank(message = "A reason is required.") @Size(max = 500) String note) {
    }
}

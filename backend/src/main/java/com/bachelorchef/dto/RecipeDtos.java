package com.bachelorchef.dto;

import com.bachelorchef.entity.Difficulty;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.service.TimeCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RecipeDtos {

    private RecipeDtos() {
    }

    public record RecipeSummary(
            Long id, String title, String imageUrl,
            int prepTimeMins, int cookTimeMins, int totalTimeMins,
            TimeCategory timeCategory, String timeCategoryLabel,
            Difficulty difficulty, BigDecimal estimatedCostBdt, int baseServings,
            BigDecimal ratingAvg, int ratingCount,
            boolean official, RecipeStatus status,
            UserDtos.AuthorSummary author, Instant createdAt) {
    }

    public record IngredientLineView(Long ingredientId, String name, BigDecimal quantity, String unit,
                                     boolean optional, boolean active) {
    }

    public record StepView(int stepNumber, String instruction, String imageUrl) {
    }

    public record RecipeDetail(
            RecipeSummary recipe,
            String description,
            String imageAssetId,
            BigDecimal costPerServingBdt,
            List<IngredientLineView> ingredients,
            List<StepView> steps,
            List<CatalogDtos.TagView> tags,
            /** Visible only to the author and admins. */
            String moderationNote,
            Instant approvedAt,
            Instant updatedAt,
            Viewer viewer) {
    }

    /** What the current viewer may do; all false for anonymous users. */
    public record Viewer(boolean canEdit, boolean isOwner, boolean saved, Long myReviewId) {
    }

    /**
     * Editable recipe content. Server-controlled fields (official flag, status, approval identity,
     * moderation note, total time) are deliberately absent and ignored if sent.
     */
    public record RecipeRequest(
            String title,
            String description,
            Integer prepTimeMins,
            Integer cookTimeMins,
            String difficulty,
            BigDecimal estimatedCostBdt,
            Integer baseServings,
            String imageUrl,
            @Size(max = 255) String imageAssetId,
            List<String> tags,
            @Valid List<IngredientInput> ingredients,
            @Valid List<StepInput> steps,
            /** Community: true = submit for moderation (PENDING), false = keep as DRAFT. Admin official: true = publish. */
            Boolean submit) {
    }

    public record IngredientInput(Long ingredientId, BigDecimal quantity, String unit, Boolean optional) {
    }

    public record StepInput(String instruction, String imageUrl, @Size(max = 255) String imageAssetId) {
    }

    public record ScaledIngredient(Long ingredientId, String name, BigDecimal quantity, String unit, boolean optional) {
    }

    public record PortionResponse(Long recipeId, int baseServings, int servings,
                                  BigDecimal estimatedTotalCostBdt, BigDecimal costPerServingBdt,
                                  List<ScaledIngredient> ingredients, String note) {
    }

    public record MatchRequest(
            List<Long> ingredientIds,
            Integer maxTotalTimeMins,
            Boolean strict,
            String difficulty,
            List<Long> tagIds,
            BigDecimal maxCostBdt,
            Integer page,
            Integer size) {
    }

    public record IngredientRef(Long id, String name) {
    }

    public record OptionalIngredientRef(Long id, String name, boolean have) {
    }

    public record MatchItem(RecipeSummary recipe, int matchPercent, int requiredCount, int matchedCount,
                            List<IngredientRef> matchedIngredients, List<IngredientRef> missingIngredients,
                            List<OptionalIngredientRef> optionalIngredients) {
    }

    public record MatchResponse(PageResponse<MatchItem> results, int maxTotalTimeMins, boolean strict,
                                int selectedIngredientCount, String disclaimer) {
    }

    public record ModerationDecision(@Size(max = 1000, message = "Must be at most 1000 characters.") String reason) {
    }
}

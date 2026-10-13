package com.bachelorchef.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Source-independent recipe input (API request or import row) checked by {@link RecipeValidator}.
 * Ingredient references are opaque keys (an id from the API, or a normalised name from an import).
 */
public record RecipeDraft(
        String title,
        String description,
        Integer prepTimeMins,
        Integer cookTimeMins,
        String difficulty,
        BigDecimal estimatedCostBdt,
        Integer baseServings,
        String imageUrl,
        List<IngredientLine> ingredients,
        List<StepLine> steps,
        List<String> tags) {

    public record IngredientLine(String ingredientKey, BigDecimal quantity, String unit, boolean optional) {
    }

    public record StepLine(String instruction, String imageUrl) {
    }
}

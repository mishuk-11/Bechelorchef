package com.bachelorchef.importer;

import java.math.BigDecimal;
import java.util.List;

/**
 * One recipe record parsed from a CSV row or JSON object, before business validation.
 * Contains no creator, status, official flag or total time: those are always server-decided.
 */
public record ImportRow(
        int rowNumber,
        Integer lineNumber,
        String title,
        String description,
        Integer prepTimeMins,
        Integer cookTimeMins,
        String difficulty,
        BigDecimal estimatedCostBdt,
        Integer baseServings,
        String imageUrl,
        List<String> tags,
        List<Ingredient> ingredients,
        List<Step> steps,
        String excerpt) {

    public record Ingredient(String name, BigDecimal quantity, String unit, boolean optional) {
    }

    public record Step(String instruction, String imageUrl) {
    }
}

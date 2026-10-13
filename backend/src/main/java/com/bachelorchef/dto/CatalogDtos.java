package com.bachelorchef.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record IngredientView(Long id, String name, String category, String defaultUnit, boolean active) {
    }

    public record IngredientRequest(
            @NotBlank(message = "Name is required.") @Size(max = 80, message = "Must be at most 80 characters.") String name,
            @Size(max = 40, message = "Must be at most 40 characters.") String category,
            @Size(max = 20, message = "Must be at most 20 characters.") String defaultUnit) {
    }

    public record TagView(Long id, String name) {
    }

    public record TagRequest(
            @NotBlank(message = "Name is required.") @Size(max = 40, message = "Must be at most 40 characters.") String name) {
    }
}

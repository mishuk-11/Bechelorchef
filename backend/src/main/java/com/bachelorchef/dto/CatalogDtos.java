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

    // ----- Bulk ingredient import (CSV / JSON)

    /**
     * Outcome of one file row. Preview: NEW = would be added. Import: CREATED = added.
     * EXISTS = already in the catalogue, DUPLICATE = same name as an earlier row, INVALID = not usable.
     */
    public enum IngredientImportStatus { NEW, CREATED, EXISTS, DUPLICATE, INVALID }

    public record IngredientImportRow(int rowNumber, String name, String category, String defaultUnit,
                                      IngredientImportStatus status, String message) {
    }

    /** {@code newCount} counts NEW rows in a preview and CREATED rows after an import. */
    public record IngredientImportResult(String filename, String fileType, boolean dryRun, int totalRows, int newCount,
                                         int existingCount, int duplicateCount, int invalidCount,
                                         java.util.List<IngredientImportRow> rows) {
    }
}

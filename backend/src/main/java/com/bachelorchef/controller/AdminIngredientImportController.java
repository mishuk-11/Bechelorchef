package com.bachelorchef.controller;

import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.importer.IngredientImportService;
import com.bachelorchef.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk "add ingredients" from a CSV or JSON file. The /api/v1/admin/** rule rejects non-admins before
 * the multipart body is parsed; {@code @PreAuthorize} is a second line of defence.
 * Stateless: the same file is sent to preview and to import, and re-sending it adds nothing twice
 * because existing names are skipped.
 */
@RestController
@RequestMapping("/api/v1/admin/ingredients/import")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: catalogue, users & analytics")
public class AdminIngredientImportController {

    private final IngredientImportService imports;

    public AdminIngredientImportController(IngredientImportService imports) {
        this.imports = imports;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Check a CSV/JSON ingredient file (max 500 rows); writes nothing",
            description = "CSV header: name,category,default_unit (only name required). JSON: [{\"name\":\"Egg\",\"category\":\"Protein\",\"default_unit\":\"pcs\"}]")
    public CatalogDtos.IngredientImportResult preview(@AuthenticationPrincipal AuthUser admin, @RequestPart("file") MultipartFile file) {
        return imports.run(admin, file, true);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add the new ingredients of a CSV/JSON file; existing names, duplicates and invalid rows are skipped")
    public CatalogDtos.IngredientImportResult importFile(@AuthenticationPrincipal AuthUser admin, @RequestPart("file") MultipartFile file) {
        return imports.run(admin, file, false);
    }
}

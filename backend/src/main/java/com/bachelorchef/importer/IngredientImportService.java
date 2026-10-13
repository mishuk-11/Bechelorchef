package com.bachelorchef.importer;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.dto.CatalogDtos.IngredientImportStatus;
import com.bachelorchef.entity.ImportFileType;
import com.bachelorchef.entity.Ingredient;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.repository.IngredientRepository;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.security.RateLimiter;
import com.bachelorchef.service.AuditService;
import com.bachelorchef.service.CatalogService;
import com.bachelorchef.service.TextNormalizer;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk "add ingredients" from a CSV or JSON file (admin catalogue).
 * <ul>
 *   <li><b>Preview</b> ({@code dryRun = true}) validates every row and reports what would happen; nothing is written.</li>
 *   <li><b>Import</b> creates the NEW rows, each through {@link CatalogService#createIngredient} (its own
 *       transaction and audit entry), so a bad row never blocks the others.</li>
 * </ul>
 * Names already in the catalogue (ignoring case and spacing, active or not) are skipped, so uploading the
 * same file twice adds nothing the second time. The rules are those of the single "Add ingredient" form:
 * name 1–80, category ≤ 40, default unit ≤ 20 characters.
 */
@Service
public class IngredientImportService {

    static final int NAME_MAX = 80, CATEGORY_MAX = 40, UNIT_MAX = 20;

    private final AppProperties properties;
    private final IngredientFileParser parser;
    private final IngredientRepository ingredients;
    private final CatalogService catalog;
    private final AuditService audit;
    private final RateLimiter rateLimiter;

    public IngredientImportService(AppProperties properties, IngredientFileParser parser, IngredientRepository ingredients,
                                   CatalogService catalog, AuditService audit, RateLimiter rateLimiter) {
        this.properties = properties;
        this.parser = parser;
        this.ingredients = ingredients;
        this.catalog = catalog;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    public CatalogDtos.IngredientImportResult run(AuthUser admin, MultipartFile file, boolean dryRun) {
        rateLimiter.check("ingredient-import:" + admin.id(), 30, Duration.ofMinutes(10));
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Choose a non-empty CSV or JSON file.");
        }
        long maxBytes = properties.imports().maxBytes();
        if (file.getSize() > maxBytes) {
            throw ApiException.payloadTooLarge("The file is larger than the " + (maxBytes / 1024 / 1024) + " MB limit.");
        }
        String filename = ImportText.sanitizeFilename(file.getOriginalFilename());
        ImportFileType type = ImportService.detectType(filename, file.getContentType());
        List<IngredientFileParser.Row> rows;
        try {
            String text = ImportService.sniff(ImportText.decodeUtf8(file.getBytes()), type);
            rows = parser.parse(text, type, properties.imports().maxRows());
        } catch (FileRejectedException e) {
            throw new ApiException(e.getStatus(), e.getCode(), e.getMessage());
        } catch (IOException e) {
            throw ApiException.badRequest("The uploaded file could not be read.");
        }
        return process(admin, filename, type, rows, dryRun);
    }

    CatalogDtos.IngredientImportResult process(AuthUser admin, String filename, ImportFileType type,
                                               List<IngredientFileParser.Row> rows, boolean dryRun) {
        List<Checked> checked = rows.stream().map(IngredientImportService::validate).toList();

        // Existing catalogue entries for every valid name, loaded in one query.
        List<String> keys = checked.stream().filter(c -> c.status == null).map(c -> c.key).distinct().toList();
        Map<String, Ingredient> existing = keys.isEmpty() ? Map.of()
                : ingredients.findByNormalizedNameIn(keys).stream()
                        .collect(Collectors.toMap(Ingredient::getNormalizedName, Function.identity(), (a, b) -> a));

        Map<String, Integer> firstRowByKey = new HashMap<>();
        List<CatalogDtos.IngredientImportRow> out = new ArrayList<>();
        int created = 0;
        for (Checked c : checked) {
            IngredientImportStatus status = c.status;
            String message = c.message;
            if (status == null) {
                Integer first = firstRowByKey.putIfAbsent(c.key, c.rowNumber);
                Ingredient found = existing.get(c.key);
                if (first != null) {
                    status = IngredientImportStatus.DUPLICATE;
                    message = "Same name as row " + first + ".";
                } else if (found != null) {
                    status = IngredientImportStatus.EXISTS;
                    message = "Already in the catalogue as \"" + found.getName() + "\"" + (found.isActive() ? "." : " (inactive).");
                } else if (dryRun) {
                    status = IngredientImportStatus.NEW;
                    message = "Will be added.";
                } else {
                    try {
                        catalog.createIngredient(new CatalogDtos.IngredientRequest(c.name, c.category, c.unit), admin.id());
                        status = IngredientImportStatus.CREATED;
                        message = "Added.";
                        created++;
                    } catch (ApiException e) {
                        // e.g. another admin added the same name a moment ago
                        status = e.getStatus() == HttpStatus.CONFLICT ? IngredientImportStatus.EXISTS : IngredientImportStatus.INVALID;
                        message = e.getMessage();
                    }
                }
            }
            out.add(new CatalogDtos.IngredientImportRow(c.rowNumber, c.name, c.category, c.unit, status, message));
        }

        int existingCount = count(out, IngredientImportStatus.EXISTS);
        int duplicateCount = count(out, IngredientImportStatus.DUPLICATE);
        int invalidCount = count(out, IngredientImportStatus.INVALID);
        int newCount = dryRun ? count(out, IngredientImportStatus.NEW) : created;
        if (!dryRun) {
            audit.record(admin.id(), "INGREDIENTS_IMPORTED", "INGREDIENT", null,
                    "file=" + filename + "; rows=" + out.size() + "; created=" + created + "; existing=" + existingCount
                            + "; duplicates=" + duplicateCount + "; invalid=" + invalidCount);
        }
        return new CatalogDtos.IngredientImportResult(filename, type.name(), dryRun, out.size(), newCount,
                existingCount, duplicateCount, invalidCount, out);
    }

    /** Field rules of the single-ingredient form. A null status means the row is valid. */
    static Checked validate(IngredientFileParser.Row row) {
        String name = TextNormalizer.clean(row.name());
        String category = TextNormalizer.clean(row.category());
        String unit = TextNormalizer.clean(row.defaultUnit());
        String problem = row.error();
        if (problem == null) {
            if (name == null) {
                problem = "Name is required.";
            } else if (TextNormalizer.length(name) > NAME_MAX) {
                problem = "Name must be at most " + NAME_MAX + " characters.";
            } else if (TextNormalizer.length(category) > CATEGORY_MAX) {
                problem = "Category must be at most " + CATEGORY_MAX + " characters.";
            } else if (TextNormalizer.length(unit) > UNIT_MAX) {
                problem = "Default unit must be at most " + UNIT_MAX + " characters.";
            }
        }
        return new Checked(row.rowNumber(), shorten(name), shorten(category), shorten(unit), TextNormalizer.key(name),
                problem == null ? null : IngredientImportStatus.INVALID, problem);
    }

    /** Keeps over-long values readable in the response without echoing huge cells back. */
    private static String shorten(String value) {
        return value == null || value.length() <= 120 ? value : value.substring(0, 117) + "...";
    }

    private static int count(List<CatalogDtos.IngredientImportRow> rows, IngredientImportStatus status) {
        return (int) rows.stream().filter(r -> Objects.equals(r.status(), status)).count();
    }

    record Checked(int rowNumber, String name, String category, String unit, String key,
                   IngredientImportStatus status, String message) {
    }
}

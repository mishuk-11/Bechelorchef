package com.bachelorchef.stqa.inzamam.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.dto.CatalogDtos.IngredientImportStatus;
import com.bachelorchef.entity.Ingredient;
import com.bachelorchef.entity.Role;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.importer.IngredientFileParser;
import com.bachelorchef.importer.IngredientImportService;
import com.bachelorchef.repository.IngredientRepository;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.security.RateLimiter;
import com.bachelorchef.service.AuditService;
import com.bachelorchef.service.CatalogService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bulk ingredient import (admin catalogue): file parsing, validation, duplicate/existing detection,
 * preview vs. import, limits. Repository, catalogue and audit are mocks; the parser is real.
 */
@DisplayName("Bulk ingredient import (CSV / JSON)")
class IngredientImportTest {

    private static final AuthUser ADMIN = new AuthUser(1L, "inzamam@admin.su", Role.ADMIN);

    private IngredientRepository repository;
    private CatalogService catalog;
    private AuditService audit;
    private IngredientImportService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = mock(AppProperties.class);
        when(properties.imports()).thenReturn(new AppProperties.Imports(5_242_880, 500, 24));
        repository = mock(IngredientRepository.class);
        catalog = mock(CatalogService.class);
        audit = mock(AuditService.class);
        when(catalog.createIngredient(any(), eq(ADMIN.id())))
                .thenAnswer(inv -> {
                    CatalogDtos.IngredientRequest r = inv.getArgument(0);
                    return new CatalogDtos.IngredientView(99L, r.name(), r.category(), r.defaultUnit(), true);
                });
        service = new IngredientImportService(properties, new IngredientFileParser(JsonMapper.builder().build()),
                repository, catalog, audit, mock(RateLimiter.class));
    }

    private void existing(String... namesAndActive) {
        List<Ingredient> list = new java.util.ArrayList<>();
        for (String spec : namesAndActive) {
            String[] parts = spec.split("\\|");
            Ingredient i = new Ingredient();
            i.setName(parts[0]);
            i.setNormalizedName(parts[0].toLowerCase());
            i.setActive(parts.length < 2 || Boolean.parseBoolean(parts[1]));
            list.add(i);
        }
        when(repository.findByNormalizedNameIn(anyCollection())).thenReturn(list);
    }

    private static MockMultipartFile file(String name, String contentType, String content) {
        return new MockMultipartFile("file", name, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile resource(String name, String contentType) throws IOException {
        try (InputStream in = IngredientImportTest.class.getResourceAsStream("/" + name)) {
            return new MockMultipartFile("file", name, contentType, in.readAllBytes());
        }
    }

    private static Map<String, IngredientImportStatus> statusByName(CatalogDtos.IngredientImportResult result) {
        return result.rows().stream().filter(r -> r.name() != null)
                .collect(Collectors.toMap(CatalogDtos.IngredientImportRow::name, CatalogDtos.IngredientImportRow::status, (a, b) -> a));
    }

    @Nested
    @DisplayName("Preview and import")
    class PreviewAndImport {

        @Test
        @DisplayName("Preview of the sample CSV: new vs. existing (incl. inactive), nothing written")
        void previewCsv() throws IOException {
            existing("Chickpeas", "Coriander Leaves|false");
            var result = service.run(ADMIN, resource("bulk_ingredients.csv", "text/csv"), true);
            assertTrue(result.dryRun());
            assertEquals(8, result.totalRows());
            assertEquals(6, result.newCount());
            assertEquals(2, result.existingCount());
            assertEquals(IngredientImportStatus.EXISTS, statusByName(result).get("Coriander Leaves"));
            assertTrue(result.rows().stream().anyMatch(r -> r.message().contains("(inactive)")));
            verify(catalog, never()).createIngredient(any(), any());
            verify(audit, never()).record(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Import creates only the new rows and writes one summary audit entry")
        void importCsv() throws IOException {
            existing("Chickpeas", "Mustard Oil");
            var result = service.run(ADMIN, resource("bulk_ingredients.csv", "text/csv"), false);
            assertFalse(result.dryRun());
            assertEquals(6, result.newCount());
            assertEquals(6, result.rows().stream().filter(r -> r.status() == IngredientImportStatus.CREATED).count());
            verify(catalog, times(6)).createIngredient(any(), eq(ADMIN.id()));
            verify(catalog, never()).createIngredient(argThat(r -> r.name().equals("Chickpeas")), any());
            verify(audit).record(eq(ADMIN.id()), eq("INGREDIENTS_IMPORTED"), eq("INGREDIENT"), isNull(),
                    argThat(details -> details.contains("created=6") && details.contains("existing=2")));
        }

        @Test
        @DisplayName("The JSON sample gives the same result as the CSV sample")
        void jsonSameAsCsv() throws IOException {
            existing("Chickpeas");
            var csv = service.run(ADMIN, resource("bulk_ingredients.csv", "text/csv"), true);
            var json = service.run(ADMIN, resource("bulk_ingredients.json", "application/json"), true);
            assertEquals(statusByName(csv), statusByName(json));
            assertEquals("JSON", json.fileType());
        }

        @Test
        @DisplayName("Quoted CSV value with a comma stays one name; values are trimmed")
        void quotedComma() throws IOException {
            existing();
            var result = service.run(ADMIN, file("i.csv", "text/csv", "name,category\n\"Chili, dried\" ,  Spices \n"), true);
            assertEquals("Chili, dried", result.rows().get(0).name());
            assertEquals("Spices", result.rows().get(0).category());
        }

        @Test
        @DisplayName("Same name twice in one file (ignoring case and spacing) is a duplicate")
        void duplicateInFile() {
            existing();
            var result = service.run(ADMIN, file("i.csv", "text/csv", "name\nEgg Noodles\n  egg   NOODLES \n"), false);
            assertEquals(IngredientImportStatus.CREATED, result.rows().get(0).status());
            assertEquals(IngredientImportStatus.DUPLICATE, result.rows().get(1).status());
            assertEquals("Same name as row 1.", result.rows().get(1).message());
            verify(catalog, times(1)).createIngredient(any(), any());
        }

        @Test
        @DisplayName("A name added by someone else during the import is reported as existing")
        void raceConflict() {
            existing();
            when(catalog.createIngredient(any(), any())).thenThrow(ApiException.conflict("An ingredient named \"Tofu\" already exists."));
            var result = service.run(ADMIN, file("i.csv", "text/csv", "name\nTofu\n"), false);
            assertEquals(IngredientImportStatus.EXISTS, result.rows().get(0).status());
            assertEquals(0, result.newCount());
        }
    }

    @Nested
    @DisplayName("Row validation (BVA on lengths)")
    class RowValidation {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = ';', value = {
                "name 80 chars is valid;      80; 1;  1;  NEW",
                "name 81 chars is invalid;    81; 1;  1;  INVALID",
                "category 40 chars is valid;  5;  40; 1;  NEW",
                "category 41 chars is invalid;5;  41; 1;  INVALID",
                "unit 20 chars is valid;      5;  1;  20; NEW",
                "unit 21 chars is invalid;    5;  1;  21; INVALID",
        })
        void lengths(String label, int name, int category, int unit, IngredientImportStatus expected) {
            existing();
            String csv = "name,category,default_unit\n" + "n".repeat(name) + "," + "c".repeat(category) + "," + "u".repeat(unit) + "\n";
            var result = service.run(ADMIN, file("i.csv", "text/csv", csv), true);
            assertEquals(expected, result.rows().get(0).status(), label);
        }

        @Test
        @DisplayName("Blank name is invalid; other rows still go through")
        void blankName() {
            existing();
            var result = service.run(ADMIN, file("i.csv", "text/csv", "name,category\n   ,Spices\nGinger,Spices\n"), true);
            assertEquals(IngredientImportStatus.INVALID, result.rows().get(0).status());
            assertEquals("Name is required.", result.rows().get(0).message());
            assertEquals(IngredientImportStatus.NEW, result.rows().get(1).status());
            assertEquals(1, result.invalidCount());
        }

        @Test
        @DisplayName("JSON: unknown field and non-text value are row errors; defaultUnit alias accepted")
        void jsonRowErrors() {
            existing();
            String json = "[{\"name\":\"Ginger\",\"colour\":\"red\"},{\"name\":42},{\"name\":\"Garlic\",\"defaultUnit\":\"clove\"}]";
            var result = service.run(ADMIN, file("i.json", "application/json", json), true);
            assertEquals(IngredientImportStatus.INVALID, result.rows().get(0).status());
            assertTrue(result.rows().get(0).message().contains("Unknown field"));
            assertEquals(IngredientImportStatus.INVALID, result.rows().get(1).status());
            assertEquals(IngredientImportStatus.NEW, result.rows().get(2).status());
            assertEquals("clove", result.rows().get(2).defaultUnit());
        }
    }

    @Nested
    @DisplayName("File-level checks")
    class FileChecks {

        private ApiException reject(MockMultipartFile f) {
            existing();
            return assertThrows(ApiException.class, () -> service.run(ADMIN, f, true));
        }

        @Test
        @DisplayName("500 rows are accepted, 501 rows are rejected before anything is written")
        void rowLimit() {
            existing();
            StringBuilder csv = new StringBuilder("name\n");
            for (int i = 1; i <= 500; i++) {
                csv.append("Ingredient ").append(i).append('\n');
            }
            assertEquals(500, service.run(ADMIN, file("i.csv", "text/csv", csv.toString()), true).totalRows());
            csv.append("Ingredient 501\n");
            ApiException e = reject(file("i.csv", "text/csv", csv.toString()));
            assertEquals("TOO_MANY_ROWS", e.getCode());
            verify(catalog, never()).createIngredient(any(), any());
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = ';', value = {
                "category,default_unit\\nSpices,g;     BAD_HEADER",
                "name,colour\\nEgg,white;              BAD_HEADER",
                "name\\n;                               NO_ROWS",
        })
        void csvFileErrors(String content, String code) {
            assertEquals(code, reject(file("i.csv", "text/csv", content.replace("\\n", "\n"))).getCode());
        }

        @Test
        @DisplayName("JSON root must be an array")
        void jsonNotArray() {
            assertEquals("NOT_AN_ARRAY", reject(file("i.json", "application/json", "{\"name\":\"Egg\"}")).getCode());
        }

        @Test
        @DisplayName("Malformed JSON is rejected")
        void jsonMalformed() {
            assertEquals("MALFORMED_FILE", reject(file("i.json", "application/json", "[{\"name\": \"Egg\"")).getCode());
        }

        @Test
        @DisplayName("Empty file and unsupported file type are rejected")
        void emptyAndWrongType() {
            assertEquals("EMPTY_FILE", reject(file("i.csv", "text/csv", "")).getCode());
            ApiException wrongType = reject(file("i.txt", "text/plain", "name\nEgg\n"));
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, wrongType.getStatus());
        }
    }
}

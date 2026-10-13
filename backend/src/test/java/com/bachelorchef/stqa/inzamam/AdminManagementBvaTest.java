package com.bachelorchef.stqa.inzamam;

import static com.bachelorchef.stqa.inzamam.AdminTestKit.ADMIN;
import static com.bachelorchef.stqa.inzamam.AdminTestKit.ingredient;
import static com.bachelorchef.stqa.inzamam.AdminTestKit.ingredientJson;
import static com.bachelorchef.stqa.inzamam.AdminTestKit.name;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

// ─────────────────────────────────────────────────────────────────────────────
//  BOUNDARY VALUE ANALYSIS (BVA)   -   module: AdminManagementController
//  Owner: Inzamam.  Test cases TC-01 .. TC-10.
//  Boundaries: ingredient name 1..80 chars, tag name 1..40 chars, page size 1..100,
//  page index 0 (first page), dashboard date range end (inclusive).
//  The full boundary table is in docs/stqa/Inzamam/AdminManagement_TestDesign.xlsx (sheet BVA).
// ─────────────────────────────────────────────────────────────────────────────
@DisplayName("Inzamam - AdminManagementController - BVA")
class AdminManagementBvaTest {

    private AdminTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new AdminTestKit();
        when(kit.catalog.createIngredient(any(), eq(ADMIN.id()))).thenReturn(ingredient("saved"));
    }

    @AfterEach
    void tearDown() {
        AdminTestKit.signOut();
    }

    // TC-01..05  Ingredient name length, valid range 1..80 (IngredientRequest @NotBlank @Size(max = 80))
    @ParameterizedTest(name = "{0} Create ingredient: name length {1} -> accepted = {2}")
    @CsvSource({
            "TC-01, 0,  false",
            "TC-02, 1,  true",
            "TC-03, 79, true",
            "TC-04, 80, true",
            "TC-05, 81, false"
    })
    void ingredientNameLength(String tc, int length, boolean accepted) throws Exception {
        var result = kit.mvc.perform(post("/api/v1/admin/ingredients")
                .contentType(MediaType.APPLICATION_JSON).content(ingredientJson(name(length))));
        if (accepted) {
            result.andExpect(status().is2xxSuccessful());
            verify(kit.catalog).createIngredient(any(), eq(ADMIN.id()));
        } else {
            result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
            verify(kit.catalog, never()).createIngredient(any(), any());
        }
    }

    // TC-06, TC-07  Page size boundaries 1 and 100 must reach the service unchanged
    @ParameterizedTest(name = "{0} Ingredient list: page size {1} is passed to the catalogue unchanged")
    @CsvSource({
            "TC-06, 1",
            "TC-07, 100"
    })
    void ingredientPageSize(String tc, int size) throws Exception {
        kit.mvc.perform(get("/api/v1/admin/ingredients").param("size", String.valueOf(size)))
                .andExpect(status().isOk());
        verify(kit.catalog).searchIngredients(isNull(), isNull(), eq(true), isNull(), isNull(), eq(size));
    }

    @Test
    @DisplayName("TC-08 Audit log: page 0 (first page boundary) is requested as page 0")
    void auditFirstPage() throws Exception {
        kit.mvc.perform(get("/api/v1/admin/audit-logs").param("page", "0").param("size", "20"))
                .andExpect(status().isOk());
        verify(kit.audit).list(eq(0), eq(20));
    }

    @Test
    @DisplayName("TC-09 Dashboard: one-day range (from = to) keeps the inclusive end date")
    void dashboardOneDayRange() throws Exception {
        kit.mvc.perform(get("/api/v1/admin/dashboard/summary").param("from", "2026-10-13").param("to", "2026-10-13"))
                .andExpect(status().isOk());
        verify(kit.analytics).summary(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13));
    }

    @Test
    @DisplayName("TC-10 Create tag: name length 41 (max 40 + 1) is rejected")
    void tagNameTooLong() throws Exception {
        kit.mvc.perform(post("/api/v1/admin/tags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name(41) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Must be at most 40 characters."));
        verify(kit.catalog, never()).createTag(any(), any());
    }
}

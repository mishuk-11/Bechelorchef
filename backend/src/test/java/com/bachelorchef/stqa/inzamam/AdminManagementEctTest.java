package com.bachelorchef.stqa.inzamam;

import static com.bachelorchef.stqa.inzamam.AdminTestKit.ADMIN;
import static com.bachelorchef.stqa.inzamam.AdminTestKit.ingredient;
import static com.bachelorchef.stqa.inzamam.AdminTestKit.ingredientJson;
import static com.bachelorchef.stqa.inzamam.AdminTestKit.row;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.entity.UserStatus;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

// ─────────────────────────────────────────────────────────────────────────────
//  EQUIVALENCE CLASS TESTING (ECT)   -   module: AdminManagementController
//  Owner: Inzamam.  Test cases TC-11 .. TC-20.
//  One representative value per class: filter given / absent, padded vs clean text,
//  valid vs missing enum, valid vs impossible date, successful create.
//  The class table is in docs/stqa/Inzamam/AdminManagement_TestDesign.xlsx (sheet ECT).
// ─────────────────────────────────────────────────────────────────────────────
@DisplayName("Inzamam - AdminManagementController - ECT")
class AdminManagementEctTest {

    private AdminTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new AdminTestKit();
    }

    @AfterEach
    void tearDown() {
        AdminTestKit.signOut();
    }

    @Test
    @DisplayName("TC-11 Ingredient list: category filter given (valid class 'Spices') is applied")
    void categoryGiven() throws Exception {
        kit.mvc.perform(get("/api/v1/admin/ingredients").param("category", "Spices")).andExpect(status().isOk());
        verify(kit.catalog).searchIngredients(isNull(), eq("Spices"), eq(true), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("TC-12 Ingredient list: category filter absent (all categories) sends no filter")
    void categoryAbsent() throws Exception {
        kit.mvc.perform(get("/api/v1/admin/ingredients")).andExpect(status().isOk());
        verify(kit.catalog).searchIngredients(isNull(), isNull(), eq(true), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("TC-13 User list: role filter ADMIN (valid class) is applied")
    void roleFilter() throws Exception {
        kit.mvc.perform(get("/api/v1/admin/users").param("role", "ADMIN")).andExpect(status().isOk());
        verify(kit.users).search(isNull(), isNull(), eq("ADMIN"), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("TC-14 User list: status filter SUSPENDED (valid class) is applied")
    void statusFilter() throws Exception {
        kit.mvc.perform(get("/api/v1/admin/users").param("status", "SUSPENDED")).andExpect(status().isOk());
        verify(kit.users).search(isNull(), eq("SUSPENDED"), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("TC-15 Create ingredient: valid request returns 201 Created")
    void createIngredientStatus() throws Exception {
        when(kit.catalog.createIngredient(any(), eq(ADMIN.id()))).thenReturn(ingredient("Garlic"));
        kit.mvc.perform(post("/api/v1/admin/ingredients").contentType(MediaType.APPLICATION_JSON).content(ingredientJson("Garlic")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Garlic"));
    }

    @Test
    @DisplayName("TC-16 Create tag: padded name (whitespace class) is answered with the saved, trimmed name")
    void paddedTagName() throws Exception {
        when(kit.catalog.createTag(any(), eq(ADMIN.id()))).thenReturn(new CatalogDtos.TagView(5L, "Spicy Food"));
        kit.mvc.perform(post("/api/v1/admin/tags").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   Spicy Food   \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Spicy Food"));
    }

    @Test
    @DisplayName("TC-17 Ingredient categories: returned in the catalogue's A-Z order")
    void categoriesOrder() throws Exception {
        when(kit.catalog.ingredientCategories()).thenReturn(List.of("Dairy", "Grains", "Spices"));
        kit.mvc.perform(get("/api/v1/admin/ingredients/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Dairy"))
                .andExpect(jsonPath("$[2]").value("Spices"));
    }

    @Test
    @DisplayName("TC-18 Suspend user: response shows the same recipe/review/cook counts as the service")
    void suspendResponse() throws Exception {
        when(kit.users.updateStatus(eq(ADMIN), eq(7L), any())).thenReturn(row(UserStatus.SUSPENDED));
        kit.mvc.perform(patch("/api/v1/admin/users/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Spam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.recipeCount").value(4))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.cookCount").value(9));
    }

    @Test
    @DisplayName("TC-19 Change user status: missing status (invalid class) is rejected with 400")
    void missingStatus() throws Exception {
        kit.mvc.perform(patch("/api/v1/admin/users/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").value("Status is required."));
        verify(kit.users, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("TC-20 Dashboard: impossible date 2026-02-30 (invalid class) is rejected with 400")
    void impossibleDate() throws Exception {
        kit.mvc.perform(get("/api/v1/admin/dashboard/summary").param("from", "2026-02-30"))
                .andExpect(status().isBadRequest());
        verify(kit.analytics, never()).summary(any(), any());
    }
}

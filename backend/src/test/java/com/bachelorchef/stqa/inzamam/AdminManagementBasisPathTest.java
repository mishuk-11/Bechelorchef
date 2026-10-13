package com.bachelorchef.stqa.inzamam;

import static com.bachelorchef.stqa.inzamam.AdminTestKit.ADMIN;
import static com.bachelorchef.stqa.inzamam.AdminTestKit.ingredient;
import static com.bachelorchef.stqa.inzamam.AdminTestKit.name;
import static com.bachelorchef.stqa.inzamam.AdminTestKit.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.dto.UserDtos;
import com.bachelorchef.entity.UserStatus;
import com.bachelorchef.exception.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

// ─────────────────────────────────────────────────────────────────────────────
//  BASIS PATH TESTING   -   module: AdminManagementController (5 functions)
//  Owner: Inzamam.  Test cases TC-21 .. TC-30.
//
//  Function            V(G)  Decisions
//  ingredients          2    size == null
//  createIngredient     3    name != null ; name.strip().length() >= 80
//  summary              2    to == null
//  auditLogs            2    page == null
//  updateStatus         1    (straight line)
//  Total independent paths = 10. Flow graphs, node legend and V(G) = E - N + 2 are in
//  docs/stqa/Inzamam/AdminManagement_BasisPath.xlsx and AdminManagement_FlowGraphs.png.
//  Each function is called directly so the test controls exactly which path runs.
// ─────────────────────────────────────────────────────────────────────────────
@DisplayName("Inzamam - AdminManagementController - Basis path")
class AdminManagementBasisPathTest {

    private AdminTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new AdminTestKit();
    }

    @AfterEach
    void tearDown() {
        AdminTestKit.signOut();
    }

    // ----- ingredients: V(G) = 2

    @Test
    @DisplayName("TC-21 ingredients P1: size == null (T) -> no size sent, service default used")
    void ingredientsP1() {
        kit.controller.ingredients("rice", null, null, 0, null);
        verify(kit.catalog).searchIngredients(eq("rice"), isNull(), eq(true), isNull(), eq(0), isNull());
    }

    @Test
    @DisplayName("TC-22 ingredients P2: size == null (F) -> requested size 20 sent unchanged")
    void ingredientsP2() {
        kit.controller.ingredients("rice", null, null, 0, 20);
        verify(kit.catalog).searchIngredients(eq("rice"), isNull(), eq(true), isNull(), eq(0), eq(20));
    }

    // ----- createIngredient: V(G) = 3

    @Test
    @DisplayName("TC-23 createIngredient P1: name != null (F) -> length check skipped, service called")
    void createIngredientP1() {
        var request = new CatalogDtos.IngredientRequest(null, "Spices", "g");
        kit.controller.createIngredient(ADMIN, request);
        verify(kit.catalog).createIngredient(request, ADMIN.id());
    }

    @Test
    @DisplayName("TC-24 createIngredient P2: name length 85 >= 80 (T) -> validation error, nothing saved")
    void createIngredientP2() {
        var request = new CatalogDtos.IngredientRequest(name(85), "Spices", "g");
        ApiException error = assertThrows(ApiException.class, () -> kit.controller.createIngredient(ADMIN, request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(kit.catalog, never()).createIngredient(any(), any());
    }

    @Test
    @DisplayName("TC-25 createIngredient P3: short name (F) -> saved and answered 201 Created")
    void createIngredientP3() {
        var request = new CatalogDtos.IngredientRequest("Garlic", "Spices", "g");
        when(kit.catalog.createIngredient(request, ADMIN.id())).thenReturn(ingredient("Garlic"));
        var response = kit.controller.createIngredient(ADMIN, request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Garlic", response.getBody().name());
    }

    // ----- summary: V(G) = 2

    @Test
    @DisplayName("TC-26 summary P1: to == null (T) -> open-ended range sent to analytics")
    void summaryP1() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        kit.controller.summary(from, null);
        verify(kit.analytics).summary(from, null);
    }

    @Test
    @DisplayName("TC-27 summary P2: to == null (F) -> inclusive end date 2026-10-31 sent unchanged")
    void summaryP2() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        kit.controller.summary(from, to);
        verify(kit.analytics).summary(from, to);
    }

    // ----- auditLogs: V(G) = 2

    @Test
    @DisplayName("TC-28 auditLogs P1: page == null (T) -> first page (0 or service default) requested")
    void auditLogsP1() {
        kit.controller.auditLogs(null, 20);
        verify(kit.audit).list(argThat(page -> page == null || page == 0), eq(20));
    }

    @Test
    @DisplayName("TC-29 auditLogs P2: page == null (F) -> page 3 requested unchanged")
    void auditLogsP2() {
        kit.controller.auditLogs(3, 20);
        verify(kit.audit).list(eq(3), eq(20));
    }

    // ----- updateStatus: V(G) = 1

    @Test
    @DisplayName("TC-30 updateStatus P1: single path -> the service's updated row is returned as is")
    void updateStatusP1() {
        var request = new UserDtos.UpdateStatusRequest(UserStatus.SUSPENDED, "Spam");
        UserDtos.AdminUserRow saved = row(UserStatus.SUSPENDED);
        when(kit.users.updateStatus(ADMIN, 7L, request)).thenReturn(saved);
        assertEquals(saved, kit.controller.updateStatus(ADMIN, 7L, request));
    }
}

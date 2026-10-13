package com.bachelorchef.stqa.saima;

import static com.bachelorchef.stqa.saima.PersonalTestKit.ME;
import static com.bachelorchef.stqa.saima.PersonalTestKit.cook;
import static com.bachelorchef.stqa.saima.PersonalTestKit.goal;
import static com.bachelorchef.stqa.saima.PersonalTestKit.historyPage;
import static com.bachelorchef.stqa.saima.PersonalTestKit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.entity.GoalCountMode;
import com.bachelorchef.exception.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

// ─────────────────────────────────────────────────────────────────────────────
//  BASIS PATH TESTING   -   module: PersonalController (5 functions)
//  Owner: Saima.  Test cases TC-19 .. TC-30.
//
//  Function        V(G)  Decisions
//  savedRecipes     2    size == null
//  updateHistory    3    notes != null ; notes.length() >= 1000
//  goals            3    stream loop (has next goal?) ; !goal.completed()
//  createGoal       3    targetCount != null ; targetCount >= 30
//  history          1    (straight line)
//  Total independent paths = 12. Flow graphs, node legend and V(G) = E - N + 2 are in
//  docs/stqa/Saima/Personal_BasisPath.xlsx and Personal_FlowGraphs.png.
//  Each function is called directly so the test controls exactly which path runs.
// ─────────────────────────────────────────────────────────────────────────────
@DisplayName("Saima - PersonalController - Basis path")
class PersonalBasisPathTest {

    private PersonalTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new PersonalTestKit();
    }

    @AfterEach
    void tearDown() {
        PersonalTestKit.signOut();
    }

    // ----- savedRecipes: V(G) = 2

    @Test
    @DisplayName("TC-19 savedRecipes P1: size == null (T) -> no size sent, service default used")
    void savedRecipesP1() {
        kit.controller.savedRecipes(ME, 0, null);
        verify(kit.saved).list(eq(ME), eq(0), isNull());
    }

    @Test
    @DisplayName("TC-20 savedRecipes P2: size == null (F) -> requested size 50 sent unchanged")
    void savedRecipesP2() {
        kit.controller.savedRecipes(ME, 0, 50);
        verify(kit.saved).list(ME, 0, 50);
    }

    // ----- updateHistory: V(G) = 3

    @Test
    @DisplayName("TC-21 updateHistory P1: notes != null (F) -> request sent unchanged")
    void updateHistoryP1() {
        var request = new CommunityDtos.CookRequest(null, null);
        kit.controller.updateHistory(ME, 3L, request);
        verify(kit.history).update(ME, 3L, request);
    }

    @Test
    @DisplayName("TC-22 updateHistory P2: notes length 1200 >= 1000 (T) -> validation error, nothing saved")
    void updateHistoryP2() {
        var request = new CommunityDtos.CookRequest(null, text(1200));
        ApiException error = assertThrows(ApiException.class, () -> kit.controller.updateHistory(ME, 3L, request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(kit.history, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("TC-23 updateHistory P3: notes 'Tasty' (length F) and new date -> both sent to the service")
    void updateHistoryP3() {
        var request = new CommunityDtos.CookRequest(Instant.parse("2026-10-12T12:00:00Z"), "Tasty");
        kit.controller.updateHistory(ME, 3L, request);
        verify(kit.history).update(ME, 3L, request);
    }

    // ----- goals: V(G) = 3

    @Test
    @DisplayName("TC-24 goals P1: no goals (loop never runs) -> empty list")
    void goalsP1() {
        when(kit.goals.list(ME)).thenReturn(List.of());
        assertTrue(kit.controller.goals(ME).isEmpty());
    }

    @Test
    @DisplayName("TC-25 goals P2: one goal in progress (loop runs, !completed T) -> goal listed")
    void goalsP2() {
        var inProgress = goal(1, 5, 2);
        when(kit.goals.list(ME)).thenReturn(List.of(inProgress));
        assertEquals(List.of(inProgress), kit.controller.goals(ME));
    }

    @Test
    @DisplayName("TC-26 goals P3: one completed goal (loop runs, !completed F) -> goal still listed as completed")
    void goalsP3() {
        var completed = goal(1, 1, 1);
        when(kit.goals.list(ME)).thenReturn(List.of(completed));
        assertEquals(List.of(completed), kit.controller.goals(ME));
    }

    // ----- createGoal: V(G) = 3

    @Test
    @DisplayName("TC-27 createGoal P1: targetCount != null (F) -> request sent to the service unchanged")
    void createGoalP1() {
        var request = new CommunityDtos.GoalRequest("Cook dinners", null, "2026-10", GoalCountMode.UNIQUE_RECIPES, null, null);
        kit.controller.createGoal(ME, request);
        verify(kit.goals).create(ME, request);
    }

    @Test
    @DisplayName("TC-28 createGoal P2: targetCount 45 >= 30 (T) -> validation error, nothing saved")
    void createGoalP2() {
        var request = new CommunityDtos.GoalRequest("Cook dinners", 45, "2026-10", GoalCountMode.UNIQUE_RECIPES, null, null);
        ApiException error = assertThrows(ApiException.class, () -> kit.controller.createGoal(ME, request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(kit.goals, never()).create(any(), any());
    }

    @Test
    @DisplayName("TC-29 createGoal P3: targetCount 5 (F) with 20-min limit -> saved unchanged, 201 Created")
    void createGoalP3() {
        var request = new CommunityDtos.GoalRequest("Quick dinners", 5, "2026-10", GoalCountMode.COOK_EVENTS, null, 20);
        when(kit.goals.create(any(), any())).thenReturn(goal(1, 5, 0));
        var response = kit.controller.createGoal(ME, request);
        verify(kit.goals).create(ME, request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // ----- history: V(G) = 1

    @Test
    @DisplayName("TC-30 history P1: single path -> service page returned as is (newest first, real totals)")
    void historyP1() {
        var page = historyPage(List.of(cook(2, 30, "2026-10-12T12:00:00Z"), cook(1, 30, "2026-10-10T12:00:00Z")), 2, 1);
        when(kit.history.list(ME, 0, 15)).thenReturn(page);
        assertEquals(page, kit.controller.history(ME, 0, 15));
    }
}

package com.bachelorchef.stqa.saima;

import static com.bachelorchef.stqa.saima.PersonalTestKit.ME;
import static com.bachelorchef.stqa.saima.PersonalTestKit.cook;
import static com.bachelorchef.stqa.saima.PersonalTestKit.goal;
import static com.bachelorchef.stqa.saima.PersonalTestKit.historyPage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bachelorchef.entity.GoalCountMode;
import com.bachelorchef.exception.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

// ─────────────────────────────────────────────────────────────────────────────
//  EQUIVALENCE CLASS TESTING (ECT)   -   module: PersonalController
//  Owner: Saima.  Test cases TC-11 .. TC-18.
//  Classes: save / unsave / unknown recipe; count mode COOK_EVENTS; goal with a time limit;
//  completed goal; new cook date; same recipe cooked twice.
//  The class table is in docs/stqa/Saima/Personal_TestDesign.xlsx (sheet ECT).
// ─────────────────────────────────────────────────────────────────────────────
@DisplayName("Saima - PersonalController - ECT")
class PersonalEctTest {

    private PersonalTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new PersonalTestKit();
    }

    @AfterEach
    void tearDown() {
        PersonalTestKit.signOut();
    }

    @Test
    @DisplayName("TC-11 Save recipe to cookbook (valid recipe class) -> 204 No Content")
    void saveRecipe() throws Exception {
        kit.mvc.perform(put("/api/v1/users/me/saved-recipes/1")).andExpect(status().isNoContent());
        verify(kit.saved).save(ME, 1L);
    }

    @Test
    @DisplayName("TC-12 Remove recipe from cookbook (saved recipe class) -> 204 No Content")
    void unsaveRecipe() throws Exception {
        kit.mvc.perform(delete("/api/v1/users/me/saved-recipes/1")).andExpect(status().isNoContent());
        verify(kit.saved).unsave(ME, 1L);
    }

    @Test
    @DisplayName("TC-13 Save recipe that does not exist (invalid id class) -> 404 Not Found")
    void saveUnknownRecipe() throws Exception {
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recipe not found.")).when(kit.saved).save(ME, 999L);
        kit.mvc.perform(put("/api/v1/users/me/saved-recipes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-14 Edit goal: count mode COOK_EVENTS (other enum class) is sent to the service")
    void changeCountMode() throws Exception {
        kit.mvc.perform(patch("/api/v1/users/me/goals/4").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countMode\":\"COOK_EVENTS\"}"))
                .andExpect(status().isOk());
        verify(kit.goals).update(eq(ME), eq(4L), argThat(r -> r.countMode() == GoalCountMode.COOK_EVENTS));
    }

    @Test
    @DisplayName("TC-15 Create goal with time limit 15 min (criteria-given class) keeps the limit")
    void goalWithTimeLimit() throws Exception {
        when(kit.goals.create(any(), any())).thenReturn(goal(1, 5, 0));
        kit.mvc.perform(post("/api/v1/users/me/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Quick dinners\",\"targetCount\":5,\"month\":\"2026-10\",\"maxTotalTimeMins\":15}"))
                .andExpect(status().isCreated());
        verify(kit.goals).create(eq(ME), argThat(r -> Integer.valueOf(15).equals(r.maxTotalTimeMins())));
    }

    @Test
    @DisplayName("TC-16 Goals list: a completed goal (completed class) is still listed")
    void completedGoalListed() throws Exception {
        when(kit.goals.list(ME)).thenReturn(List.of(goal(1, 1, 1), goal(2, 5, 2)));
        kit.mvc.perform(get("/api/v1/users/me/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].completed").value(true));
    }

    @Test
    @DisplayName("TC-17 Edit cook history: new date 2026-10-12 (date-given class) is sent to the service")
    void changeCookDate() throws Exception {
        kit.mvc.perform(patch("/api/v1/users/me/cooked-history/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cookedOn\":\"2026-10-12T12:00:00Z\",\"notes\":\"Lunch\"}"))
                .andExpect(status().isOk());
        verify(kit.history).update(eq(ME), eq(3L), argThat(r -> Instant.parse("2026-10-12T12:00:00Z").equals(r.cookedOn())));
    }

    @Test
    @DisplayName("TC-18 Cook history: same recipe cooked twice (repeat class) -> 2 cooks, 1 different recipe")
    void repeatRecipeTotals() throws Exception {
        when(kit.history.list(ME, null, null)).thenReturn(historyPage(
                List.of(cook(2, 30, "2026-10-12T12:00:00Z"), cook(1, 30, "2026-10-10T12:00:00Z")), 2, 1));
        kit.mvc.perform(get("/api/v1/users/me/cooked-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCookEvents").value(2))
                .andExpect(jsonPath("$.distinctRecipes").value(1));
    }
}

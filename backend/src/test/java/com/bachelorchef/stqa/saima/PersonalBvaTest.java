package com.bachelorchef.stqa.saima;

import static com.bachelorchef.stqa.saima.PersonalTestKit.ME;
import static com.bachelorchef.stqa.saima.PersonalTestKit.goal;
import static com.bachelorchef.stqa.saima.PersonalTestKit.goalJson;
import static com.bachelorchef.stqa.saima.PersonalTestKit.text;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

import com.bachelorchef.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

// ─────────────────────────────────────────────────────────────────────────────
//  BOUNDARY VALUE ANALYSIS (BVA)   -   module: PersonalController
//  Owner: Saima.  Test cases TC-01 .. TC-10.
//  Boundaries: goal target 1..30 (AGENTS.md 5), cook notes <= 1000 chars (CookRequest @Size),
//  cookbook page size 1..100 (around the value 10).
//  The goal service is stubbed with its documented contract (1..30), so the controller's own
//  behaviour decides the outcome. Full table: docs/stqa/Saima/Personal_TestDesign.xlsx (sheet BVA).
// ─────────────────────────────────────────────────────────────────────────────
@DisplayName("Saima - PersonalController - BVA")
class PersonalBvaTest {

    private PersonalTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new PersonalTestKit();
        when(kit.goals.create(any(), any())).thenReturn(goal(1, 5, 0));
        when(kit.goals.create(any(), argThat(r -> r.targetCount() != null && (r.targetCount() < 1 || r.targetCount() > 30))))
                .thenThrow(ApiException.validation("targetCount", "Must be between 1 and 30."));
    }

    @AfterEach
    void tearDown() {
        PersonalTestKit.signOut();
    }

    // TC-01..05  Goal target count, valid range 1..30
    @ParameterizedTest(name = "{0} Create goal: target count {1} -> accepted = {2}")
    @CsvSource({
            "TC-01, 0,  false",
            "TC-02, 1,  true",
            "TC-03, 29, true",
            "TC-04, 30, true",
            "TC-05, 31, false"
    })
    void goalTarget(String tc, int target, boolean accepted) throws Exception {
        var result = kit.mvc.perform(post("/api/v1/users/me/goals").contentType(MediaType.APPLICATION_JSON).content(goalJson(target)));
        if (accepted) {
            result.andExpect(status().isCreated());
        } else {
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.targetCount").value("Must be between 1 and 30."));
        }
    }

    // TC-06..08  Cook notes length, max 1000 (CookRequest @Size(max = 1000))
    @ParameterizedTest(name = "{0} Edit cook history: notes length {1} -> accepted = {2}")
    @CsvSource({
            "TC-06, 999,  true",
            "TC-07, 1000, true",
            "TC-08, 1001, false"
    })
    void notesLength(String tc, int length, boolean accepted) throws Exception {
        var result = kit.mvc.perform(patch("/api/v1/users/me/cooked-history/3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"" + text(length) + "\"}"));
        if (accepted) {
            result.andExpect(status().isOk());
            verify(kit.history).update(eq(ME), eq(3L), argThat(r -> r.notes().length() == length));
        } else {
            result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.notes").exists());
            verify(kit.history, never()).update(any(), any(), any());
        }
    }

    // TC-09, TC-10  Cookbook page size: 12 (what the web app asks for) and 10
    @ParameterizedTest(name = "{0} Cookbook: page size {1} is passed to the service unchanged")
    @CsvSource({
            "TC-09, 12",
            "TC-10, 10"
    })
    void cookbookPageSize(String tc, int size) throws Exception {
        kit.mvc.perform(get("/api/v1/users/me/saved-recipes").param("size", String.valueOf(size))).andExpect(status().isOk());
        verify(kit.saved).list(eq(ME), isNull(), eq(size));
    }
}

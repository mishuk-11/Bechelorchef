package com.bachelorchef.stqa.cherry;

import static com.bachelorchef.stqa.cherry.ProfileTestKit.ME;
import static com.bachelorchef.stqa.cherry.ProfileTestKit.review;
import static com.bachelorchef.stqa.cherry.ProfileTestKit.reviewPage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.entity.ReviewStatus;
import com.bachelorchef.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

// ─────────────────────────────────────────────────────────────────────────────
//  EQUIVALENCE CLASS TESTING (ECT)   -   module: ProfileController
//  Owner: Cherry.  Test cases TC-11 .. TC-20.
//  Classes: bio given; phone clear ("") / valid; status filter valid / absent / unknown;
//  wrong current password; last-admin delete; blank delete password; rejected review.
//  The class table is in docs/stqa/Cherry/Profile_TestDesign.xlsx (sheet ECT).
// ─────────────────────────────────────────────────────────────────────────────
@DisplayName("Cherry - ProfileController - ECT")
class ProfileEctTest {

    private ProfileTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new ProfileTestKit();
    }

    @AfterEach
    void tearDown() {
        ProfileTestKit.signOut();
    }

    @Test
    @DisplayName("TC-11 Update profile: new bio (non-empty text class) is sent to the service")
    void bioSaved() throws Exception {
        kit.mvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Student cook from Dhaka\"}"))
                .andExpect(status().isOk());
        verify(kit.users).updateProfile(eq(ME.id()), argThat(r -> "Student cook from Dhaka".equals(r.bio())));
    }

    @Test
    @DisplayName("TC-12 Update profile: empty phone \"\" (clear class) is sent as \"\" so the number is removed")
    void clearPhone() throws Exception {
        kit.mvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumber\":\"\"}"))
                .andExpect(status().isOk());
        verify(kit.users).updateProfile(eq(ME.id()), argThat(r -> "".equals(r.phoneNumber())));
    }

    @Test
    @DisplayName("TC-13 Update profile: valid phone +8801712345678 is sent unchanged")
    void validPhone() throws Exception {
        kit.mvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+8801712345678\"}"))
                .andExpect(status().isOk());
        verify(kit.users).updateProfile(eq(ME.id()), argThat(r -> "+8801712345678".equals(r.phoneNumber())));
    }

    @Test
    @DisplayName("TC-14 My submissions: status filter APPROVED (valid class) is applied")
    void statusFilterApplied() throws Exception {
        kit.mvc.perform(get("/api/v1/users/me/recipes").param("status", "APPROVED")).andExpect(status().isOk());
        verify(kit.recipeQueries).search(argThat(f -> f.statuses().equals(List.of(RecipeStatus.APPROVED))
                && ME.id().equals(f.authorId())), any(), any(), any());
    }

    @Test
    @DisplayName("TC-15 My submissions: no status filter (all-statuses class) lists my recipes of every status")
    void noStatusFilter() throws Exception {
        kit.mvc.perform(get("/api/v1/users/me/recipes")).andExpect(status().isOk());
        verify(kit.recipeQueries).search(argThat(f -> f.statuses().isEmpty() && ME.id().equals(f.authorId())),
                any(), any(), any());
    }

    @Test
    @DisplayName("TC-16 My submissions: unknown status PUBLISHED (invalid class) is rejected with 400")
    void unknownStatus() throws Exception {
        kit.mvc.perform(get("/api/v1/users/me/recipes").param("status", "PUBLISHED")).andExpect(status().isBadRequest());
        verify(kit.recipeQueries, never()).search(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-17 Change password: wrong current password (invalid class) -> 400 with field error")
    void wrongCurrentPassword() throws Exception {
        doThrow(ApiException.validation("currentPassword", "Current password is incorrect."))
                .when(kit.users).changePassword(eq(ME.id()), any());
        kit.mvc.perform(post("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"12345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").value("Current password is incorrect."));
    }

    @Test
    @DisplayName("TC-18 Delete account: last active admin (conflict class) -> 409 LAST_ADMIN")
    void lastAdminCannotDelete() throws Exception {
        doThrow(new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "The last active administrator cannot be deleted."))
                .when(kit.users).deleteAccount(ME.id(), "123456");
        kit.mvc.perform(delete("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    @DisplayName("TC-19 Delete account: blank password (invalid class) is rejected with 400")
    void blankDeletePassword() throws Exception {
        kit.mvc.perform(delete("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required."));
        verify(kit.users, never()).deleteAccount(any(), any());
    }

    @Test
    @DisplayName("TC-20 My reviews: REJECTED review (rejected class) shows the moderator's reason")
    void rejectedReviewShowsReason() throws Exception {
        when(kit.reviews.mine(ME, null, null))
                .thenReturn(reviewPage(List.of(review(1, ReviewStatus.REJECTED, "Off topic")), 0, 10, 1));
        kit.mvc.perform(get("/api/v1/users/me/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].moderationReason").value("Off topic"));
    }
}

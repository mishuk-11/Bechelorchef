package com.bachelorchef.stqa.cherry;

import static com.bachelorchef.stqa.cherry.ProfileTestKit.ME;
import static com.bachelorchef.stqa.cherry.ProfileTestKit.digits;
import static com.bachelorchef.stqa.cherry.ProfileTestKit.review;
import static com.bachelorchef.stqa.cherry.ProfileTestKit.reviewPage;
import static com.bachelorchef.stqa.cherry.ProfileTestKit.text;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bachelorchef.entity.ReviewStatus;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

// ─────────────────────────────────────────────────────────────────────────────
//  BOUNDARY VALUE ANALYSIS (BVA)   -   module: ProfileController
//  Owner: Cherry.  Test cases TC-01 .. TC-10.
//  Boundaries: new password 8..72 chars, phone number <= 20 chars, full name <= 100 chars,
//  second page of "my submissions", review total = page size + 1.
//  The full boundary table is in docs/stqa/Cherry/Profile_TestDesign.xlsx (sheet BVA).
// ─────────────────────────────────────────────────────────────────────────────
@DisplayName("Cherry - ProfileController - BVA")
class ProfileBvaTest {

    private ProfileTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new ProfileTestKit();
    }

    @AfterEach
    void tearDown() {
        ProfileTestKit.signOut();
    }

    // TC-01..04  New password length, valid range 8..72 (ChangePasswordRequest @Size(min = 8, max = 72))
    @ParameterizedTest(name = "{0} Change password: new password length {1} -> accepted = {2}")
    @CsvSource({
            "TC-01, 7,  false",
            "TC-02, 8,  true",
            "TC-03, 72, true",
            "TC-04, 73, false"
    })
    void newPasswordLength(String tc, int length, boolean accepted) throws Exception {
        var result = kit.mvc.perform(post("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"123456\",\"newPassword\":\"" + text(length) + "\"}"));
        if (accepted) {
            result.andExpect(status().is2xxSuccessful());
            verify(kit.users).changePassword(eq(ME.id()), any());
        } else {
            result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.newPassword").exists());
            verify(kit.users, never()).changePassword(any(), any());
        }
    }

    // TC-05, TC-06  Phone number length, max 20 (UpdateProfileRequest @Size(max = 20))
    @ParameterizedTest(name = "{0} Update profile: phone length {1} -> accepted = {2}")
    @CsvSource({
            "TC-05, 20, true",
            "TC-06, 21, false"
    })
    void phoneLength(String tc, int length, boolean accepted) throws Exception {
        var result = kit.mvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + digits(length) + "\"}"));
        if (accepted) {
            result.andExpect(status().isOk());
            verify(kit.users).updateProfile(eq(ME.id()), argThat(r -> digits(length).equals(r.phoneNumber())));
        } else {
            result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());
            verify(kit.users, never()).updateProfile(any(), any());
        }
    }

    // TC-07, TC-08  Full name length, valid range 2..100 (users.full_name VARCHAR(100))
    @ParameterizedTest(name = "{0} Update profile: full name length {1} is accepted and sent to the service")
    @CsvSource({
            "TC-07, 99",
            "TC-08, 100"
    })
    void fullNameLength(String tc, int length) throws Exception {
        kit.mvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"" + text(length) + "\"}"))
                .andExpect(status().isOk());
        verify(kit.users).updateProfile(eq(ME.id()), argThat(r -> text(length).equals(r.fullName())));
    }

    @Test
    @DisplayName("TC-09 My submissions: page 1 (second page, first value above 0) is requested")
    void secondPageOfSubmissions() throws Exception {
        kit.mvc.perform(get("/api/v1/users/me/recipes").param("page", "1").param("size", "12"))
                .andExpect(status().isOk());
        verify(kit.recipeQueries).search(any(), any(), eq(1), eq(12));
    }

    @Test
    @DisplayName("TC-10 My reviews: 3 reviews with page size 2 (total = size + 1) -> totalElements 3, totalPages 2")
    void reviewTotalsAcrossPages() throws Exception {
        var firstPage = List.of(review(1, ReviewStatus.APPROVED, null), review(2, ReviewStatus.APPROVED, null));
        when(kit.reviews.mine(ME, 0, 2)).thenReturn(reviewPage(firstPage, 0, 2, 3));
        kit.mvc.perform(get("/api/v1/users/me/reviews").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }
}

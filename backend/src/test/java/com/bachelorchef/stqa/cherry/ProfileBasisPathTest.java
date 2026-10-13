package com.bachelorchef.stqa.cherry;

import static com.bachelorchef.stqa.cherry.ProfileTestKit.ME;
import static com.bachelorchef.stqa.cherry.ProfileTestKit.review;
import static com.bachelorchef.stqa.cherry.ProfileTestKit.reviewPage;
import static com.bachelorchef.stqa.cherry.ProfileTestKit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bachelorchef.dto.UserDtos;
import com.bachelorchef.entity.ReviewStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.security.RefreshCookies;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

// ─────────────────────────────────────────────────────────────────────────────
//  BASIS PATH TESTING   -   module: ProfileController (5 functions)
//  Owner: Cherry.  Test cases TC-21 .. TC-30.
//
//  Function        V(G)  Decisions
//  update           5    fullName != null ; fullName.strip().length() >= 100 ;
//                        phoneNumber == null ; phoneNumber.isBlank()
//  myReviews        2    stream map over items (loop: has next item?)
//  myRecipes        1    (straight line)
//  changePassword   1    (straight line)
//  delete           1    (straight line)
//  Total independent paths = 10. Flow graphs, node legend and V(G) = E - N + 2 are in
//  docs/stqa/Cherry/Profile_BasisPath.xlsx and Profile_FlowGraphs.png.
//  Each function is called directly so the test controls exactly which path runs.
// ─────────────────────────────────────────────────────────────────────────────
@DisplayName("Cherry - ProfileController - Basis path")
class ProfileBasisPathTest {

    private ProfileTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new ProfileTestKit();
    }

    @AfterEach
    void tearDown() {
        ProfileTestKit.signOut();
    }

    private UserDtos.UpdateProfileRequest sentToService() {
        ArgumentCaptor<UserDtos.UpdateProfileRequest> captor = ArgumentCaptor.forClass(UserDtos.UpdateProfileRequest.class);
        verify(kit.users).updateProfile(eq(ME.id()), captor.capture());
        return captor.getValue();
    }

    // ----- update: V(G) = 5

    @Test
    @DisplayName("TC-21 update P1: fullName null (F), phone null (T) -> both sent as null (unchanged)")
    void updateP1() {
        kit.controller.update(ME, new UserDtos.UpdateProfileRequest(null, null, null, null, null));
        UserDtos.UpdateProfileRequest sent = sentToService();
        assertNull(sent.fullName());
        assertNull(sent.phoneNumber());
    }

    @Test
    @DisplayName("TC-22 update P2: fullName length 120 >= 100 (T) -> validation error, nothing saved")
    void updateP2() {
        var request = new UserDtos.UpdateProfileRequest(text(120), null, null, null, null);
        ApiException error = assertThrows(ApiException.class, () -> kit.controller.update(ME, request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(kit.users, never()).updateProfile(any(), any());
    }

    @Test
    @DisplayName("TC-23 update P3: fullName 'Cherry' (length F), phone null (T) -> name sent, phone null")
    void updateP3() {
        kit.controller.update(ME, new UserDtos.UpdateProfileRequest("Cherry", null, null, null, null));
        UserDtos.UpdateProfileRequest sent = sentToService();
        assertEquals("Cherry", sent.fullName());
        assertNull(sent.phoneNumber());
    }

    @Test
    @DisplayName("TC-24 update P4: phone \"\" (null F, blank T) -> \"\" sent so the phone is cleared")
    void updateP4() {
        kit.controller.update(ME, new UserDtos.UpdateProfileRequest(null, "", null, null, null));
        assertEquals("", sentToService().phoneNumber());
    }

    @Test
    @DisplayName("TC-25 update P5: phone +8801712345678 (null F, blank F) -> phone sent unchanged")
    void updateP5() {
        kit.controller.update(ME, new UserDtos.UpdateProfileRequest(null, "+8801712345678", null, null, null));
        assertEquals("+8801712345678", sentToService().phoneNumber());
    }

    // ----- myReviews: V(G) = 2

    @Test
    @DisplayName("TC-26 myReviews P1: no reviews (loop body never runs) -> empty page, total 0")
    void myReviewsP1() {
        when(kit.reviews.mine(ME, 0, 10)).thenReturn(reviewPage(List.of(), 0, 10, 0));
        var result = kit.controller.myReviews(ME, 0, 10);
        assertTrue(result.items().isEmpty());
        assertEquals(0, result.totalElements());
    }

    @Test
    @DisplayName("TC-27 myReviews P2: one REJECTED review (loop body runs) -> review kept with its reason")
    void myReviewsP2() {
        var rejected = review(1, ReviewStatus.REJECTED, "Off topic");
        when(kit.reviews.mine(ME, 0, 10)).thenReturn(reviewPage(List.of(rejected), 0, 10, 1));
        var result = kit.controller.myReviews(ME, 0, 10);
        assertEquals(List.of(rejected), result.items());
    }

    // ----- myRecipes: V(G) = 1

    @Test
    @DisplayName("TC-28 myRecipes P1: single path -> my recipes requested newest first, page 2 as asked")
    void myRecipesP1() {
        kit.controller.myRecipes(ME, null, 2, 12);
        verify(kit.recipeQueries).search(argThat(f -> ME.id().equals(f.authorId())), eq("newest"), eq(2), eq(12));
    }

    // ----- changePassword: V(G) = 1

    @Test
    @DisplayName("TC-29 changePassword P1: single path -> password changed, 204 No Content")
    void changePasswordP1() {
        var request = new UserDtos.ChangePasswordRequest("123456", "new-password-1");
        var response = kit.controller.changePassword(ME, request);
        verify(kit.users).changePassword(ME.id(), request);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    // ----- delete: V(G) = 1

    @Test
    @DisplayName("TC-30 delete P1: single path -> account deleted, 204 and refresh cookie cleared")
    void deleteP1() {
        var response = kit.controller.delete(ME, new UserDtos.DeleteAccountRequest("123456"));
        verify(kit.users).deleteAccount(ME.id(), "123456");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie, "Set-Cookie header missing");
        assertTrue(cookie.startsWith(RefreshCookies.NAME + "=") && cookie.contains("Max-Age=0"), cookie);
    }
}

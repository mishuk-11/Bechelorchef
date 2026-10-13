package com.bachelorchef.stqa.cherry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bachelorchef.controller.ProfileController;
import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.entity.ReviewStatus;
import com.bachelorchef.entity.Role;
import com.bachelorchef.exception.GlobalExceptionHandler;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.security.RefreshCookies;
import com.bachelorchef.service.MediaService;
import com.bachelorchef.service.RecipeQueryService;
import com.bachelorchef.service.ReviewService;
import com.bachelorchef.service.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Test kit for ProfileController (owner: Cherry).
 * Standalone MockMvc with the real validation and error handler; services are Mockito mocks.
 */
final class ProfileTestKit {

    static final AuthUser ME = new AuthUser(5L, "user1@user.com", Role.USER);

    final UserService users = mock(UserService.class);
    final RecipeQueryService recipeQueries = mock(RecipeQueryService.class);
    final ReviewService reviews = mock(ReviewService.class);
    final MediaService media = mock(MediaService.class);
    final RefreshCookies cookies = mock(RefreshCookies.class);
    final ProfileController controller = new ProfileController(users, recipeQueries, reviews, media, cookies);
    final MockMvc mvc;

    ProfileTestKit() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ME, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(cookies.clear()).thenReturn(ResponseCookie.from(RefreshCookies.NAME, "")
                .httpOnly(true).path("/api/v1/auth").maxAge(Duration.ZERO).build());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    static void signOut() {
        SecurityContextHolder.clearContext();
    }

    static String text(int length) {
        return "a".repeat(length);
    }

    static String digits(int length) {
        return "1".repeat(length);
    }

    static CommunityDtos.MyReviewView review(long id, ReviewStatus status, String reason) {
        return new CommunityDtos.MyReviewView(id, 20L + id, "Egg Fried Rice", 4, "Nice and quick", null, status, reason,
                Instant.parse("2026-10-10T08:00:00Z"));
    }

    /** One page of reviews from a list that has {@code total} reviews in all. */
    static PageResponse<CommunityDtos.MyReviewView> reviewPage(List<CommunityDtos.MyReviewView> items, int page, int size, long total) {
        return PageResponse.of(items, page, size, total);
    }
}

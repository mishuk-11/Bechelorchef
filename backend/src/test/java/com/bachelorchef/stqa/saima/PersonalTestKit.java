package com.bachelorchef.stqa.saima;

import static org.mockito.Mockito.mock;

import com.bachelorchef.controller.PersonalController;
import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.entity.GoalCountMode;
import com.bachelorchef.entity.Role;
import com.bachelorchef.exception.GlobalExceptionHandler;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.service.CookingHistoryService;
import com.bachelorchef.service.GoalService;
import com.bachelorchef.service.SavedRecipeService;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Test kit for PersonalController (owner: Saima).
 * Standalone MockMvc with the real validation and error handler; services are Mockito mocks.
 */
final class PersonalTestKit {

    static final AuthUser ME = new AuthUser(6L, "user2@user.com", Role.USER);

    final SavedRecipeService saved = mock(SavedRecipeService.class);
    final CookingHistoryService history = mock(CookingHistoryService.class);
    final GoalService goals = mock(GoalService.class);
    final PersonalController controller = new PersonalController(saved, history, goals);
    final MockMvc mvc;

    PersonalTestKit() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ME, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    static void signOut() {
        SecurityContextHolder.clearContext();
    }

    static String text(int length) {
        return "n".repeat(length);
    }

    static String goalJson(Integer targetCount) {
        return "{\"title\":\"Cook dinners\",\"targetCount\":" + targetCount + ",\"month\":\"2026-10\",\"countMode\":\"UNIQUE_RECIPES\"}";
    }

    static CommunityDtos.GoalView goal(long id, int target, int progress) {
        boolean done = progress >= target;
        return new CommunityDtos.GoalView(id, "Cook dinners", target, "2026-10", GoalCountMode.UNIQUE_RECIPES, null, null,
                progress, Math.min(100, progress * 100 / target), done, done ? Instant.parse("2026-10-12T10:00:00Z") : null,
                Instant.parse("2026-10-01T10:00:00Z"));
    }

    static CommunityDtos.HistoryView cook(long id, long recipeId, String cookedOn) {
        return new CommunityDtos.HistoryView(id, new CommunityDtos.HistoryRecipe(recipeId, "Egg Fried Rice", null, 15, true),
                Instant.parse(cookedOn), null, Instant.parse(cookedOn));
    }

    /** History page as the service returns it: newest first. */
    static CommunityDtos.HistoryPage historyPage(List<CommunityDtos.HistoryView> items, long totalCooks, long distinct) {
        return new CommunityDtos.HistoryPage(PageResponse.of(items, 0, 15, items.size()), totalCooks, distinct);
    }
}

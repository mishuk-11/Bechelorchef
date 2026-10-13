package com.bachelorchef.stqa.inzamam;

import static org.mockito.Mockito.mock;

import com.bachelorchef.analytics.AnalyticsService;
import com.bachelorchef.controller.AdminManagementController;
import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.UserDtos;
import com.bachelorchef.entity.Role;
import com.bachelorchef.entity.UserStatus;
import com.bachelorchef.exception.GlobalExceptionHandler;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.service.AdminUserService;
import com.bachelorchef.service.AuditService;
import com.bachelorchef.service.CatalogService;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Test kit for AdminManagementController (owner: Inzamam).
 * The controller runs in a standalone MockMvc with its real validation and error handler; the services
 * behind it are Mockito mocks, so every test checks only what the controller itself does.
 */
final class AdminTestKit {

    static final AuthUser ADMIN = new AuthUser(1L, "inzamam@admin.su", Role.ADMIN);

    final CatalogService catalog = mock(CatalogService.class);
    final AdminUserService users = mock(AdminUserService.class);
    final AnalyticsService analytics = mock(AnalyticsService.class);
    final AuditService audit = mock(AuditService.class);
    final AdminManagementController controller = new AdminManagementController(catalog, users, analytics, audit);
    final MockMvc mvc;

    AdminTestKit() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ADMIN, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    static void signOut() {
        SecurityContextHolder.clearContext();
    }

    static String name(int length) {
        return "n".repeat(length);
    }

    static String ingredientJson(String name) {
        return "{\"name\":\"" + name + "\",\"category\":\"Spices\",\"defaultUnit\":\"g\"}";
    }

    static CatalogDtos.IngredientView ingredient(String name) {
        return new CatalogDtos.IngredientView(10L, name, "Spices", "g", true);
    }

    static <T> PageResponse<T> page(List<T> items) {
        return PageResponse.of(items, 0, 20, items.size());
    }

    /** A user row whose four counters are all different, so a swap is visible. */
    static UserDtos.AdminUserRow row(UserStatus status) {
        return new UserDtos.AdminUserRow(7L, "User Seven", "user7@user.com", Role.USER, status,
                Instant.parse("2026-10-01T00:00:00Z"), 4, 1, 9);
    }
}

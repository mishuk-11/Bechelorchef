package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.AuthDtos;
import com.bachelorchef.entity.ImportStatus;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.entity.Role;
import com.bachelorchef.entity.User;
import com.bachelorchef.entity.UserStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.importer.ImportService;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.security.JwtService;
import com.bachelorchef.security.RateLimiter;
import com.bachelorchef.security.RefreshTokenService;
import com.bachelorchef.service.AuthService;
import com.bachelorchef.service.RecipeQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Decision table testing. Each test is one rule (column) of a table in docs/testing.md and the
 * "Decision Tables" sheet. "-" means "don't care".
 */
class DecisionTableTest {

    /**
     * DT-01 Recipe visibility.
     * C1 approved? C2 signed in? C3 admin? C4 owner? → A1 visible / A2 hidden (404)
     * R1: Y - - -  → visible
     * R2: N N - -  → hidden
     * R3: N Y Y -  → visible
     * R4: N Y N Y  → visible
     * R5: N Y N N  → hidden
     */
    @Nested
    @DisplayName("DT-01 Recipe visibility")
    class Visibility {
        private Recipe recipe(boolean approved) {
            User owner = mock(User.class);
            when(owner.getId()).thenReturn(7L);
            Recipe r = new Recipe();
            r.setStatus(approved ? RecipeStatus.APPROVED : RecipeStatus.PENDING);
            r.setCreatedBy(owner);
            return r;
        }

        @ParameterizedTest(name = "DT-01-{0}: approved={1} signedIn={2} admin={3} owner={4} → visible={5}")
        @CsvSource({
                "R1, true,  false, false, false, true",
                "R2, false, false, false, false, false",
                "R3, false, true,  true,  false, true",
                "R4, false, true,  false, true,  true",
                "R5, false, true,  false, false, false"})
        void rule(String rule, boolean approved, boolean signedIn, boolean admin, boolean owner, boolean visible) {
            AuthUser viewer = !signedIn ? null : new AuthUser(owner ? 7L : 9L, "v@x.test", admin ? Role.ADMIN : Role.USER);
            assertThat(RecipeQueryService.canView(recipe(approved), viewer)).isEqualTo(visible);
        }
    }

    /**
     * DT-02 Login outcome.
     * C1 account exists? C2 password correct? C3 status → action
     * R1: N - -          → 401 INVALID_CREDENTIALS
     * R2: Y N -          → 401 INVALID_CREDENTIALS (same message as R1)
     * R3: Y Y ACTIVE     → 200 session
     * R4: Y Y SUSPENDED  → 403 ACCOUNT_SUSPENDED
     * R5: Y Y DELETED    → 401 INVALID_CREDENTIALS
     */
    @Nested
    @DisplayName("DT-02 Login outcome")
    class Login {
        private AuthService service(UserStatus status) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
            User user = new User();
            user.setEmail("sam@example.com");
            user.setFullName("Sam");
            user.setRole(Role.USER);
            user.setStatus(status);
            user.setPasswordHash(encoder.encode("right-password"));
            UserRepository users = mock(UserRepository.class);
            when(users.findByEmail("sam@example.com")).thenReturn(Optional.of(user));
            when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
            JwtService jwt = mock(JwtService.class);
            when(jwt.issue(any())).thenReturn(new JwtService.AccessToken("t", Instant.now()));
            RateLimiter limiter = new RateLimiter(new AppProperties("Asia/Dhaka", List.of("x"), null, null, null, null,
                    new AppProperties.RateLimit(false)), Clock.systemUTC());
            return new AuthService(users, encoder, jwt, mock(RefreshTokenService.class), limiter);
        }

        private ApiException fail(AuthService auth, String email, String password) {
            try {
                auth.login(new AuthDtos.LoginRequest(email, password), "1.1.1.1");
            } catch (ApiException e) {
                return e;
            }
            throw new AssertionError("expected login to fail");
        }

        @Test @DisplayName("DT-02-R1 unknown account → 401 generic")
        void r1() {
            ApiException e = fail(service(UserStatus.ACTIVE), "ghost@example.com", "x");
            assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test @DisplayName("DT-02-R2 wrong password → 401 generic (same message as R1)")
        void r2() {
            AuthService auth = service(UserStatus.ACTIVE);
            assertThat(fail(auth, "sam@example.com", "wrong").getMessage()).isEqualTo(fail(auth, "ghost@example.com", "x").getMessage());
        }

        @Test @DisplayName("DT-02-R3 active + correct password → session")
        void r3() {
            assertThat(service(UserStatus.ACTIVE).login(new AuthDtos.LoginRequest(" SAM@example.com ", "right-password"), "1.1.1.1")
                    .response().accessToken()).isEqualTo("t");
        }

        @Test @DisplayName("DT-02-R4 suspended + correct password → 403 ACCOUNT_SUSPENDED")
        void r4() {
            ApiException e = fail(service(UserStatus.SUSPENDED), "sam@example.com", "right-password");
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getCode()).isEqualTo("ACCOUNT_SUSPENDED");
        }

        @Test @DisplayName("DT-02-R5 deleted + correct password → 401 generic")
        void r5() {
            assertThat(fail(service(UserStatus.DELETED), "sam@example.com", "right-password").getCode()).isEqualTo("INVALID_CREDENTIALS");
        }
    }

    /**
     * DT-03 Import job final status.
     * C1 imported>0? C2 failed>0? C3 skipped>0? → status
     * R1: N - -  → FAILED
     * R2: Y N N  → COMPLETED
     * R3: Y Y N  → COMPLETED_WITH_ERRORS
     * R4: Y N Y  → COMPLETED_WITH_ERRORS
     * R5: Y Y Y  → COMPLETED_WITH_ERRORS
     */
    @ParameterizedTest(name = "DT-03-{0}: imported={1} failed={2} skipped={3} → {4}")
    @CsvSource({
            "R1, 0, 2, 1, FAILED",
            "R2, 5, 0, 0, COMPLETED",
            "R3, 5, 1, 0, COMPLETED_WITH_ERRORS",
            "R4, 5, 0, 1, COMPLETED_WITH_ERRORS",
            "R5, 5, 1, 1, COMPLETED_WITH_ERRORS"})
    @DisplayName("DT-03 Import final status")
    void importStatus(String rule, int imported, int failed, int skipped, ImportStatus expected) {
        assertThat(ImportService.finalStatus(imported, failed, skipped)).isEqualTo(expected);
    }

    @Test
    @DisplayName("DT-02 helper sanity: login never succeeds without a password hash")
    void noHashNeverAuthenticates() {
        UserRepository users = mock(UserRepository.class);
        User oauthOnly = new User();
        oauthOnly.setEmail("g@example.com");
        oauthOnly.setStatus(UserStatus.ACTIVE);
        when(users.findByEmail("g@example.com")).thenReturn(Optional.of(oauthOnly));
        AuthService auth = new AuthService(users, new BCryptPasswordEncoder(4), mock(JwtService.class), mock(RefreshTokenService.class),
                new RateLimiter(new AppProperties("Asia/Dhaka", List.of("x"), null, null, null, null, new AppProperties.RateLimit(false)), Clock.systemUTC()));
        assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest("g@example.com", "anything"), "1.1.1.1"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Invalid");
    }
}

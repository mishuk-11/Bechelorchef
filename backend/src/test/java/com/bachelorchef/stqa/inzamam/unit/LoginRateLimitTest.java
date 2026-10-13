package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.AuthDtos;
import com.bachelorchef.entity.Role;
import com.bachelorchef.entity.User;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.JwtService;
import com.bachelorchef.security.RateLimiter;
import com.bachelorchef.security.RefreshTokenService;
import com.bachelorchef.service.AuthService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** DEF-022 regression: only failed logins count towards the per-account limit. */
class LoginRateLimitTest {

    private AuthService auth;

    @BeforeEach
    void setUp() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        User user = new User();
        user.setEmail("sam@example.com");
        user.setFullName("Sam");
        user.setRole(Role.USER);
        user.setPasswordHash(encoder.encode("correct-password"));
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("sam@example.com")).thenReturn(Optional.of(user));
        JwtService jwt = mock(JwtService.class);
        when(jwt.issue(any())).thenReturn(new JwtService.AccessToken("token", Instant.now()));
        AppProperties props = new AppProperties("Asia/Dhaka", java.util.List.of("http://localhost:3000"), null, null, null, null,
                new AppProperties.RateLimit(true));
        RateLimiter limiter = new RateLimiter(props, Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC));
        auth = new AuthService(users, encoder, jwt, mock(RefreshTokenService.class), limiter);
    }

    private void login(String password) {
        auth.login(new AuthDtos.LoginRequest("sam@example.com", password), "10.0.0.1");
    }

    @Test
    @DisplayName("Many successful logins never lock the account")
    void successfulLoginsDoNotCount() {
        for (int i = 0; i < 25; i++) {
            assertThatCode(() -> login("correct-password")).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("After 10 failed attempts the account is temporarily locked, even for the right password")
    void failuresLockTheAccount() {
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> login("wrong")).isInstanceOf(ApiException.class).hasMessageContaining("Invalid");
        }
        assertThatThrownBy(() -> login("correct-password")).isInstanceOf(ApiException.class).hasMessageContaining("Too many");
    }
}

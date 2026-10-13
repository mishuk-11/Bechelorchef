package com.bachelorchef.controller;

import com.bachelorchef.dto.AuthDtos;
import com.bachelorchef.dto.UserDtos;
import com.bachelorchef.exception.ApiErrorWriter;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.security.RefreshCookies;
import com.bachelorchef.security.RefreshTokenService;
import com.bachelorchef.service.AuthService;
import com.bachelorchef.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Email/password authentication with a rotating HttpOnly refresh cookie")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshCookies cookies;
    private final RefreshTokenService refreshTokens;

    public AuthController(AuthService authService, UserService userService, RefreshCookies cookies,
                          RefreshTokenService refreshTokens) {
        this.authService = authService;
        this.userService = userService;
        this.cookies = cookies;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping("/register")
    @Operation(summary = "Create a USER account and start a session")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request,
                                                          HttpServletRequest http) {
        return withCookie(HttpStatus.CREATED, authService.register(request, http.getRemoteAddr()));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in; returns an access token and sets the refresh cookie")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                       HttpServletRequest http) {
        return withCookie(HttpStatus.OK, authService.login(request, http.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie and issue a new access token")
    public ResponseEntity<?> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String token) {
        try {
            return withCookie(HttpStatus.OK, authService.refresh(token));
        } catch (ApiException e) {
            // Clear a bad cookie so the browser stops presenting it.
            return ResponseEntity.status(e.getStatus())
                    .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                    .body(ApiErrorWriter.build(e.getStatus(), e.getCode(), e.getMessage(), e.getFieldErrors()));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh token and clear the cookie")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's profile")
    public UserDtos.UserProfile me(@AuthenticationPrincipal AuthUser user) {
        return userService.profile(user.id());
    }

    private ResponseEntity<AuthDtos.AuthResponse> withCookie(HttpStatus status, AuthService.Session session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken(), refreshTokens.ttl()).toString())
                .body(session.response());
    }
}

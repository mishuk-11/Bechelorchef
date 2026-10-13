package com.bachelorchef.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Full name is required.") @Size(max = 100, message = "Must be at most 100 characters.") String fullName,
            // Format is checked after trimming/lower-casing in AuthService, consistent with login.
            @NotBlank(message = "Email is required.") @Size(max = 260) String email,
            @NotBlank(message = "Password is required.") @Size(min = 8, max = 72, message = "Must be 8 to 72 characters.") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required.") String email,
            @NotBlank(message = "Password is required.") String password) {
    }

    public record AuthResponse(String accessToken, Instant accessTokenExpiresAt, UserDtos.UserProfile user) {
    }
}

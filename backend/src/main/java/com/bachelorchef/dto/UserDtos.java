package com.bachelorchef.dto;

import com.bachelorchef.entity.Role;
import com.bachelorchef.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    /** The signed-in user's own profile. Never includes credentials or internal moderation data. */
    public record UserProfile(Long id, String fullName, String email, String phoneNumber, Role role,
                              UserStatus status, String avatarUrl, String bio, Instant createdAt) {
    }

    /** Public author attribution shown on recipes and reviews. */
    public record AuthorSummary(Long id, String displayName, String avatarUrl) {
    }

    /** PATCH semantics: null leaves a field unchanged; an empty string clears optional fields. */
    public record UpdateProfileRequest(
            String fullName,
            @Size(max = 20, message = "Must be at most 20 characters.") String phoneNumber,
            @Size(max = 500) String avatarUrl,
            @Size(max = 255) String avatarAssetId,
            String bio) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required.") String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "Must be 8 to 72 characters.") String newPassword) {
    }

    public record DeleteAccountRequest(@NotBlank(message = "Password is required.") String password) {
    }

    public record AdminUserRow(Long id, String fullName, String email, Role role, UserStatus status,
                               Instant createdAt, long recipeCount, long reviewCount, long cookCount) {
    }

    public record UpdateStatusRequest(@NotNull(message = "Status is required.") UserStatus status,
                                      @Size(max = 500) String reason) {
    }

    public record UpdateRoleRequest(@NotNull(message = "Role is required.") Role role) {
    }
}

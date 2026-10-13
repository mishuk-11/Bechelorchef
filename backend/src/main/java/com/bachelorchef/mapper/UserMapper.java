package com.bachelorchef.mapper;

import com.bachelorchef.dto.UserDtos;
import com.bachelorchef.entity.User;
import com.bachelorchef.entity.UserStatus;

public final class UserMapper {

    public static final String DELETED_NAME = "Deleted user";

    private UserMapper() {
    }

    public static UserDtos.UserProfile profile(User u) {
        return new UserDtos.UserProfile(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getRole(),
                u.getStatus(), u.getAvatarUrl(), u.getBio(), u.getCreatedAt());
    }

    /** Public attribution. Deleted accounts are anonymised and expose no id. */
    public static UserDtos.AuthorSummary author(User u) {
        if (u == null) {
            return null;
        }
        if (u.getStatus() == UserStatus.DELETED) {
            return new UserDtos.AuthorSummary(null, DELETED_NAME, null);
        }
        return new UserDtos.AuthorSummary(u.getId(), u.getFullName(), u.getAvatarUrl());
    }
}

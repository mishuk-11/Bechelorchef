package com.bachelorchef.security;

import com.bachelorchef.entity.Role;
import java.security.Principal;

/**
 * The authenticated principal. Built from the database on every request (not from token claims),
 * so role changes and suspensions take effect immediately.
 */
public record AuthUser(Long id, String email, Role role) implements Principal {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public String getName() {
        return String.valueOf(id);
    }
}

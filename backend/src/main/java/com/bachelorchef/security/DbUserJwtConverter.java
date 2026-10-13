package com.bachelorchef.security;

import com.bachelorchef.entity.User;
import com.bachelorchef.repository.UserRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the JWT subject to a live database user. Suspended or deleted accounts are rejected
 * (401) even if their access token has not expired yet, and the role always comes from the DB.
 */
@Component
public class DbUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public DbUserJwtConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Invalid token subject");
        }
        User user = users.findById(userId).orElseThrow(() -> new BadCredentialsException("Unknown user"));
        if (!user.isActive()) {
            throw new DisabledException("Account is not active");
        }
        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getRole());
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}

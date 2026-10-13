package com.bachelorchef.security;

import com.bachelorchef.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds the HttpOnly refresh-token cookie, scoped to the auth endpoints only. */
@Component
public class RefreshCookies {

    public static final String NAME = "bc_refresh";
    private static final String PATH = "/api/v1/auth";

    private final AppProperties properties;

    public RefreshCookies(AppProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token, Duration ttl) {
        return base(token).maxAge(ttl).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.auth().cookieSecure())
                .sameSite(properties.auth().cookieSameSite())
                .path(PATH);
    }
}

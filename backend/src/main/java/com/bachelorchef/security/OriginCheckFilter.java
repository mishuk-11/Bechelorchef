package com.bachelorchef.security;

import com.bachelorchef.exception.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF defence for the only endpoints authenticated by cookie (refresh and logout).
 * A browser always sends an Origin header on these cross-origin/same-origin POSTs; if it is
 * present it must be an allowed frontend origin. All other endpoints use a Bearer header,
 * which browsers never attach automatically, so they are not CSRF-exposed.
 */
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> COOKIE_ENDPOINTS = Set.of("/api/v1/auth/refresh", "/api/v1/auth/logout");

    private final List<String> allowedOrigins;
    private final ApiErrorWriter writer;

    public OriginCheckFilter(List<String> allowedOrigins, ApiErrorWriter writer) {
        this.allowedOrigins = allowedOrigins;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !COOKIE_ENDPOINTS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            writer.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Request origin is not allowed.");
            return;
        }
        chain.doFilter(request, response);
    }
}

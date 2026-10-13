package com.bachelorchef.config;

import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String displayTimezone,
        List<String> frontendOrigins,
        Auth auth,
        Bootstrap bootstrap,
        Imports imports,
        Media media,
        RateLimit rateLimit) {

    public ZoneId zone() {
        return ZoneId.of(displayTimezone == null ? "Asia/Dhaka" : displayTimezone);
    }

    public record Auth(
            String jwtSecret,
            String issuer,
            long accessTokenTtlMinutes,
            long refreshTokenTtlDays,
            boolean cookieSecure,
            String cookieSameSite) {
    }

    public record Bootstrap(String adminEmail, String adminPassword, String adminName) {
        public boolean configured() {
            return adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank();
        }
    }

    public record Imports(long maxBytes, int maxRows, int previewTtlHours) {
    }

    public record Media(
            String cloudName,
            String apiKey,
            String apiSecret,
            String folder,
            long maxImageBytes,
            List<String> allowedImageHosts) {

        public boolean cloudinaryConfigured() {
            return notBlank(cloudName) && notBlank(apiKey) && notBlank(apiSecret);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    public record RateLimit(boolean enabled) {
    }
}

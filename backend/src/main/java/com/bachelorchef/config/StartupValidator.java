package com.bachelorchef.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails fast with a clear message when required configuration is missing or unsafe. */
@Component
public class StartupValidator implements InitializingBean {

    private final AppProperties properties;
    private final Environment environment;

    public StartupValidator(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = new ArrayList<>();
        String secret = properties.auth().jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            problems.add("APP_JWT_ACCESS_SECRET must be set to at least 32 bytes.");
        }
        List<String> origins = properties.frontendOrigins();
        if (origins == null || origins.isEmpty() || origins.stream().anyMatch(String::isBlank)) {
            problems.add("APP_FRONTEND_ORIGINS must list at least one origin.");
        }
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod) {
            if (origins != null && origins.stream().anyMatch(o -> o.contains("*"))) {
                problems.add("APP_FRONTEND_ORIGINS must not contain wildcards in production.");
            }
            if (secret != null && secret.startsWith("dev-only")) {
                problems.add("The development JWT secret must not be used in production.");
            }
            if (!properties.auth().cookieSecure()) {
                problems.add("APP_COOKIE_SECURE must be true in production.");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid configuration: " + String.join(" ", problems));
        }
    }
}

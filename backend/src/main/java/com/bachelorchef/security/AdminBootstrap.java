package com.bachelorchef.security;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.entity.Role;
import com.bachelorchef.entity.User;
import com.bachelorchef.entity.UserStatus;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.service.AuditService;
import com.bachelorchef.service.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD when that
 * email does not exist yet. An existing account is never modified (no silent password reset or
 * promotion), and the password is never logged.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final AppProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminBootstrap(AppProperties properties, UserRepository users, PasswordEncoder passwordEncoder, AuditService audit) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = properties.bootstrap();
        if (bootstrap == null || !bootstrap.configured()) {
            log.info("Admin bootstrap skipped: BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD not set");
            return;
        }
        String email = TextNormalizer.email(bootstrap.adminEmail());
        if (users.existsByEmail(email)) {
            log.info("Admin bootstrap: account {} already exists; leaving it unchanged", email);
            return;
        }
        if (bootstrap.adminPassword().length() < MIN_PASSWORD_LENGTH) {
            log.warn("Admin bootstrap skipped: BOOTSTRAP_ADMIN_PASSWORD must be at least {} characters", MIN_PASSWORD_LENGTH);
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setFullName(bootstrap.adminName() == null || bootstrap.adminName().isBlank()
                ? "Bachelor Chef Admin" : bootstrap.adminName().strip());
        admin.setPasswordHash(passwordEncoder.encode(bootstrap.adminPassword()));
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        users.save(admin);
        audit.record(null, "ADMIN_BOOTSTRAPPED", "USER", admin.getId(), "email=" + email);
        log.info("Admin bootstrap: created administrator {}", email);
    }
}

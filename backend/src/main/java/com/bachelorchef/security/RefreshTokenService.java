package com.bachelorchef.security;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.entity.RefreshToken;
import com.bachelorchef.entity.User;
import com.bachelorchef.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, rotating refresh tokens. Only SHA-256 hashes are stored. Presenting an already-rotated
 * (revoked) token is treated as theft and revokes every session of that user.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final AppProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, AppProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    public Duration ttl() {
        return Duration.ofDays(properties.auth().refreshTokenTtlDays());
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(user, hash(raw), clock.instant().plus(ttl())));
        return raw;
    }

    /** Validates and rotates a token, returning the owning user and the replacement token. */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String raw) {
        RefreshToken token = find(raw).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        User user = token.getUser();
        if (token.getRevokedAt() != null) {
            log.warn("Reuse of a revoked refresh token detected for user {}; revoking all sessions", user.getId());
            tokens.revokeAllForUser(user.getId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!token.isUsable(now) || !user.isActive()) {
            throw new InvalidRefreshTokenException();
        }
        token.setRevokedAt(now);
        return new Rotation(user, issue(user));
    }

    @Transactional
    public void revoke(String raw) {
        find(raw).filter(t -> t.getRevokedAt() == null).ifPresent(t -> t.setRevokedAt(clock.instant()));
    }

    @Transactional
    public void revokeAll(Long userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpired() {
        tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    private Optional<RefreshToken> find(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 200) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash(raw));
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Rotation(User user, String refreshToken) {
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException() {
            super("Invalid refresh token");
        }
    }
}

package com.bachelorchef.service;

import com.bachelorchef.dto.AuthDtos;
import com.bachelorchef.entity.Role;
import com.bachelorchef.entity.User;
import com.bachelorchef.entity.UserStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.mapper.UserMapper;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.JwtService;
import com.bachelorchef.security.RateLimiter;
import com.bachelorchef.security.RefreshTokenService;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Generic message so login responses never reveal whether an email is registered. */
    static final String INVALID_CREDENTIALS = "Invalid email or password.";
    static final int MAX_FAILED_LOGINS = 10;
    /** Pragmatic address check (local@domain.tld) applied after trimming and lower-casing. */
    static final Pattern EMAIL = Pattern.compile("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final RateLimiter rateLimiter;
    /** Hash compared against when the email is unknown, so timing does not reveal registration. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokens, RateLimiter rateLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.rateLimiter = rateLimiter;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional
    public Session register(AuthDtos.RegisterRequest request, String clientIp) {
        rateLimiter.check("register:" + clientIp, 10, Duration.ofHours(1));
        String email = TextNormalizer.email(request.email());
        String name = TextNormalizer.clean(request.fullName());
        if (name == null) {
            throw ApiException.validation("fullName", "Full name is required.");
        }
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw ApiException.validation("email", "Must be a valid email address.");
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN",
                    "An account with this email already exists. Try signing in instead.");
        }
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER); // never taken from the request
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);
        return startSession(user);
    }

    @Transactional
    public Session login(AuthDtos.LoginRequest request, String clientIp) {
        String email = TextNormalizer.email(request.email());
        String failureKey = "login-failures:" + email;
        rateLimiter.check("login-ip:" + clientIp, 30, Duration.ofMinutes(5));
        // Only failed attempts count against an account, so legitimate sign-ins never lock it.
        rateLimiter.requireBelow(failureKey, MAX_FAILED_LOGINS);
        Optional<User> found = users.findByEmail(email);
        String hash = found.map(User::getPasswordHash).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(), hash != null ? hash : dummyHash);
        if (found.isEmpty() || hash == null || !matches) {
            rateLimiter.check(failureKey, MAX_FAILED_LOGINS, Duration.ofMinutes(15));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            // The correct password was supplied, so telling the owner why is not an enumeration leak.
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                    "This account is suspended. Contact support if you think this is a mistake.");
        }
        return startSession(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawRefreshToken) {
        try {
            RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);
            JwtService.AccessToken access = jwtService.issue(rotation.user());
            return new Session(new AuthDtos.AuthResponse(access.value(), access.expiresAt(),
                    UserMapper.profile(rotation.user())), rotation.refreshToken());
        } catch (RefreshTokenService.InvalidRefreshTokenException e) {
            throw ApiException.unauthorized("Your session has expired. Please sign in again.");
        }
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    private Session startSession(User user) {
        JwtService.AccessToken access = jwtService.issue(user);
        String refresh = refreshTokens.issue(user);
        return new Session(new AuthDtos.AuthResponse(access.value(), access.expiresAt(), UserMapper.profile(user)), refresh);
    }

    /** Response body plus the raw refresh token, which the controller puts into an HttpOnly cookie. */
    public record Session(AuthDtos.AuthResponse response, String refreshToken) {
    }
}

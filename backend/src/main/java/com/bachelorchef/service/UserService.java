package com.bachelorchef.service;

import com.bachelorchef.dto.UserDtos;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.entity.Role;
import com.bachelorchef.entity.User;
import com.bachelorchef.entity.UserStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.mapper.UserMapper;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.RefreshTokenService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 -]{5,18}$");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final MediaUrlPolicy urlPolicy;
    private final EntityManager em;
    private final Clock clock;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
                       MediaUrlPolicy urlPolicy, EntityManager em, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.urlPolicy = urlPolicy;
        this.em = em;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserDtos.UserProfile profile(Long userId) {
        return UserMapper.profile(requireActive(userId));
    }

    @Transactional
    public UserDtos.UserProfile updateProfile(Long userId, UserDtos.UpdateProfileRequest request) {
        User user = requireActive(userId);
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.fullName() != null) {
            String name = TextNormalizer.clean(request.fullName());
            if (name == null || TextNormalizer.length(name) < 2 || TextNormalizer.length(name) > 100) {
                errors.put("fullName", "Must be 2 to 100 characters.");
            } else {
                user.setFullName(name);
            }
        }
        if (request.phoneNumber() != null) {
            String phone = TextNormalizer.trimToNull(request.phoneNumber());
            if (phone != null && !PHONE.matcher(phone).matches()) {
                errors.put("phoneNumber", "Must be a valid phone number, e.g. +8801XXXXXXXXX.");
            } else if (phone != null && users.existsByPhoneNumberAndIdNot(phone, userId)) {
                errors.put("phoneNumber", "This phone number is already used by another account.");
            } else {
                user.setPhoneNumber(phone);
            }
        }
        if (request.avatarUrl() != null) {
            String url = TextNormalizer.trimToNull(request.avatarUrl());
            urlPolicy.check(url).ifPresentOrElse(msg -> errors.put("avatarUrl", msg), () -> {
                user.setAvatarUrl(url);
                user.setAvatarAssetId(url == null ? null : TextNormalizer.trimToNull(request.avatarAssetId()));
            });
        }
        if (request.bio() != null) {
            String bio = TextNormalizer.trimToNull(request.bio());
            if (TextNormalizer.length(bio) > 500) {
                errors.put("bio", "Must be at most 500 characters.");
            } else {
                user.setBio(bio);
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return UserMapper.profile(user);
    }

    @Transactional
    public void changePassword(Long userId, UserDtos.ChangePasswordRequest request) {
        User user = requireActive(userId);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.validation("currentPassword", "Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAll(userId);
    }

    /**
     * Soft-deletes and anonymises the account. Published recipes and reviews stay (attributed to
     * "Deleted user"); unpublished drafts/pending recipes are archived; private cookbook and goals
     * are removed; cook history is kept for aggregate statistics but no longer links to personal data.
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = requireActive(userId);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.validation("password", "Password is incorrect.");
        }
        if (user.getRole() == Role.ADMIN && users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "The last active administrator cannot be deleted.");
        }
        anonymise(user);
    }

    void anonymise(User user) {
        user.setFullName(UserMapper.DELETED_NAME);
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        user.setPhoneNumber(null);
        user.setPasswordHash(null);
        user.setGoogleId(null);
        user.setAvatarUrl(null);
        user.setAvatarAssetId(null);
        user.setBio(null);
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(clock.instant());
        em.createQuery("delete from SavedRecipe s where s.user.id = :id").setParameter("id", user.getId()).executeUpdate();
        em.createQuery("delete from UserGoal g where g.user.id = :id").setParameter("id", user.getId()).executeUpdate();
        em.createQuery("update Recipe r set r.status = :archived where r.createdBy.id = :id and r.status in :unpublished")
                .setParameter("archived", RecipeStatus.ARCHIVED)
                .setParameter("id", user.getId())
                .setParameter("unpublished", List.of(RecipeStatus.DRAFT, RecipeStatus.PENDING, RecipeStatus.REJECTED))
                .executeUpdate();
        refreshTokens.revokeAll(user.getId());
    }

    User requireActive(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!user.isActive()) {
            throw ApiException.forbidden("This account is not active.");
        }
        return user;
    }
}

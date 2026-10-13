package com.bachelorchef.service;

import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.Pagination;
import com.bachelorchef.dto.UserDtos;
import com.bachelorchef.entity.Role;
import com.bachelorchef.entity.User;
import com.bachelorchef.entity.UserStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.security.RefreshTokenService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin user management. Never exposes credential material. */
@Service
public class AdminUserService {

    private static final Map<String, Sort> SORTS = Map.of(
            "newest", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
            "name", Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("id")),
            "email", Sort.by(Sort.Order.asc("email")));

    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public AdminUserService(UserRepository users, RefreshTokenService refreshTokens, NamedParameterJdbcTemplate jdbc,
                            AuditService audit) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.AdminUserRow> search(String query, String status, String role, String sort,
                                                      Integer page, Integer size) {
        UserStatus statusFilter = parse(UserStatus.class, status, "status");
        Role roleFilter = parse(Role.class, role, "role");
        String q = TextNormalizer.key(query);
        Specification<User> spec = (root, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q != null) {
                String like = "%" + CatalogService.escapeLike(q) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("fullName")), like, '\\'), cb.like(root.get("email"), like, '\\')));
            }
            if (statusFilter != null) {
                p.add(cb.equal(root.get("status"), statusFilter));
            }
            if (roleFilter != null) {
                p.add(cb.equal(root.get("role"), roleFilter));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<User> result = users.findAll(spec, Pagination.of(page, size, Pagination.sort(sort, SORTS, "newest")));
        Map<Long, long[]> activity = activityCounts(result.getContent().stream().map(User::getId).toList());
        return PageResponse.of(result, u -> {
            long[] c = activity.getOrDefault(u.getId(), new long[3]);
            return new UserDtos.AdminUserRow(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getStatus(),
                    u.getCreatedAt(), c[0], c[1], c[2]);
        });
    }

    @Transactional
    public UserDtos.AdminUserRow updateStatus(AuthUser admin, Long userId, UserDtos.UpdateStatusRequest request) {
        if (admin.id().equals(userId)) {
            throw ApiException.forbidden("You cannot change your own account status.");
        }
        if (request.status() == UserStatus.DELETED) {
            throw ApiException.validation("status", "Use ACTIVE or SUSPENDED. Accounts are deleted by their owners.");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_DELETED", "Deleted accounts cannot be changed.");
        }
        if (user.getRole() == Role.ADMIN && request.status() == UserStatus.SUSPENDED
                && users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "The last active administrator cannot be suspended.");
        }
        UserStatus previous = user.getStatus();
        user.setStatus(request.status());
        if (request.status() == UserStatus.SUSPENDED) {
            refreshTokens.revokeAll(userId);
        }
        audit.record(admin.id(), "USER_STATUS_CHANGED", "USER", userId,
                previous + " -> " + request.status() + (request.reason() == null ? "" : "; reason=" + request.reason().strip()));
        return row(user);
    }

    @Transactional
    public UserDtos.AdminUserRow updateRole(AuthUser admin, Long userId, UserDtos.UpdateRoleRequest request) {
        if (admin.id().equals(userId)) {
            throw ApiException.forbidden("You cannot change your own role.");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_DELETED", "Deleted accounts cannot be changed.");
        }
        Role previous = user.getRole();
        user.setRole(request.role());
        // Revoke sessions so the new role is also reflected in any client state on next refresh.
        refreshTokens.revokeAll(userId);
        audit.record(admin.id(), "USER_ROLE_CHANGED", "USER", userId, previous + " -> " + request.role());
        return row(user);
    }

    private UserDtos.AdminUserRow row(User u) {
        long[] c = activityCounts(List.of(u.getId())).getOrDefault(u.getId(), new long[3]);
        return new UserDtos.AdminUserRow(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getStatus(),
                u.getCreatedAt(), c[0], c[1], c[2]);
    }

    /** [recipes submitted, reviews written, cook events] per user, in one query. */
    private Map<Long, long[]> activityCounts(List<Long> ids) {
        Map<Long, long[]> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT u.user_id,
                       (SELECT COUNT(*) FROM recipes r WHERE r.created_by = u.user_id AND NOT r.is_official) AS recipes,
                       (SELECT COUNT(*) FROM reviews v WHERE v.user_id = u.user_id) AS reviews,
                       (SELECT COUNT(*) FROM user_cooked_history h WHERE h.user_id = u.user_id) AS cooks
                FROM users u WHERE u.user_id IN (:ids)
                """, new MapSqlParameterSource("ids", ids),
                rs -> {
                    result.put(rs.getLong("user_id"), new long[]{rs.getLong("recipes"), rs.getLong("reviews"), rs.getLong("cooks")});
                });
        return result;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(field, "Unknown value.");
        }
    }
}

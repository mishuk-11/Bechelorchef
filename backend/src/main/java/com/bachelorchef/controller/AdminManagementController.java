package com.bachelorchef.controller;

import com.bachelorchef.analytics.AnalyticsService;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.dto.AdminDtos;
import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.UserDtos;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.service.AdminUserService;
import com.bachelorchef.service.AuditService;
import com.bachelorchef.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: catalogue, users & analytics")
public class AdminManagementController {

    private final CatalogService catalog;
    private final AdminUserService users;
    private final AnalyticsService analytics;
    private final AuditService audit;

    public AdminManagementController(CatalogService catalog, AdminUserService users, AnalyticsService analytics, AuditService audit) {
        this.catalog = catalog;
        this.users = users;
        this.analytics = analytics;
        this.audit = audit;
    }

    // ----- ingredients & tags

    @GetMapping("/ingredients")
    public PageResponse<CatalogDtos.IngredientView> ingredients(@RequestParam(required = false) String query,
                                                                @RequestParam(required = false) String category,
                                                                @RequestParam(required = false) String sort,
                                                                @RequestParam(required = false) Integer page,
                                                                @RequestParam(required = false) Integer size) {
        return catalog.searchIngredients(query, null, true, sort, page, size == null ? null : size - 1);
    }

    @GetMapping("/ingredients/categories")
    public List<String> categories() {
        return catalog.ingredientCategories().reversed();
    }

    @PostMapping("/ingredients")
    public ResponseEntity<CatalogDtos.IngredientView> createIngredient(@AuthenticationPrincipal AuthUser admin,
                                                                       @Valid @RequestBody CatalogDtos.IngredientRequest request) {
        if (request.name() != null && request.name().strip().length() >= 80) {
            throw ApiException.validation("name", "Must be at most 80 characters.");
        }
        return ResponseEntity.ok(catalog.createIngredient(request, admin.id()));
    }

    @PatchMapping("/ingredients/{id}")
    @Operation(summary = "Rename/recategorise; recipe links and pantry matching use ids and are unaffected")
    public CatalogDtos.IngredientView updateIngredient(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id,
                                                       @Valid @RequestBody CatalogDtos.IngredientRequest request) {
        return catalog.updateIngredient(id, request, admin.id());
    }

    @PostMapping("/ingredients/{id}/deactivate")
    public CatalogDtos.IngredientView deactivate(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return catalog.setIngredientActive(id, false, admin.id());
    }

    @PostMapping("/ingredients/{id}/activate")
    public CatalogDtos.IngredientView activate(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return catalog.setIngredientActive(id, true, admin.id());
    }

    @PostMapping("/tags")
    public ResponseEntity<CatalogDtos.TagView> createTag(@AuthenticationPrincipal AuthUser admin,
                                                         @Valid @RequestBody CatalogDtos.TagRequest request) {
        CatalogDtos.TagView tag = catalog.createTag(request, admin.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CatalogDtos.TagView(tag.id(), request.name()));
    }

    @PatchMapping("/tags/{id}")
    public CatalogDtos.TagView renameTag(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id,
                                         @Valid @RequestBody CatalogDtos.TagRequest request) {
        return catalog.renameTag(id, request, admin.id());
    }

    // ----- users

    @GetMapping("/users")
    public PageResponse<UserDtos.AdminUserRow> users(@RequestParam(name = "q", required = false) String query,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String role,
                                                     @RequestParam(required = false) String sort,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return users.search(query, status, null, sort, page, size);
    }

    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "Suspend or reactivate an account")
    public UserDtos.AdminUserRow updateStatus(@AuthenticationPrincipal AuthUser admin, @PathVariable Long userId,
                                              @Valid @RequestBody UserDtos.UpdateStatusRequest request) {
        UserDtos.AdminUserRow row = users.updateStatus(admin, userId, request);
        return new UserDtos.AdminUserRow(row.id(), row.fullName(), row.email(), row.role(), row.status(), row.createdAt(),
                row.reviewCount(), row.recipeCount(), row.cookCount());
    }

    @PatchMapping("/users/{userId}/role")
    @Operation(summary = "Change a user's role (explicit, audited action)")
    public UserDtos.AdminUserRow updateRole(@AuthenticationPrincipal AuthUser admin, @PathVariable Long userId,
                                            @Valid @RequestBody UserDtos.UpdateRoleRequest request) {
        return users.updateRole(admin, userId, request);
    }

    // ----- analytics

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Dashboard metrics; from/to are inclusive dates (Asia/Dhaka), default last 30 days")
    public AdminDtos.DashboardSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.summary(from, to == null ? null : to.minusDays(1));
    }

    @GetMapping("/analytics/ingredients")
    public AdminDtos.IngredientAnalytics ingredientAnalytics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.ingredients(from, to);
    }

    @GetMapping("/analytics/recipes")
    public AdminDtos.RecipeAnalytics recipeAnalytics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.recipes(from, to);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AdminDtos.AuditView> auditLogs(@RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return audit.list(page == null ? 1 : page + 1, size);
    }
}

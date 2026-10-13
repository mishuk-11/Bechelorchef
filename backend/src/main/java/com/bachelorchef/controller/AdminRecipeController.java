package com.bachelorchef.controller;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.RecipeDtos;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.entity.ReviewStatus;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.service.RecipeQueryService;
import com.bachelorchef.service.RecipeService;
import com.bachelorchef.service.ReportService;
import com.bachelorchef.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: recipes & moderation")
public class AdminRecipeController {

    private final RecipeQueryService queries;
    private final RecipeService recipes;
    private final ReviewService reviews;
    private final ReportService reports;

    public AdminRecipeController(RecipeQueryService queries, RecipeService recipes, ReviewService reviews, ReportService reports) {
        this.queries = queries;
        this.recipes = recipes;
        this.reviews = reviews;
        this.reports = reports;
    }

    @GetMapping("/recipes")
    @Operation(summary = "All recipes in any status")
    public PageResponse<RecipeDtos.RecipeSummary> list(@RequestParam(required = false) RecipeStatus status,
                                                       @RequestParam(name = "q", required = false) String query,
                                                       @RequestParam(required = false) String source,
                                                       @RequestParam(required = false) String sort,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return queries.search(new RecipeQueryService.Filters(query, null, null, null, source, null, null,
                status == null ? List.of() : List.of(status), null), sort, page, size);
    }

    @PostMapping("/recipes")
    @Operation(summary = "Create an official recipe (published immediately unless submit=false)")
    public ResponseEntity<RecipeDtos.RecipeDetail> create(@AuthenticationPrincipal AuthUser admin,
                                                          @Valid @RequestBody RecipeDtos.RecipeRequest request) {
        Long id = recipes.createOfficial(admin, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(queries.detail(id, admin));
    }

    @PostMapping("/recipes/{recipeId}/approve")
    public RecipeDtos.RecipeDetail approve(@AuthenticationPrincipal AuthUser admin, @PathVariable Long recipeId,
                                           @Valid @RequestBody(required = false) RecipeDtos.ModerationDecision decision) {
        recipes.approve(admin, recipeId, decision == null ? null : decision.reason());
        return queries.detail(recipeId, admin);
    }

    @PostMapping("/recipes/{recipeId}/reject")
    public RecipeDtos.RecipeDetail reject(@AuthenticationPrincipal AuthUser admin, @PathVariable Long recipeId,
                                          @Valid @RequestBody RecipeDtos.ModerationDecision decision) {
        recipes.reject(admin, recipeId, decision.reason());
        return queries.detail(recipeId, admin);
    }

    @PostMapping("/recipes/{recipeId}/archive")
    public RecipeDtos.RecipeDetail archive(@AuthenticationPrincipal AuthUser admin, @PathVariable Long recipeId,
                                           @Valid @RequestBody(required = false) RecipeDtos.ModerationDecision decision) {
        recipes.archive(admin, recipeId, decision == null ? null : decision.reason());
        return queries.detail(recipeId, admin);
    }

    @PostMapping("/recipes/{recipeId}/restore")
    public RecipeDtos.RecipeDetail restore(@AuthenticationPrincipal AuthUser admin, @PathVariable Long recipeId) {
        recipes.restore(admin, recipeId);
        return queries.detail(recipeId, admin);
    }

    @GetMapping("/moderation/recipes")
    @Operation(summary = "Recipe moderation queue (default: PENDING, oldest first)")
    public PageResponse<RecipeDtos.RecipeSummary> recipeQueue(@RequestParam(required = false) RecipeStatus status,
                                                              @RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        RecipeStatus s = status == null ? RecipeStatus.PENDING : status;
        return queries.search(new RecipeQueryService.Filters(null, null, null, null, null, null, null, List.of(s), null),
                "newest", page, size);
    }

    @GetMapping("/moderation/reviews")
    @Operation(summary = "Review moderation queue: PENDING (default), FLAGGED, APPROVED, REJECTED or REMOVED")
    public PageResponse<CommunityDtos.AdminReviewView> reviewQueue(@RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) Integer page,
                                                                   @RequestParam(required = false) Integer size) {
        return reviews.moderationQueue(status, page, size);
    }

    @PostMapping("/moderation/reviews/{reviewId}/approve")
    public ResponseEntity<Void> approveReview(@AuthenticationPrincipal AuthUser admin, @PathVariable Long reviewId,
                                              @Valid @RequestBody(required = false) RecipeDtos.ModerationDecision decision) {
        reviews.moderate(admin, reviewId, ReviewStatus.APPROVED, decision == null ? null : decision.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/moderation/reviews/{reviewId}/reject")
    public ResponseEntity<Void> rejectReview(@AuthenticationPrincipal AuthUser admin, @PathVariable Long reviewId,
                                             @Valid @RequestBody RecipeDtos.ModerationDecision decision) {
        reviews.moderate(admin, reviewId, ReviewStatus.REJECTED, decision.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/moderation/reviews/{reviewId}/remove")
    public ResponseEntity<Void> removeReview(@AuthenticationPrincipal AuthUser admin, @PathVariable Long reviewId,
                                             @Valid @RequestBody RecipeDtos.ModerationDecision decision) {
        reviews.moderate(admin, reviewId, ReviewStatus.REMOVED, decision.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/moderation/reports")
    @Operation(summary = "User reports (default: OPEN)")
    public PageResponse<CommunityDtos.ReportView> reports(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return reports.list(status, page, size);
    }

    @PostMapping("/moderation/reports/{reportId}/resolve")
    public ResponseEntity<Void> resolve(@AuthenticationPrincipal AuthUser admin, @PathVariable Long reportId,
                                        @Valid @RequestBody CommunityDtos.ResolveReportRequest request) {
        reports.resolve(admin, reportId, request);
        return ResponseEntity.noContent().build();
    }
}

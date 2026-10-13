package com.bachelorchef.controller;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.RecipeDtos;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.service.CookingHistoryService;
import com.bachelorchef.service.PantryMatchService;
import com.bachelorchef.service.RecipeQueryService;
import com.bachelorchef.service.RecipeService;
import com.bachelorchef.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipes")
@Tag(name = "Recipes", description = "Public discovery, pantry matching, submissions, cook log and reviews")
public class RecipeController {

    private final RecipeQueryService queries;
    private final RecipeService recipes;
    private final PantryMatchService matcher;
    private final CookingHistoryService history;
    private final ReviewService reviews;

    public RecipeController(RecipeQueryService queries, RecipeService recipes, PantryMatchService matcher,
                            CookingHistoryService history, ReviewService reviews) {
        this.queries = queries;
        this.recipes = recipes;
        this.matcher = matcher;
        this.history = history;
        this.reviews = reviews;
    }

    @GetMapping
    @Operation(summary = "Search approved recipes",
            description = "sort: newest | rating | popular | time | cost | title. source: official | community.")
    public PageResponse<RecipeDtos.RecipeSummary> search(@RequestParam(name = "q", required = false) String query,
                                                         @RequestParam(required = false) Long ingredientId,
                                                         @RequestParam(required = false) Long tagId,
                                                         @RequestParam(required = false) String difficulty,
                                                         @RequestParam(required = false) String source,
                                                         @RequestParam(required = false) BigDecimal maxCostBdt,
                                                         @RequestParam(required = false) Integer maxTotalTimeMins,
                                                         @RequestParam(required = false) String sort,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return queries.search(new RecipeQueryService.Filters(query, ingredientId, tagId, difficulty, source, maxCostBdt,
                maxTotalTimeMins, List.of(RecipeStatus.APPROVED), null), sort, page, size);
    }

    @GetMapping("/{recipeId}")
    @Operation(summary = "Recipe detail (approved recipes; authors and admins also see unpublished ones)")
    public RecipeDtos.RecipeDetail detail(@PathVariable Long recipeId, @AuthenticationPrincipal AuthUser viewer) {
        return queries.detail(recipeId, viewer);
    }

    @GetMapping("/{recipeId}/portions")
    @Operation(summary = "Scaled ingredient quantities and estimated BDT cost for 1–20 servings")
    public RecipeDtos.PortionResponse portions(@PathVariable Long recipeId, @RequestParam Integer servings,
                                               @AuthenticationPrincipal AuthUser viewer) {
        return queries.portions(recipeId, servings, viewer);
    }

    @PostMapping("/match")
    @Operation(summary = "Pantry matcher: rank recipes by the ingredients you have")
    public RecipeDtos.MatchResponse match(@RequestBody RecipeDtos.MatchRequest request) {
        return matcher.match(request);
    }

    @PostMapping
    @Operation(summary = "Submit a community recipe (PENDING moderation, or DRAFT when submit=false)")
    public ResponseEntity<RecipeDtos.RecipeDetail> create(@AuthenticationPrincipal AuthUser user,
                                                          @Valid @RequestBody RecipeDtos.RecipeRequest request) {
        Long id = recipes.createCommunity(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(queries.detail(id, user));
    }

    @PatchMapping("/{recipeId}")
    @Operation(summary = "Edit a recipe (owner or admin). Send the full editable content.")
    public RecipeDtos.RecipeDetail update(@PathVariable Long recipeId, @AuthenticationPrincipal AuthUser user,
                                          @Valid @RequestBody RecipeDtos.RecipeRequest request) {
        recipes.update(user, recipeId, request);
        return queries.detail(recipeId, user);
    }

    @DeleteMapping("/{recipeId}")
    @Operation(summary = "Withdraw (owner) or archive (admin). Recipes are never hard-deleted.")
    public ResponseEntity<Void> delete(@PathVariable Long recipeId, @AuthenticationPrincipal AuthUser user) {
        recipes.withdraw(user, recipeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{recipeId}/cook")
    @Operation(summary = "Log that I cooked this recipe")
    public ResponseEntity<CommunityDtos.HistoryView> cook(@PathVariable Long recipeId, @AuthenticationPrincipal AuthUser user,
                                                          @Valid @RequestBody(required = false) CommunityDtos.CookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(history.log(user, recipeId, request));
    }

    @GetMapping("/{recipeId}/reviews")
    @Operation(summary = "Approved reviews for a recipe")
    public PageResponse<CommunityDtos.ReviewView> reviews(@PathVariable Long recipeId, @AuthenticationPrincipal AuthUser viewer,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return reviews.listApproved(recipeId, viewer, page, size);
    }

    @PostMapping("/{recipeId}/reviews")
    @Operation(summary = "Write my review (one per recipe; enters moderation)")
    public ResponseEntity<CommunityDtos.ReviewView> review(@PathVariable Long recipeId, @AuthenticationPrincipal AuthUser user,
                                                           @Valid @RequestBody CommunityDtos.ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviews.create(user, recipeId, request));
    }
}

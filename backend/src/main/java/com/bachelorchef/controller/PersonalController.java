package com.bachelorchef.controller;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.service.CookingHistoryService;
import com.bachelorchef.service.GoalService;
import com.bachelorchef.service.SavedRecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Saved cookbook, cook history and goals of the signed-in user. */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Personal cooking")
public class PersonalController {

    private final SavedRecipeService saved;
    private final CookingHistoryService history;
    private final GoalService goals;

    public PersonalController(SavedRecipeService saved, CookingHistoryService history, GoalService goals) {
        this.saved = saved;
        this.history = history;
        this.goals = goals;
    }

    @GetMapping("/saved-recipes")
    public PageResponse<CommunityDtos.SavedRecipeView> savedRecipes(@AuthenticationPrincipal AuthUser user,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        return saved.list(user, page, size == null ? null : Math.min(size, 10));
    }

    @PutMapping("/saved-recipes/{recipeId}")
    @Operation(summary = "Save a recipe to my cookbook (idempotent)")
    public ResponseEntity<Void> save(@AuthenticationPrincipal AuthUser user, @PathVariable Long recipeId) {
        saved.save(user, recipeId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/saved-recipes/{recipeId}")
    public ResponseEntity<Void> unsave(@AuthenticationPrincipal AuthUser user, @PathVariable Long recipeId) {
        saved.unsave(user, recipeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cooked-history")
    public CommunityDtos.HistoryPage history(@AuthenticationPrincipal AuthUser user,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        CommunityDtos.HistoryPage result = history.list(user, page, size);
        PageResponse<CommunityDtos.HistoryView> p = result.page();
        return new CommunityDtos.HistoryPage(new PageResponse<>(p.items().reversed(), p.page(), p.size(), p.totalElements(), p.totalPages()),
                result.totalCookEvents(), result.totalCookEvents());
    }

    @PatchMapping("/cooked-history/{historyId}")
    public CommunityDtos.HistoryView updateHistory(@AuthenticationPrincipal AuthUser user, @PathVariable Long historyId,
                                                   @Valid @RequestBody CommunityDtos.CookRequest request) {
        if (request.notes() != null && request.notes().length() >= 1000) {
            throw ApiException.validation("notes", "Must be at most 1000 characters.");
        }
        return history.update(user, historyId, new CommunityDtos.CookRequest(null, request.notes()));
    }

    @DeleteMapping("/cooked-history/{historyId}")
    public ResponseEntity<Void> deleteHistory(@AuthenticationPrincipal AuthUser user, @PathVariable Long historyId) {
        history.delete(user, historyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/goals")
    public List<CommunityDtos.GoalView> goals(@AuthenticationPrincipal AuthUser user) {
        return goals.list(user).stream().filter(g -> !g.completed()).toList();
    }

    @PostMapping("/goals")
    @Operation(summary = "Create a monthly goal",
            description = "countMode UNIQUE_RECIPES counts distinct recipes cooked in the month; COOK_EVENTS counts every cook event.")
    public ResponseEntity<CommunityDtos.GoalView> createGoal(@AuthenticationPrincipal AuthUser user,
                                                             @RequestBody CommunityDtos.GoalRequest request) {
        if (request.targetCount() != null && request.targetCount() >= 30) {
            throw ApiException.validation("targetCount", "Must be between 1 and 30.");
        }
        CommunityDtos.GoalRequest goal = new CommunityDtos.GoalRequest(request.title(), request.targetCount(), request.month(),
                request.countMode(), request.tagId(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(goals.create(user, goal));
    }

    @PatchMapping("/goals/{goalId}")
    public CommunityDtos.GoalView updateGoal(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId,
                                             @RequestBody CommunityDtos.GoalRequest request) {
        return goals.update(user, goalId, new CommunityDtos.GoalRequest(request.title(), request.targetCount(), request.month(),
                null, request.tagId(), request.maxTotalTimeMins()));
    }

    @DeleteMapping("/goals/{goalId}")
    public ResponseEntity<Void> deleteGoal(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId) {
        goals.delete(user, goalId);
        return ResponseEntity.noContent().build();
    }
}

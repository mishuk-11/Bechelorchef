package com.bachelorchef.controller;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.MediaDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.RecipeDtos;
import com.bachelorchef.dto.UserDtos;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.service.MediaService;
import com.bachelorchef.service.RecipeQueryService;
import com.bachelorchef.service.ReviewService;
import com.bachelorchef.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints scoped to the signed-in user ("me"); no user id is ever taken from the URL. */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Profile")
public class ProfileController {

    private final UserService users;
    private final RecipeQueryService recipeQueries;
    private final ReviewService reviews;
    private final MediaService media;
    private final com.bachelorchef.security.RefreshCookies cookies;

    public ProfileController(UserService users, RecipeQueryService recipeQueries, ReviewService reviews, MediaService media,
                             com.bachelorchef.security.RefreshCookies cookies) {
        this.users = users;
        this.recipeQueries = recipeQueries;
        this.reviews = reviews;
        this.media = media;
        this.cookies = cookies;
    }

    @PatchMapping
    @Operation(summary = "Update allowed profile fields")
    public UserDtos.UserProfile update(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        if (request.fullName() != null && request.fullName().strip().length() >= 100) {
            throw ApiException.validation("fullName", "Must be 2 to 100 characters.");
        }
        String phone = request.phoneNumber() == null || request.phoneNumber().isBlank() ? null : request.phoneNumber();
        return users.updateProfile(user.id(), new UserDtos.UpdateProfileRequest(request.fullName(), phone,
                request.avatarUrl(), request.avatarAssetId(), null));
    }

    @PostMapping("/password")
    @Operation(summary = "Change password (signs out other sessions)")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        users.changePassword(user.id(), request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    @Operation(summary = "Delete (anonymise) my account")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody UserDtos.DeleteAccountRequest request) {
        users.deleteAccount(user.id(), request.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recipes")
    @Operation(summary = "My submitted recipes in every moderation state")
    public PageResponse<RecipeDtos.RecipeSummary> myRecipes(@AuthenticationPrincipal AuthUser user,
                                                            @RequestParam(required = false) RecipeStatus status,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        List<RecipeStatus> statuses = List.of();
        return recipeQueries.search(new RecipeQueryService.Filters(null, null, null, null, null, null, null, statuses, user.id()),
                "title", 0, size);
    }

    @GetMapping("/reviews")
    @Operation(summary = "My reviews with their moderation state")
    public PageResponse<CommunityDtos.MyReviewView> myReviews(@AuthenticationPrincipal AuthUser user,
                                                              @RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        PageResponse<CommunityDtos.MyReviewView> result = reviews.mine(user, page, size);
        List<CommunityDtos.MyReviewView> items = result.items().stream()
                .map(r -> new CommunityDtos.MyReviewView(r.id(), r.recipeId(), r.recipeTitle(), r.rating(), r.comment(),
                        r.photoUrl(), r.status(), null, r.updatedAt()))
                .toList();
        return PageResponse.of(items, result.page(), result.size(), items.size());
    }

    @PostMapping("/avatar-upload-signature")
    @Operation(summary = "Signed parameters for a direct avatar upload to the media provider")
    public MediaDtos.SignatureResponse avatarSignature(@AuthenticationPrincipal AuthUser user) {
        return media.sign(user, MediaDtos.Purpose.AVATAR);
    }
}

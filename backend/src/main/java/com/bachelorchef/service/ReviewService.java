package com.bachelorchef.service;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.Pagination;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.ReportStatus;
import com.bachelorchef.entity.ReportTargetType;
import com.bachelorchef.entity.Review;
import com.bachelorchef.entity.ReviewStatus;
import com.bachelorchef.entity.User;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.mapper.UserMapper;
import com.bachelorchef.repository.ContentReportRepository;
import com.bachelorchef.repository.RecipeRepository;
import com.bachelorchef.repository.ReviewRepository;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.security.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reviews and ratings. Moderation policy: every new or edited review is PENDING and is excluded
 * from public lists and rating aggregates until an admin approves it. Users may review any
 * published recipe except their own; cooking it first is not required.
 */
@Service
public class ReviewService {

    public static final int RATING_MIN = 1;
    public static final int RATING_MAX = 5;

    private final ReviewRepository reviews;
    private final RecipeRepository recipes;
    private final UserRepository users;
    private final ContentReportRepository reports;
    private final RatingAggregateService aggregates;
    private final MediaUrlPolicy urlPolicy;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, RecipeRepository recipes, UserRepository users,
                         ContentReportRepository reports, RatingAggregateService aggregates, MediaUrlPolicy urlPolicy,
                         RateLimiter rateLimiter, AuditService audit, Clock clock) {
        this.reviews = reviews;
        this.recipes = recipes;
        this.users = users;
        this.reports = reports;
        this.aggregates = aggregates;
        this.urlPolicy = urlPolicy;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
    }

    /** @return field errors for the rating/comment/photo of a review request */
    public Map<String, String> validate(CommunityDtos.ReviewRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        Integer rating = request.rating();
        if (rating == null) {
            errors.put("rating", "A rating is required.");
        } else if (rating < RATING_MIN || rating > RATING_MAX) {
            errors.put("rating", "Must be an integer between " + RATING_MIN + " and " + RATING_MAX + ".");
        }
        if (TextNormalizer.length(TextNormalizer.trimToNull(request.comment())) > 2000) {
            errors.put("comment", "Must be at most 2000 characters.");
        }
        urlPolicy.check(TextNormalizer.trimToNull(request.photoUrl())).ifPresent(m -> errors.put("photoUrl", m));
        return errors;
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityDtos.ReviewView> listApproved(Long recipeId, AuthUser viewer, Integer page, Integer size) {
        Recipe recipe = recipes.findById(recipeId).orElseThrow(() -> ApiException.notFound("Recipe"));
        if (!RecipeQueryService.canView(recipe, viewer)) {
            throw ApiException.notFound("Recipe");
        }
        Page<Review> result = reviews.findByRecipeIdAndStatus(recipeId, ReviewStatus.APPROVED,
                Pagination.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.of(result, ReviewService::view);
    }

    @Transactional
    public CommunityDtos.ReviewView create(AuthUser actor, Long recipeId, CommunityDtos.ReviewRequest request) {
        rateLimiter.check("review:" + actor.id(), 20, Duration.ofHours(1));
        Recipe recipe = recipes.findById(recipeId).orElseThrow(() -> ApiException.notFound("Recipe"));
        if (!RecipeQueryService.canView(recipe, actor)) {
            throw ApiException.notFound("Recipe");
        }
        if (!recipe.isPublic()) {
            throw new ApiException(HttpStatus.CONFLICT, "RECIPE_NOT_PUBLISHED", "Only published recipes can be reviewed.");
        }
        if (recipe.isOwnedBy(actor.id())) {
            throw ApiException.forbidden("You cannot review your own recipe.");
        }
        if (reviews.findByRecipeIdAndUserId(recipeId, actor.id()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_EXISTS", "You have already reviewed this recipe. Edit your review instead.");
        }
        Map<String, String> errors = validate(request);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        Review review = new Review(recipe, users.getReferenceById(actor.id()));
        applyContent(review, request);
        reviews.saveAndFlush(review);
        return view(review);
    }

    @Transactional
    public CommunityDtos.ReviewView update(AuthUser actor, Long reviewId, CommunityDtos.ReviewRequest request) {
        Review review = ownReview(actor, reviewId);
        if (review.getStatus() == ReviewStatus.REMOVED) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_REMOVED", "This review was removed by a moderator.");
        }
        Map<String, String> errors = validate(request);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        boolean wasApproved = review.getStatus() == ReviewStatus.APPROVED;
        applyContent(review, request);
        review.setStatus(ReviewStatus.PENDING);
        review.setModerationReason(null);
        review.setModeratedBy(null);
        review.setModeratedAt(null);
        if (wasApproved) {
            aggregates.recompute(review.getRecipe().getId());
        }
        return view(review);
    }

    /**
     * Users may delete their own review; the row is removed, aggregates are corrected, and any open
     * reports about it are closed so they do not linger in the moderation queue.
     */
    @Transactional
    public void delete(AuthUser actor, Long reviewId) {
        Review review = ownReview(actor, reviewId);
        Long recipeId = review.getRecipe().getId();
        reports.findByTargetTypeAndTargetIdAndStatus(ReportTargetType.REVIEW, reviewId, ReportStatus.OPEN).forEach(r -> {
            r.setStatus(ReportStatus.DISMISSED);
            r.setResolutionNote("Review deleted by its author.");
            r.setResolvedAt(clock.instant());
        });
        reviews.delete(review);
        reviews.flush();
        aggregates.recompute(recipeId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityDtos.MyReviewView> mine(AuthUser actor, Integer page, Integer size) {
        return PageResponse.of(reviews.findByUserId(actor.id(), Pagination.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")))),
                r -> new CommunityDtos.MyReviewView(r.getId(), r.getRecipe().getId(), r.getRecipe().getTitle(), r.getRating(),
                        r.getComment(), r.getPhotoUrl(), r.getStatus(), r.getModerationReason(), r.getUpdatedAt()));
    }

    // ------------------------------------------------------------------ admin moderation

    /** status: PENDING, APPROVED, REJECTED, REMOVED or FLAGGED (has open reports). */
    @Transactional(readOnly = true)
    public PageResponse<CommunityDtos.AdminReviewView> moderationQueue(String status, Integer page, Integer size) {
        String s = status == null || status.isBlank() ? "PENDING" : status.toUpperCase(java.util.Locale.ROOT);
        var pageable = Pagination.of(page, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        Page<Review> result;
        if ("FLAGGED".equals(s)) {
            result = reviews.findFlagged(pageable);
        } else {
            ReviewStatus reviewStatus;
            try {
                reviewStatus = ReviewStatus.valueOf(s);
            } catch (IllegalArgumentException e) {
                throw ApiException.validation("status", "Must be PENDING, APPROVED, REJECTED, REMOVED or FLAGGED.");
            }
            result = reviews.findByStatusIn(Set.of(reviewStatus), pageable);
        }
        return PageResponse.of(result, ReviewService::adminView);
    }

    @Transactional
    public void moderate(AuthUser admin, Long reviewId, ReviewStatus target, String reason) {
        Review review = reviews.findById(reviewId).orElseThrow(() -> ApiException.notFound("Review"));
        if (review.getUser().getId().equals(admin.id())) {
            throw ApiException.forbidden("You cannot moderate your own review.");
        }
        String why = TextNormalizer.trimToNull(reason);
        if (target != ReviewStatus.APPROVED && why == null) {
            throw ApiException.validation("reason", "A reason is required.");
        }
        if (target == ReviewStatus.APPROVED && !review.getRecipe().isPublic()) {
            throw new ApiException(HttpStatus.CONFLICT, "RECIPE_NOT_PUBLISHED", "Reviews of unpublished recipes cannot be approved.");
        }
        review.setStatus(target);
        review.setModerationReason(why);
        review.setModeratedBy(users.getReferenceById(admin.id()));
        review.setModeratedAt(clock.instant());
        aggregates.recompute(review.getRecipe().getId());
        if (target == ReviewStatus.REMOVED) {
            reports.findByTargetTypeAndTargetIdAndStatus(ReportTargetType.REVIEW, reviewId, ReportStatus.OPEN)
                    .forEach(r -> {
                        r.setStatus(ReportStatus.ACTIONED);
                        r.setResolvedBy(users.getReferenceById(admin.id()));
                        r.setResolutionNote(why);
                        r.setResolvedAt(clock.instant());
                    });
        }
        audit.record(admin.id(), "REVIEW_" + target.name(), "REVIEW", reviewId, why);
    }

    private Review ownReview(AuthUser actor, Long reviewId) {
        Review review = reviews.findById(reviewId).orElseThrow(() -> ApiException.notFound("Review"));
        if (!review.getUser().getId().equals(actor.id())) {
            throw ApiException.notFound("Review"); // do not reveal other users' reviews
        }
        return review;
    }

    private void applyContent(Review review, CommunityDtos.ReviewRequest request) {
        review.setRating(request.rating());
        review.setComment(TextNormalizer.trimToNull(request.comment()));
        String photo = TextNormalizer.trimToNull(request.photoUrl());
        review.setPhotoUrl(photo);
        review.setPhotoAssetId(photo == null ? null : TextNormalizer.trimToNull(request.photoAssetId()));
    }

    static CommunityDtos.ReviewView view(Review r) {
        return new CommunityDtos.ReviewView(r.getId(), r.getRecipe().getId(), r.getRating(), r.getComment(), r.getPhotoUrl(),
                r.getStatus(), UserMapper.author(r.getUser()), r.getCreatedAt(), r.getUpdatedAt());
    }

    static CommunityDtos.AdminReviewView adminView(Review r) {
        User u = r.getUser();
        return new CommunityDtos.AdminReviewView(r.getId(), r.getRecipe().getId(), r.getRecipe().getTitle(), r.getRating(),
                r.getComment(), r.getPhotoUrl(), r.getStatus(), r.getModerationReason(), UserMapper.author(u), u.getEmail(),
                r.getCreatedAt());
    }

    public static List<ReviewStatus> publicStatuses() {
        return List.of(ReviewStatus.APPROVED);
    }
}

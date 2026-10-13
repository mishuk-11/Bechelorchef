package com.bachelorchef.service;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.Pagination;
import com.bachelorchef.entity.ContentReport;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.entity.ReportStatus;
import com.bachelorchef.entity.ReportTargetType;
import com.bachelorchef.entity.Review;
import com.bachelorchef.entity.ReviewStatus;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User flags on recipes/reviews and the admin resolution workflow. */
@Service
public class ReportService {

    private final ContentReportRepository reports;
    private final RecipeRepository recipes;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final RatingAggregateService aggregates;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final Clock clock;

    public ReportService(ContentReportRepository reports, RecipeRepository recipes, ReviewRepository reviews,
                         UserRepository users, RatingAggregateService aggregates, RateLimiter rateLimiter,
                         AuditService audit, Clock clock) {
        this.reports = reports;
        this.recipes = recipes;
        this.reviews = reviews;
        this.users = users;
        this.aggregates = aggregates;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void create(AuthUser actor, CommunityDtos.ReportRequest request) {
        rateLimiter.check("report:" + actor.id(), 20, Duration.ofHours(1));
        Long authorId = switch (request.targetType()) {
            case RECIPE -> {
                Recipe recipe = recipes.findById(request.targetId()).filter(Recipe::isPublic)
                        .orElseThrow(() -> ApiException.notFound("Recipe"));
                yield recipe.getCreatedBy() == null ? null : recipe.getCreatedBy().getId();
            }
            case REVIEW -> {
                Review review = reviews.findById(request.targetId())
                        .filter(r -> r.getStatus() == ReviewStatus.APPROVED && r.getRecipe().isPublic())
                        .orElseThrow(() -> ApiException.notFound("Review"));
                yield review.getUser().getId();
            }
        };
        if (actor.id().equals(authorId)) {
            throw ApiException.forbidden("You cannot report your own content.");
        }
        if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(actor.id(), request.targetType(),
                request.targetId(), ReportStatus.OPEN)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED", "You have already reported this. A moderator will review it.");
        }
        reports.save(new ContentReport(users.getReferenceById(actor.id()), request.targetType(), request.targetId(),
                request.reason().strip()));
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityDtos.ReportView> list(String status, Integer page, Integer size) {
        ReportStatus s;
        try {
            s = status == null || status.isBlank() ? ReportStatus.OPEN : ReportStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("status", "Must be OPEN, DISMISSED or ACTIONED.");
        }
        Page<ContentReport> result = reports.findByStatus(s,
                Pagination.of(page, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));
        List<Long> recipeIds = idsOf(result, ReportTargetType.RECIPE);
        List<Long> reviewIds = idsOf(result, ReportTargetType.REVIEW);
        Map<Long, Recipe> recipeMap = recipes.findAllById(recipeIds).stream().collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Map<Long, Review> reviewMap = reviews.findAllById(reviewIds).stream().collect(Collectors.toMap(Review::getId, Function.identity()));
        return PageResponse.of(result, r -> {
            String summary;
            Long recipeId;
            if (r.getTargetType() == ReportTargetType.RECIPE) {
                Recipe recipe = recipeMap.get(r.getTargetId());
                summary = recipe == null ? "(recipe no longer exists)" : recipe.getTitle() + " [" + recipe.getStatus() + "]";
                recipeId = r.getTargetId();
            } else {
                Review review = reviewMap.get(r.getTargetId());
                summary = review == null ? "(review deleted by its author)"
                        : review.getRating() + "★ " + excerpt(review.getComment()) + " [" + review.getStatus() + "]";
                recipeId = review == null ? null : review.getRecipe().getId();
            }
            return new CommunityDtos.ReportView(r.getId(), r.getTargetType(), r.getTargetId(), summary, recipeId,
                    r.getReason(), r.getStatus(), UserMapper.author(r.getReporter()), r.getCreatedAt());
        });
    }

    @Transactional
    public void resolve(AuthUser admin, Long reportId, CommunityDtos.ResolveReportRequest request) {
        ContentReport report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Report"));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "This report has already been resolved.");
        }
        String note = request.note().strip();
        ReportStatus outcome = request.action() == CommunityDtos.ReportAction.DISMISS ? ReportStatus.DISMISSED : ReportStatus.ACTIONED;
        if (outcome == ReportStatus.ACTIONED) {
            removeTarget(admin, report, note);
        }
        // Resolve every open report on the same target with the same decision.
        for (ContentReport r : reports.findByTargetTypeAndTargetIdAndStatus(report.getTargetType(), report.getTargetId(), ReportStatus.OPEN)) {
            r.setStatus(outcome);
            r.setResolvedBy(users.getReferenceById(admin.id()));
            r.setResolutionNote(note);
            r.setResolvedAt(clock.instant());
        }
        audit.record(admin.id(), "REPORT_" + outcome.name(), report.getTargetType().name(), report.getTargetId(), note);
    }

    private void removeTarget(AuthUser admin, ContentReport report, String note) {
        if (report.getTargetType() == ReportTargetType.RECIPE) {
            Recipe recipe = recipes.findById(report.getTargetId()).orElseThrow(() -> ApiException.notFound("Recipe"));
            if (!recipe.isOfficial() && recipe.isOwnedBy(admin.id())) {
                throw ApiException.forbidden("You cannot moderate your own content.");
            }
            recipe.setStatus(RecipeStatus.ARCHIVED);
            recipe.setModerationNote(note);
        } else {
            Review review = reviews.findById(report.getTargetId()).orElseThrow(() -> ApiException.notFound("Review"));
            if (review.getUser().getId().equals(admin.id())) {
                throw ApiException.forbidden("You cannot moderate your own content.");
            }
            review.setStatus(ReviewStatus.REMOVED);
            review.setModerationReason(note);
            review.setModeratedBy(users.getReferenceById(admin.id()));
            review.setModeratedAt(clock.instant());
            aggregates.recompute(review.getRecipe().getId());
        }
    }

    private static List<Long> idsOf(Page<ContentReport> page, ReportTargetType type) {
        return page.getContent().stream().filter(r -> r.getTargetType() == type).map(ContentReport::getTargetId).distinct().toList();
    }

    private static String excerpt(String text) {
        if (text == null) {
            return "(no comment)";
        }
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }
}

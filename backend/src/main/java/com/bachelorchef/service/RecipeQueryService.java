package com.bachelorchef.service;

import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.Pagination;
import com.bachelorchef.dto.RecipeDtos;
import com.bachelorchef.entity.Difficulty;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.RecipeIngredient;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.mapper.CatalogMapper;
import com.bachelorchef.mapper.RecipeMapper;
import com.bachelorchef.repository.RecipeIngredientRepository;
import com.bachelorchef.repository.RecipeRepository;
import com.bachelorchef.repository.RecipeStepRepository;
import com.bachelorchef.repository.ReviewRepository;
import com.bachelorchef.repository.SavedRecipeRepository;
import com.bachelorchef.entity.SavedRecipe;
import com.bachelorchef.entity.Review;
import com.bachelorchef.security.AuthUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeQueryService {

    /** Public sort keys → safe, stable Sort definitions (id is always the final tie-breaker). */
    static final Map<String, Sort> SORTS = Map.of(
            "newest", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
            "rating", Sort.by(Sort.Order.desc("ratingAvg"), Sort.Order.desc("ratingCount"), Sort.Order.asc("id")),
            "popular", Sort.by(Sort.Order.desc("ratingCount"), Sort.Order.desc("ratingAvg"), Sort.Order.asc("id")),
            "time", Sort.by(Sort.Order.asc("totalTimeMins"), Sort.Order.asc("id")),
            "cost", Sort.by(Sort.Order.asc("estimatedCostBdt"), Sort.Order.asc("id")),
            "title", Sort.by(Sort.Order.asc("normalizedTitle"), Sort.Order.asc("id")));

    private final RecipeRepository recipes;
    private final RecipeIngredientRepository recipeIngredients;
    private final RecipeStepRepository steps;
    private final SavedRecipeRepository saved;
    private final ReviewRepository reviews;

    public RecipeQueryService(RecipeRepository recipes, RecipeIngredientRepository recipeIngredients,
                              RecipeStepRepository steps, SavedRecipeRepository saved, ReviewRepository reviews) {
        this.recipes = recipes;
        this.recipeIngredients = recipeIngredients;
        this.steps = steps;
        this.saved = saved;
        this.reviews = reviews;
    }

    /** Catalogue filters. All optional; {@code statuses} is set by the caller, never by public input. */
    public record Filters(String query, Long ingredientId, Long tagId, String difficulty, String source,
                          BigDecimal maxCostBdt, Integer maxTotalTimeMins, List<RecipeStatus> statuses, Long authorId) {
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeDtos.RecipeSummary> search(Filters filters, String sort, Integer page, Integer size) {
        validateFilters(filters);
        return PageResponse.of(recipes.findAll(spec(filters), Pagination.of(page, size, Pagination.sort(sort, SORTS, "newest"))),
                RecipeMapper::summary);
    }

    @Transactional(readOnly = true)
    public RecipeDtos.RecipeDetail detail(Long recipeId, AuthUser viewer) {
        Recipe recipe = requireViewable(recipeId, viewer);
        List<RecipeIngredient> lines = recipeIngredients.findForRecipe(recipeId);
        boolean owner = viewer != null && recipe.isOwnedBy(viewer.id());
        boolean privileged = owner || (viewer != null && viewer.isAdmin());
        boolean canEdit = viewer != null && (viewer.isAdmin() || (owner && recipe.getStatus() != RecipeStatus.ARCHIVED));
        boolean isSaved = viewer != null && saved.existsById(new SavedRecipe.Key(viewer.id(), recipeId));
        Long myReviewId = viewer == null ? null
                : reviews.findByRecipeIdAndUserId(recipeId, viewer.id()).map(Review::getId).orElse(null);
        return new RecipeDtos.RecipeDetail(
                RecipeMapper.summary(recipe),
                recipe.getDescription(),
                recipe.getImageAssetId(),
                PortionCalculator.costPerServing(recipe.getEstimatedCostBdt(), recipe.getBaseServings(), recipe.getBaseServings()),
                lines.stream().map(l -> new RecipeDtos.IngredientLineView(l.getIngredient().getId(), l.getIngredient().getName(),
                        l.getQuantity().stripTrailingZeros().scale() < 0 ? l.getQuantity().setScale(0) : l.getQuantity().stripTrailingZeros(),
                        l.getUnit(), l.isOptional(), l.getIngredient().isActive())).toList(),
                steps.findForRecipe(recipeId).stream()
                        .map(s -> new RecipeDtos.StepView(s.getStepNumber(), s.getInstructionText(), s.getImageUrl())).toList(),
                recipe.getTags().stream().map(CatalogMapper::tag)
                        .sorted(Comparator.comparing(t -> t.name().toLowerCase(Locale.ROOT))).toList(),
                privileged ? recipe.getModerationNote() : null,
                recipe.getApprovedAt(),
                recipe.getUpdatedAt(),
                new RecipeDtos.Viewer(canEdit, owner, isSaved, myReviewId));
    }

    @Transactional(readOnly = true)
    public RecipeDtos.PortionResponse portions(Long recipeId, Integer servings, AuthUser viewer) {
        if (!PortionCalculator.isValidServings(servings)) {
            throw ApiException.validation("servings", "Servings must be a whole number from "
                    + PortionCalculator.MIN_SERVINGS + " to " + PortionCalculator.MAX_SERVINGS + ".");
        }
        Recipe recipe = requireViewable(recipeId, viewer);
        int base = recipe.getBaseServings();
        List<RecipeDtos.ScaledIngredient> scaled = recipeIngredients.findForRecipe(recipeId).stream()
                .map(l -> new RecipeDtos.ScaledIngredient(l.getIngredient().getId(), l.getIngredient().getName(),
                        PortionCalculator.scaleQuantity(l.getQuantity(), base, servings), l.getUnit(), l.isOptional()))
                .toList();
        return new RecipeDtos.PortionResponse(recipeId, base, servings,
                PortionCalculator.scaledCost(recipe.getEstimatedCostBdt(), base, servings),
                PortionCalculator.costPerServing(recipe.getEstimatedCostBdt(), base, servings),
                scaled,
                "Costs are linear estimates in BDT based on the recipe's base servings, not live market prices.");
    }

    /**
     * Public users see only APPROVED recipes. Authors may see their own recipes in any state and
     * admins see everything. Other cases return 404 so unpublished content is not revealed.
     */
    @Transactional(readOnly = true)
    public Recipe requireViewable(Long recipeId, AuthUser viewer) {
        Recipe recipe = recipes.findDetailedById(recipeId).orElseThrow(() -> ApiException.notFound("Recipe"));
        if (!canView(recipe, viewer)) {
            throw ApiException.notFound("Recipe");
        }
        return recipe;
    }

    public static boolean canView(Recipe recipe, AuthUser viewer) {
        return recipe.isPublic() || (viewer != null && (viewer.isAdmin() || recipe.isOwnedBy(viewer.id())));
    }

    private static void validateFilters(Filters f) {
        if (f.difficulty() != null && !f.difficulty().isBlank() && Difficulty.parse(f.difficulty()).isEmpty()) {
            throw ApiException.validation("difficulty", "Must be one of EASY, MEDIUM or HARD.");
        }
        if (f.source() != null && !f.source().isBlank() && !List.of("official", "community").contains(f.source().toLowerCase(Locale.ROOT))) {
            throw ApiException.validation("source", "Must be official or community.");
        }
        if (f.maxTotalTimeMins() != null && (f.maxTotalTimeMins() < 1 || f.maxTotalTimeMins() > 60)) {
            throw ApiException.validation("maxTotalTimeMins", "Must be between 1 and 60.");
        }
        if (f.maxCostBdt() != null && f.maxCostBdt().signum() < 0) {
            throw ApiException.validation("maxCostBdt", "Must not be negative.");
        }
        if (f.query() != null && f.query().length() > 100) {
            throw ApiException.validation("q", "Search text must be at most 100 characters.");
        }
    }

    static Specification<Recipe> spec(Filters f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                p.add(root.get("status").in(f.statuses()));
            }
            if (f.authorId() != null) {
                p.add(cb.equal(root.get("createdBy").get("id"), f.authorId()));
            }
            String q = TextNormalizer.key(f.query());
            if (q != null) {
                String like = "%" + CatalogService.escapeLike(q) + "%";
                p.add(cb.or(cb.like(root.get("normalizedTitle"), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like, '\\')));
            }
            if (f.ingredientId() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<RecipeIngredient> ri = sub.from(RecipeIngredient.class);
                sub.select(ri.get("recipe").get("id"))
                        .where(cb.equal(ri.get("ingredient").get("id"), f.ingredientId()));
                p.add(root.get("id").in(sub));
            }
            if (f.tagId() != null) {
                p.add(cb.equal(root.join("tags").get("id"), f.tagId()));
            }
            Difficulty.parse(f.difficulty()).ifPresent(d -> p.add(cb.equal(root.get("difficulty"), d)));
            if (f.source() != null && !f.source().isBlank()) {
                p.add(cb.equal(root.get("official"), "official".equalsIgnoreCase(f.source())));
            }
            if (f.maxCostBdt() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("estimatedCostBdt"), f.maxCostBdt()));
            }
            if (f.maxTotalTimeMins() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("totalTimeMins"), f.maxTotalTimeMins()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}

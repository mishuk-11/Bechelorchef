package com.bachelorchef.service;

import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.RecipeDtos;
import com.bachelorchef.entity.Difficulty;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.RecipeIngredient;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.mapper.RecipeMapper;
import com.bachelorchef.repository.PantryMatchRepository;
import com.bachelorchef.repository.RecipeIngredientRepository;
import com.bachelorchef.repository.RecipeRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pantry matcher. Exactly three queries per request regardless of result size: candidate counts
 * (grouped), recipe rows for the page, and ingredient lines for the page.
 */
@Service
public class PantryMatchService {

    public static final int DEFAULT_MAX_TIME = 20;
    public static final int MAX_SELECTED = 50;
    public static final int MAX_PAGE_SIZE = 50;
    public static final String DISCLAIMER = "Pantry match is a guide, not a guarantee: brands, equipment and "
            + "substitutions can change how a recipe turns out.";

    private final PantryMatchRepository matchRepository;
    private final RecipeRepository recipes;
    private final RecipeIngredientRepository recipeIngredients;

    public PantryMatchService(PantryMatchRepository matchRepository, RecipeRepository recipes,
                              RecipeIngredientRepository recipeIngredients) {
        this.matchRepository = matchRepository;
        this.recipes = recipes;
        this.recipeIngredients = recipeIngredients;
    }

    /** Validated, normalised matcher input. */
    record Criteria(Set<Long> ingredientIds, int maxTime, boolean strict, Difficulty difficulty,
                    List<Long> tagIds, java.math.BigDecimal maxCost, int page, int size) {
    }

    static Criteria normalise(RecipeDtos.MatchRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<Long> ids = request.ingredientIds() == null ? Set.of()
                : request.ingredientIds().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            errors.put("ingredientIds", "Select at least one ingredient.");
        } else if (ids.size() > MAX_SELECTED) {
            errors.put("ingredientIds", "Select at most " + MAX_SELECTED + " ingredients.");
        }
        int maxTime = request.maxTotalTimeMins() == null ? DEFAULT_MAX_TIME : request.maxTotalTimeMins();
        if (maxTime < 1 || maxTime > TimeCategory.TOTAL_MAX) {
            errors.put("maxTotalTimeMins", "Must be between 1 and " + TimeCategory.TOTAL_MAX + " minutes.");
        }
        Difficulty difficulty = null;
        if (request.difficulty() != null && !request.difficulty().isBlank()) {
            difficulty = Difficulty.parse(request.difficulty()).orElse(null);
            if (difficulty == null) {
                errors.put("difficulty", "Must be one of EASY, MEDIUM or HARD.");
            }
        }
        if (request.maxCostBdt() != null && request.maxCostBdt().signum() < 0) {
            errors.put("maxCostBdt", "Must not be negative.");
        }
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 12 : request.size();
        if (page < 0) {
            errors.put("page", "Must be 0 or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            errors.put("size", "Must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        List<Long> tagIds = request.tagIds() == null ? List.of() : request.tagIds().stream().filter(Objects::nonNull).distinct().toList();
        return new Criteria(ids, maxTime, Boolean.TRUE.equals(request.strict()), difficulty, tagIds,
                request.maxCostBdt(), page, size);
    }

    @Transactional(readOnly = true)
    public RecipeDtos.MatchResponse match(RecipeDtos.MatchRequest request) {
        Criteria c = normalise(request);
        List<PantryMatcher.Candidate> ranked = PantryMatcher.rank(
                matchRepository.findCandidates(c.ingredientIds(), c.maxTime(), c.difficulty(), c.tagIds(), c.maxCost()),
                c.strict());

        int from = Math.min(c.page() * c.size(), ranked.size());
        int to = Math.min(from + c.size(), ranked.size());
        List<PantryMatcher.Candidate> pageItems = ranked.subList(from, to);
        List<Long> pageIds = pageItems.stream().map(PantryMatcher.Candidate::recipeId).toList();

        Map<Long, Recipe> recipeById = pageIds.isEmpty() ? Map.of()
                : recipes.findByIdIn(pageIds).stream().collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Map<Long, List<RecipeIngredient>> linesByRecipe = pageIds.isEmpty() ? Map.of()
                : recipeIngredients.findForRecipes(pageIds).stream().collect(Collectors.groupingBy(l -> l.getRecipe().getId()));

        List<RecipeDtos.MatchItem> items = new ArrayList<>();
        for (PantryMatcher.Candidate candidate : pageItems) {
            Recipe recipe = recipeById.get(candidate.recipeId());
            if (recipe == null) {
                continue; // removed concurrently
            }
            items.add(toItem(recipe, candidate, linesByRecipe.getOrDefault(candidate.recipeId(), List.of()), c.ingredientIds()));
        }
        return new RecipeDtos.MatchResponse(PageResponse.of(items, c.page(), c.size(), ranked.size()),
                c.maxTime(), c.strict(), c.ingredientIds().size(), DISCLAIMER);
    }

    private static RecipeDtos.MatchItem toItem(Recipe recipe, PantryMatcher.Candidate candidate,
                                               List<RecipeIngredient> lines, Set<Long> selected) {
        List<RecipeDtos.IngredientRef> matched = new ArrayList<>();
        List<RecipeDtos.IngredientRef> missing = new ArrayList<>();
        List<RecipeDtos.OptionalIngredientRef> optional = new ArrayList<>();
        for (RecipeIngredient line : lines) {
            Long id = line.getIngredient().getId();
            String name = line.getIngredient().getName();
            if (line.isOptional()) {
                optional.add(new RecipeDtos.OptionalIngredientRef(id, name, selected.contains(id)));
            } else if (selected.contains(id)) {
                matched.add(new RecipeDtos.IngredientRef(id, name));
            } else {
                missing.add(new RecipeDtos.IngredientRef(id, name));
            }
        }
        return new RecipeDtos.MatchItem(RecipeMapper.summary(recipe),
                PantryMatcher.matchPercent(candidate.matchedRequiredCount(), candidate.requiredCount()),
                candidate.requiredCount(), candidate.matchedRequiredCount(), matched, missing, optional);
    }
}

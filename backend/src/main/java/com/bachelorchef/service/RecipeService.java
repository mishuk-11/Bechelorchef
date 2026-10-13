package com.bachelorchef.service;

import com.bachelorchef.dto.RecipeDtos;
import com.bachelorchef.entity.Difficulty;
import com.bachelorchef.entity.Ingredient;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.RecipeIngredient;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.entity.RecipeStep;
import com.bachelorchef.entity.Tag;
import com.bachelorchef.entity.User;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.repository.RecipeIngredientRepository;
import com.bachelorchef.repository.RecipeRepository;
import com.bachelorchef.repository.RecipeStepRepository;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.AuthUser;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recipe writes and moderation transitions. Every multi-table change (recipe row, ingredient
 * lines, steps, tags) happens inside one transaction.
 *
 * <p>Status rules:
 * <ul>
 *   <li>Community create: {@code submit} (default true) → PENDING, otherwise DRAFT.</li>
 *   <li>Owner edit of DRAFT/PENDING/REJECTED: {@code submit} → PENDING, otherwise DRAFT.</li>
 *   <li>Owner edit of APPROVED: returns to PENDING only if the content actually changed.</li>
 *   <li>ARCHIVED recipes cannot be edited by their owner.</li>
 *   <li>Admin official create: {@code submit} (default true) → APPROVED, otherwise DRAFT.</li>
 *   <li>Admin edits never trigger re-moderation.</li>
 * </ul>
 */
@Service
public class RecipeService {

    private final RecipeRepository recipes;
    private final RecipeIngredientRepository recipeIngredients;
    private final RecipeStepRepository steps;
    private final UserRepository users;
    private final CatalogService catalog;
    private final RecipeValidator validator;
    private final AuditService audit;
    private final EntityManager em;
    private final Clock clock;

    public RecipeService(RecipeRepository recipes, RecipeIngredientRepository recipeIngredients, RecipeStepRepository steps,
                         UserRepository users, CatalogService catalog, RecipeValidator validator, AuditService audit,
                         EntityManager em, Clock clock) {
        this.recipes = recipes;
        this.recipeIngredients = recipeIngredients;
        this.steps = steps;
        this.users = users;
        this.catalog = catalog;
        this.validator = validator;
        this.audit = audit;
        this.em = em;
        this.clock = clock;
    }

    @Transactional
    public Long createCommunity(AuthUser actor, RecipeDtos.RecipeRequest request) {
        User author = users.getReferenceById(actor.id());
        Recipe recipe = new Recipe();
        recipe.setOfficial(false);
        recipe.setCreatedBy(author);
        recipe.setStatus(submit(request) ? RecipeStatus.PENDING : RecipeStatus.DRAFT);
        applyContent(recipe, request, false, Set.of());
        return recipe.getId();
    }

    @Transactional
    public Long createOfficial(AuthUser admin, RecipeDtos.RecipeRequest request) {
        User adminUser = users.getReferenceById(admin.id());
        Recipe recipe = new Recipe();
        recipe.setOfficial(true);
        recipe.setCreatedBy(adminUser);
        if (submit(request)) {
            markApproved(recipe, adminUser);
        } else {
            recipe.setStatus(RecipeStatus.DRAFT);
        }
        applyContent(recipe, request, true, Set.of());
        audit.record(admin.id(), "OFFICIAL_RECIPE_CREATED", "RECIPE", recipe.getId(), "status=" + recipe.getStatus());
        return recipe.getId();
    }

    @Transactional
    public void update(AuthUser actor, Long recipeId, RecipeDtos.RecipeRequest request) {
        Recipe recipe = recipes.findDetailedById(recipeId).orElseThrow(() -> ApiException.notFound("Recipe"));
        boolean owner = recipe.isOwnedBy(actor.id());
        if (!owner && !actor.isAdmin()) {
            // 404 rather than 403 so the existence of other users' unpublished recipes is not revealed.
            if (!recipe.isPublic()) {
                throw ApiException.notFound("Recipe");
            }
            throw ApiException.forbidden("You can only edit your own recipes.");
        }
        List<RecipeIngredient> currentLines = recipeIngredients.findForRecipe(recipeId);
        String before = fingerprint(recipe, currentLines, steps.findForRecipe(recipeId));
        Set<Long> alreadyLinked = currentLines.stream().map(l -> l.getIngredient().getId()).collect(Collectors.toSet());

        if (actor.isAdmin()) {
            applyContent(recipe, request, true, alreadyLinked);
            if (recipe.isOfficial() && recipe.getStatus() == RecipeStatus.DRAFT && Boolean.TRUE.equals(request.submit())) {
                markApproved(recipe, users.getReferenceById(actor.id()));
            }
            audit.record(actor.id(), "RECIPE_EDITED_BY_ADMIN", "RECIPE", recipeId, "status=" + recipe.getStatus());
            return;
        }

        if (recipe.getStatus() == RecipeStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "RECIPE_ARCHIVED", "Archived recipes cannot be edited.");
        }
        RecipeStatus previous = recipe.getStatus();
        applyContent(recipe, request, false, alreadyLinked);
        if (previous == RecipeStatus.APPROVED) {
            String after = fingerprint(recipe, recipeIngredients.findForRecipe(recipeId), steps.findForRecipe(recipeId));
            if (!before.equals(after)) {
                recipe.setStatus(RecipeStatus.PENDING);
                recipe.setApprovedBy(null);
                recipe.setApprovedAt(null);
                recipe.setModerationNote(null);
            }
        } else {
            recipe.setStatus(submit(request) ? RecipeStatus.PENDING : RecipeStatus.DRAFT);
        }
    }

    /** DELETE /recipes/{id}: owners withdraw, admins archive. Nothing is hard-deleted. */
    @Transactional
    public void withdraw(AuthUser actor, Long recipeId) {
        Recipe recipe = recipes.findById(recipeId).orElseThrow(() -> ApiException.notFound("Recipe"));
        if (!recipe.isOwnedBy(actor.id()) && !actor.isAdmin()) {
            throw recipe.isPublic() ? ApiException.forbidden("You can only remove your own recipes.") : ApiException.notFound("Recipe");
        }
        if (recipe.getStatus() == RecipeStatus.ARCHIVED) {
            return;
        }
        recipe.setStatus(RecipeStatus.ARCHIVED);
        if (actor.isAdmin()) {
            audit.record(actor.id(), "RECIPE_ARCHIVED", "RECIPE", recipeId, null);
        }
    }

    // ------------------------------------------------------------------ admin moderation

    @Transactional
    public void approve(AuthUser admin, Long recipeId, String note) {
        Recipe recipe = loadForModeration(admin, recipeId);
        requireStatus(recipe, Set.of(RecipeStatus.PENDING, RecipeStatus.DRAFT, RecipeStatus.REJECTED), "approved");
        markApproved(recipe, users.getReferenceById(admin.id()));
        recipe.setModerationNote(TextNormalizer.trimToNull(note));
        audit.record(admin.id(), "RECIPE_APPROVED", "RECIPE", recipeId, note);
    }

    @Transactional
    public void reject(AuthUser admin, Long recipeId, String reason) {
        String why = TextNormalizer.trimToNull(reason);
        if (why == null) {
            throw ApiException.validation("reason", "A reason is required when rejecting.");
        }
        Recipe recipe = loadForModeration(admin, recipeId);
        requireStatus(recipe, Set.of(RecipeStatus.PENDING), "rejected");
        recipe.setStatus(RecipeStatus.REJECTED);
        recipe.setModerationNote(why);
        audit.record(admin.id(), "RECIPE_REJECTED", "RECIPE", recipeId, why);
    }

    @Transactional
    public void archive(AuthUser admin, Long recipeId, String reason) {
        Recipe recipe = recipes.findById(recipeId).orElseThrow(() -> ApiException.notFound("Recipe"));
        if (recipe.getStatus() == RecipeStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "This recipe is already archived.");
        }
        recipe.setStatus(RecipeStatus.ARCHIVED);
        if (TextNormalizer.trimToNull(reason) != null) {
            recipe.setModerationNote(reason.strip());
        }
        audit.record(admin.id(), "RECIPE_ARCHIVED", "RECIPE", recipeId, reason);
    }

    /** Restores to APPROVED if the recipe had been approved before archiving, otherwise to PENDING. */
    @Transactional
    public void restore(AuthUser admin, Long recipeId) {
        Recipe recipe = recipes.findById(recipeId).orElseThrow(() -> ApiException.notFound("Recipe"));
        requireStatus(recipe, Set.of(RecipeStatus.ARCHIVED), "restored");
        recipe.setStatus(recipe.getApprovedAt() != null ? RecipeStatus.APPROVED : RecipeStatus.PENDING);
        audit.record(admin.id(), "RECIPE_RESTORED", "RECIPE", recipeId, "status=" + recipe.getStatus());
    }

    // ------------------------------------------------------------------ internals

    private Recipe loadForModeration(AuthUser admin, Long recipeId) {
        Recipe recipe = recipes.findById(recipeId).orElseThrow(() -> ApiException.notFound("Recipe"));
        if (!recipe.isOfficial() && recipe.isOwnedBy(admin.id())) {
            throw ApiException.forbidden("You cannot moderate your own community submission.");
        }
        return recipe;
    }

    private static void requireStatus(Recipe recipe, Set<RecipeStatus> allowed, String verb) {
        if (!allowed.contains(recipe.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "A " + recipe.getStatus().name().toLowerCase() + " recipe cannot be " + verb + ".");
        }
    }

    private void markApproved(Recipe recipe, User approver) {
        recipe.setStatus(RecipeStatus.APPROVED);
        recipe.setApprovedBy(approver);
        recipe.setApprovedAt(clock.instant());
    }

    private static boolean submit(RecipeDtos.RecipeRequest request) {
        return request.submit() == null || request.submit();
    }

    /**
     * Validates and writes all editable content. Inactive ingredients may stay on a recipe that
     * already uses them but cannot be newly added.
     */
    void applyContent(Recipe recipe, RecipeDtos.RecipeRequest request, boolean allowNewTags, Set<Long> alreadyLinked) {
        RecipeDraft draft = toDraft(request);
        Map<String, String> errors = new LinkedHashMap<>(validator.validate(draft));

        List<RecipeDtos.IngredientInput> inputs = request.ingredients() == null ? List.of() : request.ingredients();
        Map<Long, Ingredient> found = catalog.ingredientsById(
                inputs.stream().filter(Objects::nonNull).map(RecipeDtos.IngredientInput::ingredientId).filter(Objects::nonNull).toList());
        for (int i = 0; i < inputs.size(); i++) {
            RecipeDtos.IngredientInput input = inputs.get(i);
            if (input == null || input.ingredientId() == null) {
                continue;
            }
            Ingredient ingredient = found.get(input.ingredientId());
            if (ingredient == null) {
                errors.putIfAbsent("ingredients[" + i + "].ingredient", "Unknown ingredient.");
            } else if (!ingredient.isActive() && !alreadyLinked.contains(ingredient.getId())) {
                errors.putIfAbsent("ingredients[" + i + "].ingredient", ingredient.getName() + " is no longer available.");
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        Set<Tag> resolvedTags = catalog.resolveTags(request.tags(), allowNewTags);

        String title = TextNormalizer.clean(request.title());
        recipe.setTitle(title);
        recipe.setNormalizedTitle(TextNormalizer.key(title));
        recipe.setDescription(TextNormalizer.trimToNull(request.description()));
        recipe.setTimes(request.prepTimeMins(), request.cookTimeMins());
        recipe.setDifficulty(Difficulty.parse(request.difficulty()).orElseThrow());
        recipe.setEstimatedCostBdt(request.estimatedCostBdt().setScale(2));
        recipe.setBaseServings(request.baseServings());
        String imageUrl = TextNormalizer.trimToNull(request.imageUrl());
        recipe.setImageUrl(imageUrl);
        recipe.setImageAssetId(imageUrl == null ? null : TextNormalizer.trimToNull(request.imageAssetId()));
        recipe.getTags().clear();
        recipe.getTags().addAll(resolvedTags);
        if (recipe.getId() == null) {
            recipes.saveAndFlush(recipe);
        } else {
            // Detach previously loaded child rows so re-inserting the same composite keys after the
            // bulk delete does not collide with stale managed instances.
            recipeIngredients.findForRecipe(recipe.getId()).forEach(em::detach);
            steps.findForRecipe(recipe.getId()).forEach(em::detach);
        }

        recipeIngredients.deleteForRecipe(recipe.getId());
        steps.deleteForRecipe(recipe.getId());
        List<RecipeIngredient> lines = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            RecipeDtos.IngredientInput input = inputs.get(i);
            lines.add(new RecipeIngredient(recipe, found.get(input.ingredientId()), input.quantity(),
                    TextNormalizer.clean(input.unit()), Boolean.TRUE.equals(input.optional()), i + 1));
        }
        lines.forEach(em::persist);
        List<RecipeStep> newSteps = new ArrayList<>();
        List<RecipeDtos.StepInput> stepInputs = request.steps();
        for (int i = 0; i < stepInputs.size(); i++) {
            RecipeDtos.StepInput s = stepInputs.get(i);
            String url = TextNormalizer.trimToNull(s.imageUrl());
            newSteps.add(new RecipeStep(recipe, i + 1, s.instruction().strip(), url,
                    url == null ? null : TextNormalizer.trimToNull(s.imageAssetId())));
        }
        steps.saveAll(newSteps);
    }

    static RecipeDraft toDraft(RecipeDtos.RecipeRequest r) {
        List<RecipeDraft.IngredientLine> lines = r.ingredients() == null ? null : r.ingredients().stream()
                .map(i -> i == null ? null : new RecipeDraft.IngredientLine(
                        i.ingredientId() == null ? null : String.valueOf(i.ingredientId()),
                        i.quantity(), i.unit(), Boolean.TRUE.equals(i.optional())))
                .toList();
        List<RecipeDraft.StepLine> stepLines = r.steps() == null ? null : r.steps().stream()
                .map(s -> s == null ? null : new RecipeDraft.StepLine(s.instruction(), s.imageUrl()))
                .toList();
        return new RecipeDraft(r.title(), r.description(), r.prepTimeMins(), r.cookTimeMins(), r.difficulty(),
                r.estimatedCostBdt(), r.baseServings(), r.imageUrl(), lines, stepLines, r.tags());
    }

    /** Canonical representation of material recipe content, used to detect real edits. */
    static String fingerprint(Recipe r, List<RecipeIngredient> lines, List<RecipeStep> recipeSteps) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getTitle()).append('|').append(Objects.toString(r.getDescription(), "")).append('|')
                .append(r.getPrepTimeMins()).append('|').append(r.getCookTimeMins()).append('|')
                .append(r.getDifficulty()).append('|')
                .append(r.getEstimatedCostBdt() == null ? "" : r.getEstimatedCostBdt().stripTrailingZeros().toPlainString()).append('|')
                .append(r.getBaseServings()).append('|').append(Objects.toString(r.getImageUrl(), "")).append('|');
        r.getTags().stream().map(Tag::getNormalizedName).sorted().forEach(t -> sb.append(t).append(','));
        sb.append('|');
        for (RecipeIngredient l : lines) {
            BigDecimal q = l.getQuantity();
            sb.append(l.getIngredient().getId()).append(':').append(q.stripTrailingZeros().toPlainString())
                    .append(':').append(l.getUnit()).append(':').append(l.isOptional()).append(';');
        }
        sb.append('|');
        for (RecipeStep s : recipeSteps) {
            sb.append(s.getInstructionText()).append('#').append(Objects.toString(s.getImageUrl(), "")).append(';');
        }
        return sb.toString();
    }
}

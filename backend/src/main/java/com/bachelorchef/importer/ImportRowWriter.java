package com.bachelorchef.importer;

import com.bachelorchef.entity.Difficulty;
import com.bachelorchef.entity.Ingredient;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.RecipeIngredient;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.entity.RecipeStep;
import com.bachelorchef.entity.User;
import com.bachelorchef.repository.RecipeRepository;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.service.CatalogService;
import com.bachelorchef.service.TextNormalizer;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one validated import row as an official, approved recipe. Runs in its own transaction
 * (REQUIRES_NEW): the recipe row, ingredient links, steps, tags and any newly created catalogue
 * entries commit together or not at all, independently of other rows.
 */
@Component
public class ImportRowWriter {

    private final RecipeRepository recipes;
    private final UserRepository users;
    private final CatalogService catalog;
    private final EntityManager em;
    private final Clock clock;

    public ImportRowWriter(RecipeRepository recipes, UserRepository users, CatalogService catalog, EntityManager em, Clock clock) {
        this.recipes = recipes;
        this.users = users;
        this.catalog = catalog;
        this.em = em;
        this.clock = clock;
    }

    public enum Outcome { IMPORTED, SKIPPED_DUPLICATE }

    public record Result(Outcome outcome, Long recipeId, List<String> createdIngredients) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result write(ImportRow row, Long adminId, Long jobId) {
        String key = TextNormalizer.key(row.title());
        if (recipes.existsOfficialWithTitle(key, RecipeStatus.ARCHIVED)) {
            return new Result(Outcome.SKIPPED_DUPLICATE, null, List.of());
        }
        User admin = users.getReferenceById(adminId);
        List<String> created = new ArrayList<>();
        List<Ingredient> resolved = new ArrayList<>();
        for (ImportRow.Ingredient line : row.ingredients()) {
            boolean existed = em.createQuery("select count(i) from Ingredient i where i.normalizedName = :k", Long.class)
                    .setParameter("k", TextNormalizer.key(line.name())).getSingleResult() > 0;
            Ingredient ingredient = catalog.findOrCreateIngredient(line.name());
            if (!ingredient.isActive()) {
                throw new RowFailure("INGREDIENT_INACTIVE",
                        "Ingredient \"" + ingredient.getName() + "\" is deactivated in the catalogue; reactivate it or remove it from the row.");
            }
            if (!existed) {
                created.add(ingredient.getName());
            }
            resolved.add(ingredient);
        }

        Recipe recipe = new Recipe();
        String title = TextNormalizer.clean(row.title());
        recipe.setTitle(title);
        recipe.setNormalizedTitle(key);
        recipe.setDescription(TextNormalizer.trimToNull(row.description()));
        recipe.setTimes(row.prepTimeMins(), row.cookTimeMins()); // total is derived, never read from the file
        recipe.setDifficulty(Difficulty.parse(row.difficulty()).orElseThrow());
        recipe.setEstimatedCostBdt(row.estimatedCostBdt().setScale(2));
        recipe.setBaseServings(row.baseServings());
        recipe.setImageUrl(TextNormalizer.trimToNull(row.imageUrl()));
        recipe.setOfficial(true);
        recipe.setCreatedBy(admin);
        recipe.setStatus(RecipeStatus.APPROVED);
        recipe.setApprovedBy(admin);
        recipe.setApprovedAt(clock.instant());
        recipe.setImportJobId(jobId);
        recipe.getTags().addAll(catalog.resolveTags(row.tags(), true));
        recipes.saveAndFlush(recipe);

        for (int i = 0; i < row.ingredients().size(); i++) {
            ImportRow.Ingredient line = row.ingredients().get(i);
            em.persist(new RecipeIngredient(recipe, resolved.get(i), line.quantity(), TextNormalizer.clean(line.unit()),
                    line.optional(), i + 1));
        }
        for (int i = 0; i < row.steps().size(); i++) {
            ImportRow.Step step = row.steps().get(i);
            em.persist(new RecipeStep(recipe, i + 1, step.instruction().strip(), TextNormalizer.trimToNull(step.imageUrl()), null));
        }
        em.flush();
        return new Result(Outcome.IMPORTED, recipe.getId(), created);
    }

    /** A row-specific failure with a user-facing message; rolls back only that row. */
    public static class RowFailure extends RuntimeException {
        private final String code;

        public RowFailure(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}

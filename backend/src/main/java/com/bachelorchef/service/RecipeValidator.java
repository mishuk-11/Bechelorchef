package com.bachelorchef.service;

import com.bachelorchef.entity.Difficulty;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for recipe business rules, shared by the REST API and the bulk importer.
 * Returns field-keyed messages (camelCase field paths, e.g. {@code ingredients[2].quantity}).
 */
@Component
public class RecipeValidator {

    public static final int TITLE_MIN = 3;
    public static final int TITLE_MAX = 150;
    public static final int DESCRIPTION_MAX = 5000;
    public static final int PREP_MIN = 0;
    public static final int PREP_MAX = 60;
    public static final int COOK_MIN = 1;
    public static final int COOK_MAX = 60;
    public static final int TOTAL_MAX = TimeCategory.TOTAL_MAX;
    public static final int SERVINGS_MIN = 1;
    public static final int SERVINGS_MAX = 20;
    public static final BigDecimal COST_MAX = new BigDecimal("100000.00");
    public static final BigDecimal QUANTITY_MAX = new BigDecimal("9999999.999");
    public static final int UNIT_MAX = 20;
    public static final int STEP_TEXT_MAX = 2000;
    public static final int MAX_INGREDIENTS = 50;
    public static final int MAX_STEPS = 30;
    public static final int MAX_TAGS = 10;
    public static final int TAG_MAX = 40;

    private final MediaUrlPolicy urlPolicy;

    public RecipeValidator(MediaUrlPolicy urlPolicy) {
        this.urlPolicy = urlPolicy;
    }

    public Map<String, String> validate(RecipeDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateTitle(draft.title(), errors);
        if (TextNormalizer.length(draft.description()) > DESCRIPTION_MAX) {
            errors.put("description", "Must be at most " + DESCRIPTION_MAX + " characters.");
        }
        validateTimes(draft.prepTimeMins(), draft.cookTimeMins(), errors);
        if (Difficulty.parse(draft.difficulty()).isEmpty()) {
            errors.put("difficulty", "Must be one of EASY, MEDIUM or HARD.");
        }
        validateCost(draft.estimatedCostBdt(), errors);
        validateServings(draft.baseServings(), errors);
        checkUrl("imageUrl", draft.imageUrl(), errors);
        validateIngredients(draft.ingredients(), errors);
        validateSteps(draft.steps(), errors);
        validateTags(draft.tags(), errors);
        return errors;
    }

    void validateTitle(String title, Map<String, String> errors) {
        String cleaned = TextNormalizer.clean(title);
        if (cleaned == null) {
            errors.put("title", "Title is required.");
        } else if (TextNormalizer.length(cleaned) < TITLE_MIN || TextNormalizer.length(cleaned) > TITLE_MAX) {
            errors.put("title", "Must be between " + TITLE_MIN + " and " + TITLE_MAX + " characters.");
        }
    }

    /**
     * Field boundaries are checked first; the calculated total is only checked when both fields
     * are individually valid, so a single wrong field yields a single clear message.
     */
    void validateTimes(Integer prep, Integer cook, Map<String, String> errors) {
        boolean prepValid = prep != null && prep >= PREP_MIN && prep <= PREP_MAX;
        boolean cookValid = cook != null && cook >= COOK_MIN && cook <= COOK_MAX;
        if (!prepValid) {
            errors.put("prepTimeMins", "Prep time must be a whole number from " + PREP_MIN + " to " + PREP_MAX + " minutes.");
        }
        if (!cookValid) {
            errors.put("cookTimeMins", "Cook time must be a whole number from " + COOK_MIN + " to " + COOK_MAX + " minutes.");
        }
        if (prepValid && cookValid && prep + cook > TOTAL_MAX) {
            errors.put("totalTimeMins", "Total time (prep + cook = " + (prep + cook) + ") must not exceed "
                    + TOTAL_MAX + " minutes.");
        }
    }

    void validateCost(BigDecimal cost, Map<String, String> errors) {
        if (cost == null) {
            errors.put("estimatedCostBdt", "Estimated cost is required.");
        } else if (cost.signum() < 0 || cost.compareTo(COST_MAX) > 0) {
            errors.put("estimatedCostBdt", "Must be between 0 and " + COST_MAX.toPlainString() + " BDT.");
        } else if (cost.stripTrailingZeros().scale() > 2) {
            errors.put("estimatedCostBdt", "Use at most 2 decimal places.");
        }
    }

    void validateServings(Integer servings, Map<String, String> errors) {
        if (servings == null || servings < SERVINGS_MIN || servings > SERVINGS_MAX) {
            errors.put("baseServings", "Servings must be a whole number from " + SERVINGS_MIN + " to " + SERVINGS_MAX + ".");
        }
    }

    void validateIngredients(List<RecipeDraft.IngredientLine> lines, Map<String, String> errors) {
        if (lines == null || lines.isEmpty()) {
            errors.put("ingredients", "Add at least one ingredient.");
            return;
        }
        if (lines.size() > MAX_INGREDIENTS) {
            errors.put("ingredients", "A recipe can have at most " + MAX_INGREDIENTS + " ingredients.");
            return;
        }
        Set<String> seen = new HashSet<>();
        boolean anyRequired = false;
        for (int i = 0; i < lines.size(); i++) {
            RecipeDraft.IngredientLine line = lines.get(i);
            String prefix = "ingredients[" + i + "]";
            if (line == null || line.ingredientKey() == null || line.ingredientKey().isBlank()) {
                errors.put(prefix + ".ingredient", "Choose an ingredient.");
                continue;
            }
            if (!seen.add(line.ingredientKey())) {
                errors.put(prefix + ".ingredient", "This ingredient is listed more than once.");
            }
            validateQuantity(prefix, line.quantity(), errors);
            String unit = TextNormalizer.clean(line.unit());
            if (unit == null || TextNormalizer.length(unit) > UNIT_MAX) {
                errors.put(prefix + ".unit", "Unit is required (max " + UNIT_MAX + " characters).");
            }
            anyRequired |= !line.optional();
        }
        if (!anyRequired && !errors.containsKey("ingredients")) {
            errors.put("ingredients", "At least one ingredient must be required (not optional).");
        }
    }

    private static void validateQuantity(String prefix, BigDecimal quantity, Map<String, String> errors) {
        if (quantity == null || quantity.signum() <= 0) {
            errors.put(prefix + ".quantity", "Quantity must be greater than 0.");
        } else if (quantity.compareTo(QUANTITY_MAX) > 0 || quantity.stripTrailingZeros().scale() > 3) {
            errors.put(prefix + ".quantity", "Quantity is too large or has more than 3 decimal places.");
        }
    }

    void validateSteps(List<RecipeDraft.StepLine> steps, Map<String, String> errors) {
        if (steps == null || steps.isEmpty()) {
            errors.put("steps", "Add at least one cooking step.");
            return;
        }
        if (steps.size() > MAX_STEPS) {
            errors.put("steps", "A recipe can have at most " + MAX_STEPS + " steps.");
            return;
        }
        for (int i = 0; i < steps.size(); i++) {
            RecipeDraft.StepLine step = steps.get(i);
            String text = step == null ? null : TextNormalizer.trimToNull(step.instruction());
            if (text == null) {
                errors.put("steps[" + i + "].instruction", "Instruction is required.");
            } else if (TextNormalizer.length(text) > STEP_TEXT_MAX) {
                errors.put("steps[" + i + "].instruction", "Must be at most " + STEP_TEXT_MAX + " characters.");
            }
            if (step != null) {
                checkUrl("steps[" + i + "].imageUrl", step.imageUrl(), errors);
            }
        }
    }

    void validateTags(List<String> tags, Map<String, String> errors) {
        if (tags == null) {
            return;
        }
        if (tags.size() > MAX_TAGS) {
            errors.put("tags", "Use at most " + MAX_TAGS + " tags.");
            return;
        }
        for (String tag : tags) {
            String cleaned = TextNormalizer.clean(tag);
            if (cleaned == null || TextNormalizer.length(cleaned) > TAG_MAX) {
                errors.put("tags", "Tags must be 1 to " + TAG_MAX + " characters.");
                return;
            }
        }
    }

    private void checkUrl(String field, String url, Map<String, String> errors) {
        Optional<String> problem = urlPolicy.check(TextNormalizer.trimToNull(url));
        problem.ifPresent(message -> errors.put(field, message));
    }
}

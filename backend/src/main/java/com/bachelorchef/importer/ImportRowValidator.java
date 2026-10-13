package com.bachelorchef.importer;

import com.bachelorchef.entity.ImportFileType;
import com.bachelorchef.service.RecipeDraft;
import com.bachelorchef.service.RecipeValidator;
import com.bachelorchef.service.TextNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Applies the same {@link RecipeValidator} rules used by the REST API to an import row and
 * translates field paths to the import file's own column/key names.
 */
@Component
public class ImportRowValidator {

    private static final Pattern INDEXED = Pattern.compile("^(ingredients|steps)\\[(\\d+)]\\.?(.*)$");

    private final RecipeValidator validator;

    public ImportRowValidator(RecipeValidator validator) {
        this.validator = validator;
    }

    public List<ImportIssue> validate(ImportRow row, ImportFileType type) {
        RecipeDraft draft = new RecipeDraft(row.title(), row.description(), row.prepTimeMins(), row.cookTimeMins(),
                row.difficulty(), row.estimatedCostBdt(), row.baseServings(), row.imageUrl(),
                row.ingredients().stream().map(i -> new RecipeDraft.IngredientLine(TextNormalizer.key(i.name()),
                        i.quantity(), i.unit(), i.optional())).toList(),
                row.steps().stream().map(s -> new RecipeDraft.StepLine(s.instruction(), s.imageUrl())).toList(),
                row.tags());
        List<ImportIssue> issues = new ArrayList<>();
        for (Map.Entry<String, String> e : validator.validate(draft).entrySet()) {
            String[] mapped = mapField(e.getKey(), e.getValue(), type);
            issues.add(ImportIssue.error(row.rowNumber(), row.lineNumber(), mapped[0], codeFor(e.getKey()), mapped[1], row.excerpt()));
        }
        return issues;
    }

    /** @return {field, message} using the import format's naming. */
    public static String[] mapField(String field, String message, ImportFileType type) {
        Matcher m = INDEXED.matcher(field);
        if (m.matches()) {
            String list = m.group(1);
            int index = Integer.parseInt(m.group(2));
            String sub = snake(m.group(3));
            if (type == ImportFileType.CSV) {
                String column = list.equals("ingredients") ? "ingredients_csv" : "instructions_pipe";
                String label = list.equals("ingredients") ? "Item " : "Step ";
                return new String[]{column, label + (index + 1) + ": " + message};
            }
            return new String[]{list + "[" + index + "]" + (sub.isEmpty() ? "" : "." + sub), message};
        }
        if (type == ImportFileType.CSV) {
            switch (field) {
                case "ingredients" -> { return new String[]{"ingredients_csv", message}; }
                case "steps" -> { return new String[]{"instructions_pipe", message}; }
                case "tags" -> { return new String[]{"tags_pipe", message}; }
                default -> { }
            }
        }
        return new String[]{snake(field), message};
    }

    static String snake(String camel) {
        return switch (camel) {
            case "difficulty" -> "difficulty_level";
            case "instruction" -> "instruction_text";
            case "ingredient" -> "name";
            default -> camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static String codeFor(String field) {
        if (field.equals("totalTimeMins")) {
            return "TOTAL_TIME_EXCEEDED";
        }
        return "INVALID_VALUE";
    }
}

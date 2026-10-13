package com.bachelorchef.importer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Parses a JSON array of recipe objects. Strict: unknown fields are errors, server-controlled
 * fields (is_official, status, created_by, ...) are rejected, and {@code total_time_mins} is
 * ignored with a warning because the server always derives it.
 */
@Component
public class JsonRecipeParser {

    static final Set<String> ALLOWED = Set.of("title", "description", "prep_time_mins", "cook_time_mins",
            "difficulty_level", "estimated_cost_bdt", "base_servings", "image_url", "tags", "ingredients", "steps");
    static final Set<String> IGNORED = Set.of("total_time_mins");
    static final Set<String> FORBIDDEN = Set.of("id", "recipe_id", "is_official", "official", "status", "created_by",
            "approved_by", "approved_at", "moderation_note", "rating_avg", "rating_count", "user_id", "is_sample");
    static final Set<String> INGREDIENT_FIELDS = Set.of("name", "quantity", "unit", "is_optional");
    static final Set<String> STEP_FIELDS = Set.of("step_number", "instruction_text", "image_url");

    /** Reads one array element at a time from a shared stream; money/quantities stay exact BigDecimals. */
    private final ObjectReader reader;

    public JsonRecipeParser(ObjectMapper mapper) {
        this.reader = mapper.readerFor(JsonNode.class)
                .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public List<ParsedRecord> parse(byte[] content, int maxRows) {
        List<ParsedRecord> records = new ArrayList<>();
        try (JsonParser parser = reader.createParser(content)) {
            JsonToken first = parser.nextToken();
            if (first == null) {
                throw new FileRejectedException("EMPTY_FILE", "The file is empty.");
            }
            if (first != JsonToken.START_ARRAY) {
                throw new FileRejectedException("INVALID_ROOT", "The JSON root must be an array of recipe objects, e.g. [ {...}, {...} ].");
            }
            int row = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                row++;
                if (row > maxRows) {
                    throw new FileRejectedException("TOO_MANY_ROWS",
                            "The file has more than " + maxRows + " recipe objects. Split it into batches of at most " + maxRows + ".");
                }
                TokenStreamLocation location = parser.currentTokenLocation();
                int line = location == null ? -1 : location.getLineNr();
                JsonNode node = reader.readValue(parser);
                records.add(parseObject(node, row, line > 0 ? line : null));
            }
            if (parser.nextToken() != null) {
                throw new FileRejectedException("MALFORMED_FILE", "Unexpected content after the closing ] of the array.");
            }
        } catch (JacksonException e) {
            TokenStreamLocation loc = e.getLocation();
            String where = loc == null ? "" : " near line " + loc.getLineNr() + ", column " + loc.getColumnNr();
            throw new FileRejectedException("MALFORMED_FILE", "The JSON is malformed" + where + ".");
        }
        if (records.isEmpty()) {
            throw new FileRejectedException("EMPTY_FILE", "The JSON array contains no recipes.");
        }
        return records;
    }

    ParsedRecord parseObject(JsonNode node, int row, Integer line) {
        String excerpt = ImportText.excerpt(node.toString());
        List<ImportIssue> issues = new ArrayList<>();
        if (!node.isObject()) {
            issues.add(ImportIssue.error(row, line, null, "INVALID_TYPE", "Each array element must be a recipe object.", excerpt));
            return new ParsedRecord(row, line, null, issues, excerpt);
        }
        Ctx c = new Ctx(row, line, excerpt, issues);
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String key = entry.getKey();
            if (FORBIDDEN.contains(key)) {
                c.error(key, "FORBIDDEN_FIELD", "\"" + key + "\" cannot be set by an import; the server decides it.");
            } else if (IGNORED.contains(key)) {
                issues.add(ImportIssue.warning(row, line, key, "IGNORED_FIELD",
                        "total_time_mins is ignored; it is calculated as prep_time_mins + cook_time_mins.", excerpt));
            } else if (!ALLOWED.contains(key)) {
                c.error(key, "UNKNOWN_FIELD", "Unknown field \"" + key + "\".");
            }
        }
        String title = c.string(node, "title", "title");
        String description = c.string(node, "description", "description");
        Integer prep = c.integer(node, "prep_time_mins", "prep_time_mins");
        Integer cook = c.integer(node, "cook_time_mins", "cook_time_mins");
        String difficulty = c.string(node, "difficulty_level", "difficulty_level");
        BigDecimal cost = c.decimal(node, "estimated_cost_bdt", "estimated_cost_bdt");
        Integer servings = node.has("base_servings") && !node.get("base_servings").isNull()
                ? c.integer(node, "base_servings", "base_servings") : Integer.valueOf(1);
        String imageUrl = c.string(node, "image_url", "image_url");
        List<String> tags = parseTags(node.get("tags"), c);
        List<ImportRow.Ingredient> ingredients = parseIngredients(node.get("ingredients"), c);
        List<ImportRow.Step> steps = parseSteps(node.get("steps"), c);
        ImportRow parsed = new ImportRow(row, line, title, description, prep, cook, difficulty, cost, servings, imageUrl,
                tags, ingredients, steps, excerpt);
        return new ParsedRecord(row, line, parsed, issues, excerpt);
    }

    private static List<String> parseTags(JsonNode node, Ctx c) {
        List<String> tags = new ArrayList<>();
        if (node == null || node.isNull()) {
            return tags;
        }
        if (!node.isArray()) {
            c.error("tags", "INVALID_TYPE", "tags must be an array of strings.");
            return tags;
        }
        for (JsonNode t : node) {
            if (!t.isString()) {
                c.error("tags", "INVALID_TYPE", "tags must be an array of strings.");
                return tags;
            }
            tags.add(t.stringValue());
        }
        return tags;
    }

    private static List<ImportRow.Ingredient> parseIngredients(JsonNode node, Ctx c) {
        List<ImportRow.Ingredient> result = new ArrayList<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (!node.isArray()) {
            c.error("ingredients", "INVALID_TYPE", "ingredients must be an array of objects.");
            return result;
        }
        for (int i = 0; i < node.size(); i++) {
            JsonNode item = node.get(i);
            String path = "ingredients[" + i + "]";
            if (!item.isObject()) {
                c.error(path, "INVALID_TYPE", path + " must be an object with name, quantity and unit.");
                continue;
            }
            c.checkKeys(item, INGREDIENT_FIELDS, path);
            String name = c.string(item, "name", path + ".name");
            BigDecimal quantity = c.decimal(item, "quantity", path + ".quantity");
            String unit = c.string(item, "unit", path + ".unit");
            boolean optional = false;
            JsonNode opt = item.get("is_optional");
            if (opt != null && !opt.isNull()) {
                if (opt.isBoolean()) {
                    optional = opt.booleanValue();
                } else {
                    c.error(path + ".is_optional", "INVALID_TYPE", path + ".is_optional must be true or false.");
                }
            }
            if (name == null || name.isBlank()) {
                c.error(path + ".name", "EMPTY_INGREDIENT", path + ".name is required.");
                continue;
            }
            result.add(new ImportRow.Ingredient(name.strip(), quantity, unit, optional));
        }
        return result;
    }

    private static List<ImportRow.Step> parseSteps(JsonNode node, Ctx c) {
        if (node == null || node.isNull()) {
            return new ArrayList<>();
        }
        if (!node.isArray()) {
            c.error("steps", "INVALID_TYPE", "steps must be an array of objects.");
            return new ArrayList<>();
        }
        record Numbered(Integer number, int index, ImportRow.Step step) {
        }
        List<Numbered> numbered = new ArrayList<>();
        int withNumber = 0;
        for (int i = 0; i < node.size(); i++) {
            JsonNode item = node.get(i);
            String path = "steps[" + i + "]";
            if (!item.isObject()) {
                c.error(path, "INVALID_TYPE", path + " must be an object with instruction_text.");
                continue;
            }
            c.checkKeys(item, STEP_FIELDS, path);
            Integer number = item.has("step_number") && !item.get("step_number").isNull()
                    ? c.integer(item, "step_number", path + ".step_number") : null;
            if (number != null) {
                withNumber++;
            }
            String text = c.string(item, "instruction_text", path + ".instruction_text");
            if (text == null || text.isBlank()) {
                c.error(path + ".instruction_text", "EMPTY_STEP", path + ".instruction_text is required.");
                continue;
            }
            numbered.add(new Numbered(number, i, new ImportRow.Step(text.strip(), c.string(item, "image_url", path + ".image_url"))));
        }
        if (withNumber > 0 && withNumber < node.size()) {
            c.error("steps", "INCONSISTENT_STEPS", "Either give every step a step_number or none of them.");
        }
        if (withNumber == node.size() && withNumber > 0) {
            Set<Integer> seen = new HashSet<>();
            for (Numbered n : numbered) {
                if (n.number() != null && !seen.add(n.number())) {
                    c.error("steps", "DUPLICATE_STEP_NUMBER", "step_number " + n.number() + " is used more than once.");
                }
            }
            numbered.sort(Comparator.comparing(Numbered::number, Comparator.nullsLast(Integer::compare)));
        }
        // Steps are renumbered 1..n in this order when stored.
        return new ArrayList<>(numbered.stream().map(Numbered::step).toList());
    }

    /** Per-record typed accessors that collect type errors. */
    private static final class Ctx {
        private final int row;
        private final Integer line;
        private final String excerpt;
        private final List<ImportIssue> issues;

        Ctx(int row, Integer line, String excerpt, List<ImportIssue> issues) {
            this.row = row;
            this.line = line;
            this.excerpt = excerpt;
            this.issues = issues;
        }

        void error(String field, String code, String message) {
            issues.add(ImportIssue.error(row, line, field, code, message, excerpt));
        }

        void checkKeys(JsonNode obj, Set<String> allowed, String path) {
            for (String key : obj.propertyNames()) {
                if (!allowed.contains(key)) {
                    error(path + "." + key, "UNKNOWN_FIELD", "Unknown field \"" + key + "\" in " + path + ".");
                }
            }
        }

        String string(JsonNode obj, String key, String path) {
            JsonNode v = obj.get(key);
            if (v == null || v.isNull()) {
                return null;
            }
            if (!v.isString()) {
                error(path, "INVALID_TYPE", path + " must be a string.");
                return null;
            }
            return v.stringValue();
        }

        Integer integer(JsonNode obj, String key, String path) {
            JsonNode v = obj.get(key);
            if (v == null || v.isNull()) {
                return null;
            }
            if (!v.isIntegralNumber() || !v.canConvertToInt()) {
                error(path, "INVALID_TYPE", path + " must be a whole number" + (v.isString() ? " (not a string)." : "."));
                return null;
            }
            return v.intValue();
        }

        BigDecimal decimal(JsonNode obj, String key, String path) {
            JsonNode v = obj.get(key);
            if (v == null || v.isNull()) {
                return null;
            }
            if (!v.isNumber()) {
                error(path, "INVALID_TYPE", path + " must be a number" + (v.isString() ? " (not a string)." : "."));
                return null;
            }
            return v.decimalValue();
        }
    }
}

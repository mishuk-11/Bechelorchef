package com.bachelorchef.importer;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;

/**
 * Parses the documented CSV import format with Apache Commons CSV (RFC 4180: quoted fields may
 * contain commas, escaped quotes and line breaks).
 *
 * <pre>
 * Required: title, prep_time_mins, cook_time_mins, difficulty_level, estimated_cost_bdt, ingredients_csv, instructions_pipe
 * Optional: description, base_servings (default 1), image_url, tags_pipe
 * ingredients_csv   = Name:quantity:unit[:optional] items separated by |
 * instructions_pipe = steps separated by |
 * Escapes inside these fields: \| literal pipe, \: literal colon, \\ backslash
 * </pre>
 */
@Component
public class CsvRecipeParser {

    public static final List<String> REQUIRED_COLUMNS = List.of("title", "prep_time_mins", "cook_time_mins",
            "difficulty_level", "estimated_cost_bdt", "ingredients_csv", "instructions_pipe");
    public static final List<String> OPTIONAL_COLUMNS = List.of("description", "base_servings", "image_url", "tags_pipe");

    public List<ParsedRecord> parse(String text, int maxRows) {
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(false)
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                .get();
        List<ParsedRecord> records = new ArrayList<>();
        try (CSVParser parser = CSVParser.builder().setReader(new StringReader(text)).setFormat(format).get()) {
            List<String> headers = checkHeader(parser.getHeaderNames());
            Iterator<CSVRecord> iterator = parser.iterator();
            int rowNumber = 0;
            while (true) {
                CSVRecord record;
                try {
                    if (!iterator.hasNext()) {
                        break;
                    }
                    record = iterator.next();
                } catch (UncheckedIOException | IllegalStateException e) {
                    throw new FileRejectedException("MALFORMED_FILE",
                            "The CSV is malformed near line " + parser.getCurrentLineNumber() + " (check for an unclosed quote).");
                }
                rowNumber++;
                if (rowNumber > maxRows) {
                    throw new FileRejectedException("TOO_MANY_ROWS",
                            "The file has more than " + maxRows + " data rows. Split it into batches of at most " + maxRows + ".");
                }
                records.add(parseRecord(record, headers, rowNumber, (int) parser.getCurrentLineNumber()));
            }
        } catch (IllegalArgumentException e) {
            throw new FileRejectedException("INVALID_HEADER", "The header row is invalid: duplicate or empty column names.");
        } catch (IOException e) {
            throw new FileRejectedException("MALFORMED_FILE", "The CSV file could not be read.");
        }
        if (records.isEmpty()) {
            throw new FileRejectedException("EMPTY_FILE", "The file has a header but no data rows.");
        }
        return records;
    }

    private static List<String> checkHeader(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new FileRejectedException("EMPTY_FILE", "The file is empty.");
        }
        List<String> headers = raw.stream().map(h -> h == null ? "" : h.strip().toLowerCase(Locale.ROOT)).toList();
        Set<String> present = new LinkedHashSet<>(headers);
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !present.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new FileRejectedException("MISSING_COLUMNS", "Missing required column(s): " + String.join(", ", missing) + ".");
        }
        List<String> unknown = headers.stream()
                .filter(h -> !REQUIRED_COLUMNS.contains(h) && !OPTIONAL_COLUMNS.contains(h)).toList();
        if (!unknown.isEmpty()) {
            throw new FileRejectedException("UNKNOWN_COLUMNS", "Unknown column(s): " + String.join(", ", unknown)
                    + ". Allowed optional columns: " + String.join(", ", OPTIONAL_COLUMNS) + ".");
        }
        return headers;
    }

    ParsedRecord parseRecord(CSVRecord record, List<String> headers, int row, int line) {
        String excerpt = ImportText.excerpt(String.join(",", record.values()));
        List<ImportIssue> issues = new ArrayList<>();
        if (record.size() != headers.size()) {
            issues.add(ImportIssue.error(row, line, null, "COLUMN_COUNT",
                    "Expected " + headers.size() + " columns but found " + record.size() + ".", excerpt));
            return new ParsedRecord(row, line, null, issues, excerpt);
        }
        Fields f = new Fields(record, headers, row, line, excerpt, issues);
        Integer prep = f.integer("prep_time_mins", true);
        Integer cook = f.integer("cook_time_mins", true);
        BigDecimal cost = f.decimal("estimated_cost_bdt");
        Integer servings = f.has("base_servings") && !f.text("base_servings").isBlank() ? f.integer("base_servings", false) : Integer.valueOf(1);
        List<ImportRow.Ingredient> ingredients = parseIngredients(f.text("ingredients_csv"), f);
        List<ImportRow.Step> steps = parseSteps(f.text("instructions_pipe"), f);
        List<String> tags = f.has("tags_pipe") ? parseTags(f.text("tags_pipe")) : List.of();
        ImportRow parsed = new ImportRow(row, line, f.text("title"), f.has("description") ? blankToNull(f.text("description")) : null,
                prep, cook, f.text("difficulty_level").strip(), cost, servings,
                f.has("image_url") ? blankToNull(f.text("image_url")) : null, tags, ingredients, steps, excerpt);
        return new ParsedRecord(row, line, parsed, issues, excerpt);
    }

    static List<ImportRow.Ingredient> parseIngredients(String value, Fields f) {
        List<ImportRow.Ingredient> result = new ArrayList<>();
        if (value.isBlank()) {
            return result; // reported by business validation as "at least one ingredient"
        }
        List<String> items = ImportText.splitEscaped(value, '|');
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i).strip();
            String where = "Item " + (i + 1) + " (\"" + ImportText.excerpt(ImportText.unescape(item)) + "\")";
            if (item.isEmpty()) {
                f.error("ingredients_csv", "EMPTY_INGREDIENT", "Item " + (i + 1) + " is empty (check for a doubled or trailing |).");
                continue;
            }
            List<String> parts = ImportText.splitEscaped(item, ':');
            boolean optional = parts.size() == 4 && "optional".equalsIgnoreCase(parts.get(3).strip());
            if (parts.size() != 3 && !optional) {
                f.error("ingredients_csv", "MALFORMED_INGREDIENT", where + " must be Name:quantity:unit or Name:quantity:unit:optional.");
                continue;
            }
            String name = ImportText.unescape(parts.get(0)).strip();
            String unit = ImportText.unescape(parts.get(2)).strip();
            BigDecimal quantity = null;
            if (name.isEmpty()) {
                f.error("ingredients_csv", "EMPTY_INGREDIENT", where + " has an empty ingredient name.");
                continue;
            }
            try {
                quantity = new BigDecimal(parts.get(1).strip());
            } catch (NumberFormatException e) {
                f.error("ingredients_csv", "INVALID_NUMBER", where + " has an invalid quantity \"" + parts.get(1).strip() + "\".");
                continue;
            }
            result.add(new ImportRow.Ingredient(name, quantity, unit, optional));
        }
        return result;
    }

    static List<ImportRow.Step> parseSteps(String value, Fields f) {
        List<ImportRow.Step> result = new ArrayList<>();
        if (value.isBlank()) {
            return result;
        }
        List<String> items = ImportText.splitEscaped(value, '|');
        for (int i = 0; i < items.size(); i++) {
            String text = ImportText.unescape(items.get(i)).strip();
            if (text.isEmpty()) {
                f.error("instructions_pipe", "EMPTY_STEP", "Step " + (i + 1) + " is empty (check for a doubled or trailing |).");
                continue;
            }
            result.add(new ImportRow.Step(text, null));
        }
        return result;
    }

    static List<String> parseTags(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return ImportText.splitEscaped(value, '|').stream().map(ImportText::unescape).map(String::strip)
                .filter(s -> !s.isEmpty()).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Typed access to the cells of one record, collecting conversion errors. */
    static final class Fields {
        private final CSVRecord record;
        private final List<String> headers;
        private final int row;
        private final int line;
        private final String excerpt;
        private final List<ImportIssue> issues;

        Fields(CSVRecord record, List<String> headers, int row, int line, String excerpt, List<ImportIssue> issues) {
            this.record = record;
            this.headers = headers;
            this.row = row;
            this.line = line;
            this.excerpt = excerpt;
            this.issues = issues;
        }

        boolean has(String column) {
            return headers.contains(column);
        }

        String text(String column) {
            int index = headers.indexOf(column);
            return index < 0 ? "" : record.get(index);
        }

        Integer integer(String column, boolean required) {
            String raw = text(column).strip();
            if (raw.isEmpty()) {
                if (required) {
                    error(column, "MISSING_VALUE", column + " is required.");
                }
                return null;
            }
            if (!raw.matches("-?\\d{1,9}")) {
                error(column, "INVALID_NUMBER", column + " must be a whole number (got \"" + ImportText.excerpt(raw) + "\").");
                return null;
            }
            return Integer.valueOf(raw);
        }

        BigDecimal decimal(String column) {
            String raw = text(column).strip();
            if (raw.isEmpty()) {
                error(column, "MISSING_VALUE", column + " is required.");
                return null;
            }
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException e) {
                error(column, "INVALID_NUMBER", column + " must be a number (got \"" + ImportText.excerpt(raw) + "\").");
                return null;
            }
        }

        void error(String field, String code, String message) {
            issues.add(ImportIssue.error(row, line, field, code, message, excerpt));
        }
    }
}

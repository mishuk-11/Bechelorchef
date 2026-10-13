package com.bachelorchef.importer;

import com.bachelorchef.entity.ImportFileType;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses a bulk ingredient file into raw rows (no business validation, no database access).
 * <pre>
 * CSV : header row required; columns name (required), category, default_unit (optional, any order)
 *       e.g.  name,category,default_unit
 *             Red Lentils,Legumes,cup
 *             "Chili, dried",Spices,pcs
 * JSON: an array of objects: [{"name": "Red Lentils", "category": "Legumes", "default_unit": "cup"}]
 *       ("defaultUnit" is accepted as an alias of "default_unit"; values must be strings or null)
 * </pre>
 * File-level problems (empty, bad header, not an array, more than {@code maxRows} rows, malformed
 * text) throw {@link FileRejectedException}; problems with a single row are reported on that row.
 */
@Component
public class IngredientFileParser {

    /** One data row. {@code error} is set when the row's structure is unusable. */
    public record Row(int rowNumber, String name, String category, String defaultUnit, String error) {
    }

    static final Set<String> CSV_COLUMNS = Set.of("name", "category", "default_unit");
    private static final Map<String, String> JSON_KEYS = Map.of(
            "name", "name", "category", "category", "default_unit", "default_unit", "defaultunit", "default_unit");

    private final ObjectMapper mapper;

    public IngredientFileParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<Row> parse(String text, ImportFileType type, int maxRows) {
        List<Row> rows = type == ImportFileType.CSV ? parseCsv(text, maxRows) : parseJson(text, maxRows);
        if (rows.isEmpty()) {
            throw new FileRejectedException("NO_ROWS", "The file contains no ingredients.");
        }
        return rows;
    }

    // ------------------------------------------------------------------ CSV

    List<Row> parseCsv(String text, int maxRows) {
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .get();
        try (CSVParser parser = CSVParser.builder().setReader(new StringReader(text)).setFormat(format).get()) {
            Map<String, Integer> header = checkHeader(parser.getHeaderNames());
            List<Row> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() == maxRows) {
                    throw new FileRejectedException("TOO_MANY_ROWS", "The file has more than " + maxRows + " ingredients. Split it into smaller files.");
                }
                int rowNumber = rows.size() + 1;
                if (!record.isConsistent()) {
                    rows.add(new Row(rowNumber, null, null, null,
                            "Expected " + header.size() + " columns but found " + record.size() + "."));
                    continue;
                }
                rows.add(new Row(rowNumber, cell(record, header, "name"), cell(record, header, "category"),
                        cell(record, header, "default_unit"), null));
            }
            return rows;
        } catch (IOException | IllegalArgumentException | IllegalStateException | java.io.UncheckedIOException e) {
            throw new FileRejectedException("MALFORMED_FILE", "The CSV file could not be read: " + ImportText.excerpt(e.getMessage()));
        }
    }

    private static Map<String, Integer> checkHeader(List<String> names) {
        Map<String, Integer> header = new java.util.LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String column = names.get(i) == null ? "" : names.get(i).strip().toLowerCase(Locale.ROOT);
            if (column.isEmpty()) {
                continue;
            }
            if (!CSV_COLUMNS.contains(column)) {
                throw new FileRejectedException("BAD_HEADER",
                        "Unknown column \"" + ImportText.excerpt(names.get(i)) + "\". Allowed columns: name, category, default_unit.");
            }
            if (header.put(column, i) != null) {
                throw new FileRejectedException("BAD_HEADER", "The column \"" + column + "\" appears twice.");
            }
        }
        if (!header.containsKey("name")) {
            throw new FileRejectedException("BAD_HEADER", "The first line must be a header with a \"name\" column (optional: category, default_unit).");
        }
        return header;
    }

    private static String cell(CSVRecord record, Map<String, Integer> header, String column) {
        Integer index = header.get(column);
        return index == null || index >= record.size() ? null : record.get(index);
    }

    // ------------------------------------------------------------------ JSON

    List<Row> parseJson(String text, int maxRows) {
        JsonNode root;
        try {
            root = mapper.readTree(text);
        } catch (JacksonException e) {
            throw new FileRejectedException("MALFORMED_FILE", "The file is not valid JSON.");
        }
        if (root == null || !root.isArray()) {
            throw new FileRejectedException("NOT_AN_ARRAY", "The JSON file must be an array of ingredient objects, e.g. [{\"name\": \"Egg\"}].");
        }
        if (root.size() > maxRows) {
            throw new FileRejectedException("TOO_MANY_ROWS", "The file has more than " + maxRows + " ingredients. Split it into smaller files.");
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < root.size(); i++) {
            rows.add(jsonRow(root.get(i), i + 1));
        }
        return rows;
    }

    private static Row jsonRow(JsonNode node, int rowNumber) {
        if (node == null || !node.isObject()) {
            return new Row(rowNumber, null, null, null, "Each item must be an object like {\"name\": \"Egg\"}.");
        }
        String name = null, category = null, unit = null;
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String field = JSON_KEYS.get(entry.getKey().toLowerCase(Locale.ROOT));
            if (field == null) {
                return new Row(rowNumber, null, null, null,
                        "Unknown field \"" + ImportText.excerpt(entry.getKey()) + "\". Allowed: name, category, default_unit.");
            }
            if (!seen.add(field)) {
                return new Row(rowNumber, null, null, null, "The field \"" + field + "\" is given twice.");
            }
            JsonNode value = entry.getValue();
            if (!value.isNull() && !value.isString()) {
                return new Row(rowNumber, null, null, null, "\"" + entry.getKey() + "\" must be text.");
            }
            String text = value.isNull() ? null : value.stringValue();
            switch (field) {
                case "name" -> name = text;
                case "category" -> category = text;
                default -> unit = text;
            }
        }
        return new Row(rowNumber, name, category, unit, null);
    }
}

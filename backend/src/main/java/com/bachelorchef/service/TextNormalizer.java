package com.bachelorchef.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** Consistent trimming and case-folding for names, keys and emails. */
public final class TextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextNormalizer() {
    }

    /** Trims and collapses internal whitespace; returns null for null/blank input. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    /** Uniqueness key for ingredients, tags and titles: cleaned and lower-cased (locale independent). */
    public static String key(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    /**
     * Length in Unicode code points, which is how PostgreSQL counts characters (char_length and
     * varchar(n)). String.length() counts UTF-16 units, so an emoji would count twice.
     */
    public static int length(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    public static String email(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    /** Trims free text but preserves internal line breaks; null when blank. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

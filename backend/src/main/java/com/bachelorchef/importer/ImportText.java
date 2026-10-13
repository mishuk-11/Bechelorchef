package com.bachelorchef.importer;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Text helpers shared by the parsers: strict UTF-8 decoding, escaped splitting, safe excerpts. */
public final class ImportText {

    public static final int EXCERPT_MAX = 200;

    private ImportText() {
    }

    /** Decodes strict UTF-8 (rejecting binary/invalid bytes) and removes a leading BOM. */
    public static String decodeUtf8(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (text.indexOf('\0') >= 0) {
                throw new FileRejectedException("MALFORMED_FILE", "The file contains binary data and is not a text file.");
            }
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new FileRejectedException("MALFORMED_FILE", "The file must be UTF-8 encoded text.");
        }
    }

    /**
     * Splits on an unescaped separator. Escapes: {@code \|} literal pipe, {@code \:} literal colon,
     * {@code \\} backslash. Other backslashes are kept as-is. Parts are not trimmed.
     */
    public static List<String> splitEscaped(String value, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (next == '|' || next == ':' || next == '\\') {
                    current.append('\\').append(next); // keep escaped for nested splitting
                    i++;
                    continue;
                }
            }
            if (c == separator) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    /** Removes the escape backslashes left by {@link #splitEscaped}. */
    public static String unescape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (next == '|' || next == ':' || next == '\\') {
                    sb.append(next);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Short, single-line excerpt with control characters removed. */
    public static String excerpt(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\p{Cntrl}&&[^\t]]", " ").replaceAll("\\s+", " ").strip();
        return cleaned.length() > EXCERPT_MAX ? cleaned.substring(0, EXCERPT_MAX - 3) + "..." : cleaned;
    }

    /** Keeps only a safe base filename (no path, no control or shell-special characters). */
    public static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "upload";
        }
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        if (base.isEmpty()) {
            return "upload";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }
}

package com.bachelorchef.entity;

import java.util.Locale;
import java.util.Optional;

public enum Difficulty {
    EASY("Easy"), MEDIUM("Medium"), HARD("Hard");

    private final String label;

    Difficulty(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Case-insensitive parse; empty when the value is not a known difficulty. */
    public static Optional<Difficulty> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

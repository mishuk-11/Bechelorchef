package com.bachelorchef.service;

/**
 * Timing label derived from the computed total time (prep + cook):
 * Express &le; 15, Bachelor Quick 16..20, Extended 21..60.
 */
public enum TimeCategory {
    EXPRESS("Express"),
    BACHELOR_QUICK("Bachelor Quick"),
    EXTENDED("Extended");

    public static final int EXPRESS_MAX = 15;
    public static final int QUICK_MAX = 20;
    public static final int TOTAL_MAX = 60;

    private final String label;

    TimeCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * @throws IllegalArgumentException when the total is outside 1..60 (such a recipe is invalid)
     */
    public static TimeCategory of(int totalTimeMins) {
        if (totalTimeMins < 1 || totalTimeMins > TOTAL_MAX) {
            throw new IllegalArgumentException("Total time must be between 1 and " + TOTAL_MAX + " minutes");
        }
        if (totalTimeMins <= EXPRESS_MAX) {
            return EXPRESS;
        }
        if (totalTimeMins <= QUICK_MAX) {
            return BACHELOR_QUICK;
        }
        return EXTENDED;
    }
}

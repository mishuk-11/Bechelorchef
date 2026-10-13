package com.bachelorchef.importer;

import com.bachelorchef.entity.ImportIssueSeverity;

/**
 * A problem found in an import file. {@code rowNumber} is the 1-based data record number (the CSV
 * header is not counted); {@code lineNumber} is the physical line where the record ends (CSV) or
 * starts (JSON). File-level issues have a null row number.
 */
public record ImportIssue(Integer rowNumber, Integer lineNumber, String field, ImportIssueSeverity severity,
                          String code, String message, String excerpt) {

    public static ImportIssue error(Integer row, Integer line, String field, String code, String message, String excerpt) {
        return new ImportIssue(row, line, field, ImportIssueSeverity.ERROR, code, message, excerpt);
    }

    public static ImportIssue warning(Integer row, Integer line, String field, String code, String message, String excerpt) {
        return new ImportIssue(row, line, field, ImportIssueSeverity.WARNING, code, message, excerpt);
    }

    public boolean isError() {
        return severity == ImportIssueSeverity.ERROR;
    }
}

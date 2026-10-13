package com.bachelorchef.importer;

import java.util.List;

/** Parser output for one data record: the row (null if structurally unusable) plus its issues. */
public record ParsedRecord(int rowNumber, Integer lineNumber, ImportRow row, List<ImportIssue> issues, String excerpt) {

    public boolean hasErrors() {
        return row == null || issues.stream().anyMatch(ImportIssue::isError);
    }
}

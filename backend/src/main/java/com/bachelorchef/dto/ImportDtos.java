package com.bachelorchef.dto;

import com.bachelorchef.entity.ImportFileType;
import com.bachelorchef.entity.ImportIssueSeverity;
import com.bachelorchef.entity.ImportStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class ImportDtos {

    private ImportDtos() {
    }

    public record IssueView(Integer rowNumber, Integer lineNumber, String field, ImportIssueSeverity severity,
                            String code, String message, String excerpt) {
    }

    public record RowPreview(int rowNumber, Integer lineNumber, String title, boolean valid,
                             Integer totalTimeMins, int ingredientCount, int stepCount) {
    }

    public record PreviewResponse(Long jobId, ImportStatus status, ImportFileType fileType, String filename,
                                  int totalRows, int validRows, int invalidRows,
                                  List<RowPreview> rows, List<IssueView> issues) {
    }

    public record ConfirmRequest(@NotNull(message = "jobId is required.") Long jobId) {
    }

    public record JobView(Long id, String filename, ImportFileType fileType, ImportStatus status,
                          UserDtos.AuthorSummary uploadedBy, Instant startedAt, Instant finishedAt,
                          int totalRows, int validRows, int importedRows, int failedRows, int skippedRows,
                          String summary) {
    }

    public record JobDetail(JobView job, List<IssueView> issues) {
    }
}

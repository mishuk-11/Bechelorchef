package com.bachelorchef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bulk_upload_errors")
public class BulkUploadError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "error_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private BulkUploadJob job;

    @Column(name = "row_number")
    private Integer rowNumber;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "field_name", length = 80)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ImportIssueSeverity severity;

    @Column(name = "error_code", nullable = false, length = 40)
    private String errorCode;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "row_excerpt", length = 300)
    private String rowExcerpt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BulkUploadError() {
    }

    public BulkUploadError(BulkUploadJob job, Integer rowNumber, Integer lineNumber, String fieldName,
                           ImportIssueSeverity severity, String errorCode, String message, String rowExcerpt) {
        this.job = job;
        this.rowNumber = rowNumber;
        this.lineNumber = lineNumber;
        this.fieldName = fieldName;
        this.severity = severity;
        this.errorCode = errorCode;
        this.message = message;
        this.rowExcerpt = rowExcerpt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public BulkUploadJob getJob() { return job; }
    public Integer getRowNumber() { return rowNumber; }
    public Integer getLineNumber() { return lineNumber; }
    public String getFieldName() { return fieldName; }
    public ImportIssueSeverity getSeverity() { return severity; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getRowExcerpt() { return rowExcerpt; }
}

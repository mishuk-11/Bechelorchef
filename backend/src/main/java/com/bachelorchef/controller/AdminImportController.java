package com.bachelorchef.controller;

import com.bachelorchef.dto.ImportDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.importer.ImportService;
import com.bachelorchef.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk recipe import. The /api/v1/admin/** rule in the security filter chain rejects non-admins
 * before the multipart body is parsed; {@code @PreAuthorize} is a second line of defence.
 */
@RestController
@RequestMapping("/api/v1/admin/imports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: bulk import")
public class AdminImportController {

    private final ImportService imports;

    public AdminImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validate a CSV/JSON file (max 500 recipes) and preview rows; writes no recipes")
    public ImportDtos.PreviewResponse preview(@AuthenticationPrincipal AuthUser admin, @RequestPart("file") MultipartFile file) {
        return imports.preview(admin, file);
    }

    @PostMapping
    @Operation(summary = "Confirm a previewed job; idempotent — retries return the stored result")
    public ImportDtos.JobDetail confirm(@AuthenticationPrincipal AuthUser admin, @Valid @RequestBody ImportDtos.ConfirmRequest request) {
        return imports.confirm(admin, request.jobId());
    }

    @GetMapping
    public PageResponse<ImportDtos.JobView> list(@RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return imports.list(page, size);
    }

    @GetMapping("/{jobId}")
    public ImportDtos.JobDetail detail(@PathVariable Long jobId) {
        return imports.detail(jobId);
    }

    @GetMapping(value = "/{jobId}/errors.csv", produces = "text/csv")
    @Operation(summary = "Download the job's row issues as CSV")
    public ResponseEntity<byte[]> errorsCsv(@PathVariable Long jobId) throws java.io.IOException {
        ImportDtos.JobDetail detail = imports.detail(jobId);
        StringBuilder out = new StringBuilder();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.RFC4180.builder()
                .setHeader("row_number", "line_number", "severity", "code", "field", "message", "excerpt").get())) {
            for (ImportDtos.IssueView i : detail.issues()) {
                printer.printRecord(i.rowNumber(), i.lineNumber(), i.severity(), i.code(), i.field(), i.message(),
                        safeCell(i.excerpt()));
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"import-" + jobId + "-issues.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Neutralises spreadsheet formula injection in exported cells. */
    static String safeCell(String value) {
        if (value != null && !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}

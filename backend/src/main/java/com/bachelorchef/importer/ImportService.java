package com.bachelorchef.importer;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.ImportDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.Pagination;
import com.bachelorchef.entity.BulkUploadError;
import com.bachelorchef.entity.BulkUploadJob;
import com.bachelorchef.entity.ImportFileType;
import com.bachelorchef.entity.ImportIssueSeverity;
import com.bachelorchef.entity.ImportStatus;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.mapper.UserMapper;
import com.bachelorchef.repository.BulkUploadErrorRepository;
import com.bachelorchef.repository.BulkUploadJobRepository;
import com.bachelorchef.repository.RecipeRepository;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.security.RateLimiter;
import com.bachelorchef.service.AuditService;
import com.bachelorchef.service.TextNormalizer;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Two-step bulk import.
 * <ol>
 *   <li><b>Preview</b> validates the whole file and stores a PREVIEWED job with row-level issues
 *       and the normalised valid rows. No recipe, ingredient or tag is written.</li>
 *   <li><b>Confirm</b> atomically claims the job (PREVIEWED → PROCESSING, exactly once) and writes
 *       each valid row in its own transaction. Retrying a confirmation returns the stored result
 *       instead of importing again.</li>
 * </ol>
 * Duplicate policy: a row whose normalised title matches a non-archived official recipe (or an
 * earlier row of the same file) is skipped/rejected rather than creating a second copy.
 * The admin role is enforced by the security filter chain before the multipart body is parsed.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final Set<String> CSV_TYPES = Set.of("text/csv", "application/csv", "application/vnd.ms-excel",
            "text/plain", "application/octet-stream");
    private static final Set<String> JSON_TYPES = Set.of("application/json", "text/json", "text/plain", "application/octet-stream");

    private final AppProperties properties;
    private final CsvRecipeParser csvParser;
    private final JsonRecipeParser jsonParser;
    private final ImportRowValidator rowValidator;
    private final ImportRowWriter rowWriter;
    private final BulkUploadJobRepository jobs;
    private final BulkUploadErrorRepository errors;
    private final RecipeRepository recipes;
    private final UserRepository users;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ImportService(AppProperties properties, CsvRecipeParser csvParser, JsonRecipeParser jsonParser,
                         ImportRowValidator rowValidator, ImportRowWriter rowWriter, BulkUploadJobRepository jobs,
                         BulkUploadErrorRepository errors, RecipeRepository recipes, UserRepository users,
                         AuditService audit, RateLimiter rateLimiter, ObjectMapper mapper,
                         PlatformTransactionManager transactionManager, Clock clock) {
        this.properties = properties;
        this.csvParser = csvParser;
        this.jsonParser = jsonParser;
        this.rowValidator = rowValidator;
        this.rowWriter = rowWriter;
        this.jobs = jobs;
        this.errors = errors;
        this.recipes = recipes;
        this.users = users;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ preview

    public ImportDtos.PreviewResponse preview(AuthUser admin, MultipartFile file) {
        rateLimiter.check("import:" + admin.id(), 30, Duration.ofMinutes(10));
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Choose a non-empty CSV or JSON file.");
        }
        long maxBytes = properties.imports().maxBytes();
        if (file.getSize() > maxBytes) {
            throw ApiException.payloadTooLarge("The file is larger than the " + (maxBytes / 1024 / 1024) + " MB limit.");
        }
        String filename = ImportText.sanitizeFilename(file.getOriginalFilename());
        ImportFileType type = detectType(filename, file.getContentType());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The uploaded file could not be read.");
        }
        try {
            return previewContent(admin, filename, type, content);
        } catch (FileRejectedException e) {
            Long jobId = recordFailedJob(admin, filename, type, content, e);
            log.info("Import file rejected (job {}): {}", jobId, e.getCode());
            throw new ApiException(e.getStatus(), e.getCode(), e.getMessage());
        }
    }

    ImportDtos.PreviewResponse previewContent(AuthUser admin, String filename, ImportFileType type, byte[] content) {
        int maxRows = properties.imports().maxRows();
        List<ParsedRecord> records = switch (type) {
            case CSV -> csvParser.parse(sniff(ImportText.decodeUtf8(content), type), maxRows);
            case JSON -> {
                sniff(ImportText.decodeUtf8(content), type);
                yield jsonParser.parse(content, maxRows);
            }
        };

        List<ImportIssue> allIssues = new ArrayList<>();
        List<ImportDtos.RowPreview> rows = new ArrayList<>();
        List<ImportRow> validRows = new ArrayList<>();
        Map<String, Integer> firstRowByTitle = new HashMap<>();
        for (ParsedRecord record : records) {
            List<ImportIssue> issues = new ArrayList<>(record.issues());
            ImportRow row = record.row();
            if (row != null) {
                issues.addAll(rowValidator.validate(row, type));
                String key = TextNormalizer.key(row.title());
                if (key != null) {
                    Integer earlier = firstRowByTitle.putIfAbsent(key, row.rowNumber());
                    if (earlier != null) {
                        issues.add(ImportIssue.error(row.rowNumber(), row.lineNumber(), "title", "DUPLICATE_IN_FILE",
                                "Same title as row " + earlier + " in this file.", row.excerpt()));
                    } else if (recipes.existsOfficialWithTitle(key, RecipeStatus.ARCHIVED)) {
                        issues.add(ImportIssue.warning(row.rowNumber(), row.lineNumber(), "title", "DUPLICATE_TITLE",
                                "An official recipe with this title already exists; this row will be skipped.", row.excerpt()));
                    }
                }
            }
            boolean valid = row != null && issues.stream().noneMatch(ImportIssue::isError);
            if (valid) {
                validRows.add(row);
            }
            allIssues.addAll(issues);
            Integer total = row != null && row.prepTimeMins() != null && row.cookTimeMins() != null
                    ? row.prepTimeMins() + row.cookTimeMins() : null;
            rows.add(new ImportDtos.RowPreview(record.rowNumber(), record.lineNumber(), row == null ? null : row.title(),
                    valid, total, row == null ? 0 : row.ingredients().size(), row == null ? 0 : row.steps().size()));
        }

        String payload = mapper.writeValueAsString(validRows);
        int invalid = records.size() - validRows.size();
        Long jobId = tx.execute(status -> {
            BulkUploadJob job = new BulkUploadJob(users.getReferenceById(admin.id()), filename, type, clock.instant());
            job.setFileSha256(sha256(content));
            job.setFileSizeBytes((long) content.length);
            job.setStatus(ImportStatus.PREVIEWED);
            job.setTotalRows(records.size());
            job.setValidRows(validRows.size());
            job.setFailedRows(invalid);
            job.setStagedPayload(payload);
            job.setSummary(records.size() + " rows: " + validRows.size() + " valid, " + invalid + " invalid (preview).");
            jobs.save(job);
            saveIssues(job, allIssues);
            return job.getId();
        });
        return new ImportDtos.PreviewResponse(jobId, ImportStatus.PREVIEWED, type, filename, records.size(),
                validRows.size(), invalid, rows, allIssues.stream().map(ImportService::issueView).toList());
    }

    // ------------------------------------------------------------------ confirm

    public ImportDtos.JobDetail confirm(AuthUser admin, Long jobId) {
        BulkUploadJob job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Import job"));
        Integer claimed = tx.execute(s -> jobs.claimForProcessing(jobId));
        if (claimed == null || claimed == 0) {
            BulkUploadJob current = jobs.findById(jobId).orElseThrow();
            return switch (current.getStatus()) {
                case COMPLETED, COMPLETED_WITH_ERRORS, FAILED -> detail(jobId); // idempotent retry
                case PROCESSING -> throw new ApiException(HttpStatus.CONFLICT, "IMPORT_IN_PROGRESS", "This import is already being processed.");
                case EXPIRED -> throw new ApiException(HttpStatus.CONFLICT, "PREVIEW_EXPIRED", "This preview has expired. Upload the file again.");
                case PREVIEWED -> throw new ApiException(HttpStatus.CONFLICT, "IMPORT_CONFLICT", "Please retry the confirmation.");
            };
        }

        List<ImportRow> rows = mapper.readValue(job.getStagedPayload() == null ? "[]" : job.getStagedPayload(),
                new TypeReference<List<ImportRow>>() { });
        int imported = 0;
        int skipped = 0;
        int writeFailures = 0;
        List<ImportIssue> issues = new ArrayList<>();
        for (ImportRow row : rows) {
            try {
                ImportRowWriter.Result result = rowWriter.write(row, admin.id(), jobId);
                if (result.outcome() == ImportRowWriter.Outcome.SKIPPED_DUPLICATE) {
                    skipped++;
                    issues.add(ImportIssue.warning(row.rowNumber(), row.lineNumber(), "title", "SKIPPED_DUPLICATE",
                            "Skipped: an official recipe with this title already exists.", row.excerpt()));
                } else {
                    imported++;
                    if (!result.createdIngredients().isEmpty()) {
                        issues.add(ImportIssue.warning(row.rowNumber(), row.lineNumber(), "ingredients", "INGREDIENTS_CREATED",
                                "New catalogue ingredient(s) created: " + String.join(", ", result.createdIngredients()) + ".", null));
                    }
                }
            } catch (ImportRowWriter.RowFailure e) {
                writeFailures++;
                issues.add(ImportIssue.error(row.rowNumber(), row.lineNumber(), "ingredients", e.getCode(), e.getMessage(), row.excerpt()));
            } catch (DataAccessException | jakarta.persistence.PersistenceException e) {
                writeFailures++;
                log.warn("Import job {} row {} failed to save: {}", jobId, row.rowNumber(), e.getMessage());
                issues.add(ImportIssue.error(row.rowNumber(), row.lineNumber(), null, "SAVE_FAILED",
                        "The row could not be saved (it may conflict with data changed at the same time). Nothing from this row was saved.",
                        row.excerpt()));
            }
        }
        int finalImported = imported;
        int finalSkipped = skipped;
        int finalFailures = writeFailures;
        tx.executeWithoutResult(s -> {
            BulkUploadJob j = jobs.findById(jobId).orElseThrow();
            int failed = j.getFailedRows() + finalFailures;
            j.setImportedRows(finalImported);
            j.setSkippedRows(finalSkipped);
            j.setFailedRows(failed);
            j.setStatus(finalStatus(finalImported, failed, finalSkipped));
            j.setFinishedAt(clock.instant());
            j.setStagedPayload(null); // staged rows are not retained after processing
            j.setSummary(String.format(Locale.ROOT, "%d imported, %d failed, %d skipped as duplicates (of %d rows).",
                    finalImported, failed, finalSkipped, j.getTotalRows()));
            saveIssues(j, issues);
            audit.record(admin.id(), "BULK_IMPORT_COMPLETED", "IMPORT_JOB", jobId, j.getSummary());
        });
        return detail(jobId);
    }

    /** COMPLETED: every row imported. FAILED: nothing imported. Otherwise COMPLETED_WITH_ERRORS. */
    public static ImportStatus finalStatus(int imported, int failed, int skipped) {
        if (imported == 0) {
            return ImportStatus.FAILED;
        }
        return failed == 0 && skipped == 0 ? ImportStatus.COMPLETED : ImportStatus.COMPLETED_WITH_ERRORS;
    }

    // ------------------------------------------------------------------ history

    public PageResponse<ImportDtos.JobView> list(Integer page, Integer size) {
        return tx.execute(s -> PageResponse.of(jobs.findAllWithUploader(
                Pagination.of(page, size, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")))), ImportService::jobView));
    }

    public ImportDtos.JobDetail detail(Long jobId) {
        return tx.execute(s -> {
            BulkUploadJob job = jobs.findWithUploader(jobId).orElseThrow(() -> ApiException.notFound("Import job"));
            List<ImportDtos.IssueView> issueViews = errors.findByJobIdOrderByRowNumberAscIdAsc(jobId).stream()
                    .map(e -> new ImportDtos.IssueView(e.getRowNumber(), e.getLineNumber(), e.getFieldName(), e.getSeverity(),
                            e.getErrorCode(), e.getMessage(), e.getRowExcerpt()))
                    .toList();
            return new ImportDtos.JobDetail(jobView(job), issueViews);
        });
    }

    /** Previews not confirmed within the TTL expire and their staged rows are discarded. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void expireStalePreviews() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(properties.imports().previewTtlHours()));
        tx.executeWithoutResult(s -> jobs.findByStatusAndStartedAtBefore(ImportStatus.PREVIEWED, cutoff).forEach(job -> {
            job.setStatus(ImportStatus.EXPIRED);
            job.setStagedPayload(null);
            job.setFinishedAt(clock.instant());
        }));
    }

    // ------------------------------------------------------------------ helpers

    /** Extension and declared content type must agree; the content itself is sniffed later. */
    public static ImportFileType detectType(String filename, String contentType) {
        String lower = filename.toLowerCase(Locale.ROOT);
        String ct = contentType == null ? "application/octet-stream" : contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv") && CSV_TYPES.contains(ct)) {
            return ImportFileType.CSV;
        }
        if (lower.endsWith(".json") && JSON_TYPES.contains(ct)) {
            return ImportFileType.JSON;
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                "Upload a .csv or .json file (received " + filename + ", " + ct + ").");
    }

    /** The extension is not proof of format: check that the text actually looks like the declared type. */
    public static String sniff(String text, ImportFileType type) {
        String start = text.stripLeading();
        if (start.isEmpty()) {
            throw new FileRejectedException("EMPTY_FILE", "The file is empty.");
        }
        char first = start.charAt(0);
        if (type == ImportFileType.JSON && first != '[' && first != '{') {
            throw new FileRejectedException("FORMAT_MISMATCH", "The file has a .json extension but does not contain JSON.");
        }
        if (type == ImportFileType.CSV && (first == '[' || first == '{')) {
            throw new FileRejectedException("FORMAT_MISMATCH", "The file has a .csv extension but looks like JSON.");
        }
        return text;
    }

    private Long recordFailedJob(AuthUser admin, String filename, ImportFileType type, byte[] content, FileRejectedException e) {
        return tx.execute(s -> {
            BulkUploadJob job = new BulkUploadJob(users.getReferenceById(admin.id()), filename, type, clock.instant());
            job.setFileSha256(sha256(content));
            job.setFileSizeBytes((long) content.length);
            job.setStatus(ImportStatus.FAILED);
            job.setFinishedAt(clock.instant());
            job.setSummary("File rejected: " + e.getMessage());
            jobs.save(job);
            errors.save(new BulkUploadError(job, null, null, null, ImportIssueSeverity.ERROR, e.getCode(), truncate(e.getMessage(), 500), null));
            return job.getId();
        });
    }

    private void saveIssues(BulkUploadJob job, List<ImportIssue> issues) {
        errors.saveAll(issues.stream().map(i -> new BulkUploadError(job, i.rowNumber(), i.lineNumber(),
                truncate(i.field(), 80), i.severity(), i.code(), truncate(i.message(), 500), truncate(i.excerpt(), 300))).toList());
    }

    static ImportDtos.IssueView issueView(ImportIssue i) {
        return new ImportDtos.IssueView(i.rowNumber(), i.lineNumber(), i.field(), i.severity(), i.code(), i.message(), i.excerpt());
    }

    static ImportDtos.JobView jobView(BulkUploadJob j) {
        return new ImportDtos.JobView(j.getId(), j.getSourceFilename(), j.getFileType(), j.getStatus(),
                UserMapper.author(j.getUploadedBy()), j.getStartedAt(), j.getFinishedAt(), j.getTotalRows(), j.getValidRows(),
                j.getImportedRows(), j.getFailedRows(), j.getSkippedRows(), j.getSummary());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.bachelorchef.analytics;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.AdminDtos;
import com.bachelorchef.dto.ImportDtos;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.importer.ImportService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard and analytics. Date ranges are inclusive local dates in the display time zone
 * (default: the last 30 days including today) and are limited to 366 days.
 */
@Service
public class AnalyticsService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    static final int TOP_N = 5;

    private final AnalyticsRepository repo;
    private final ImportService imports;
    private final AppProperties properties;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repo, ImportService imports, AppProperties properties, Clock clock) {
        this.repo = repo;
        this.imports = imports;
        this.properties = properties;
        this.clock = clock;
    }

    /** Resolved, validated range. */
    public record Range(LocalDate from, LocalDate to, OffsetDateTime fromInstant, OffsetDateTime toInstant) {
    }

    public Range range(LocalDate from, LocalDate to) {
        ZoneId zone = properties.zone();
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(zone));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw ApiException.validation("from", "The start date must be on or before the end date.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.validation("from", "The range can cover at most " + MAX_DAYS + " days.");
        }
        return new Range(start, end,
                start.atStartOfDay(zone).toInstant().atOffset(ZoneOffset.UTC),
                end.plusDays(1).atStartOfDay(zone).toInstant().atOffset(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public AdminDtos.DashboardSummary summary(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        MapSqlParameterSource params = AnalyticsRepository.range(r.fromInstant(), r.toInstant());
        Object[] rating = repo.approvedRatingStats();
        List<ImportDtos.JobView> recentImports = imports.list(0, TOP_N).items();
        return new AdminDtos.DashboardSummary(
                r.from(), r.to(),
                repo.scalar("SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'"),
                repo.count("SELECT COUNT(*) FROM users WHERE created_at >= :from AND created_at < :to", params),
                repo.count("SELECT COUNT(DISTINCT user_id) FROM user_cooked_history WHERE cooked_on >= :from AND cooked_on < :to", params),
                repo.scalar("SELECT COUNT(*) FROM recipes WHERE status = 'APPROVED'"),
                repo.scalar("SELECT COUNT(*) FROM recipes WHERE status = 'PENDING'"),
                repo.scalar("SELECT COUNT(*) FROM reviews WHERE status = 'PENDING'"),
                repo.scalar("SELECT COUNT(*) FROM content_reports WHERE status = 'OPEN'"),
                repo.count("SELECT COUNT(*) FROM user_cooked_history WHERE cooked_on >= :from AND cooked_on < :to", params),
                (BigDecimal) rating[1],
                (Long) rating[0],
                repo.popularIngredients(r.fromInstant(), r.toInstant(), TOP_N),
                repo.topRated(r.fromInstant(), r.toInstant(), TOP_N),
                repo.mostCooked(r.fromInstant(), r.toInstant(), TOP_N),
                recentImports);
    }

    @Transactional(readOnly = true)
    public AdminDtos.IngredientAnalytics ingredients(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        return new AdminDtos.IngredientAnalytics(r.from(), r.to(), repo.popularIngredients(r.fromInstant(), r.toInstant(), 20));
    }

    @Transactional(readOnly = true)
    public AdminDtos.RecipeAnalytics recipes(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        return new AdminDtos.RecipeAnalytics(r.from(), r.to(),
                repo.topRated(r.fromInstant(), r.toInstant(), 10),
                repo.mostCooked(r.fromInstant(), r.toInstant(), 10),
                repo.dailyActivity(r.fromInstant(), r.toInstant(), properties.zone().getId(), r.from(), r.to()));
    }
}

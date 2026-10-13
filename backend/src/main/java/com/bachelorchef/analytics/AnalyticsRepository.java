package com.bachelorchef.analytics;

import com.bachelorchef.dto.AdminDtos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Aggregate queries for the admin dashboard. Range parameters are UTC instants [from, to) computed
 * from local dates in the display time zone. All inputs are bound parameters.
 */
@Repository
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    public long scalar(String sql) {
        return count(sql, new MapSqlParameterSource());
    }

    public Object[] approvedRatingStats() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS n, COALESCE(ROUND(AVG(v.rating)::numeric, 2), 0) AS avg
                FROM reviews v JOIN recipes r ON r.recipe_id = v.recipe_id
                WHERE v.status = 'APPROVED' AND r.status = 'APPROVED'
                """, new MapSqlParameterSource(), (rs, i) -> new Object[]{rs.getLong("n"), rs.getBigDecimal("avg")});
    }

    public List<AdminDtos.IngredientStat> popularIngredients(OffsetDateTime from, OffsetDateTime to, int limit) {
        return jdbc.query("""
                SELECT i.ingredient_id, i.name, COUNT(h.history_id) AS cook_count,
                       (SELECT COUNT(*) FROM recipe_ingredients x WHERE x.ingredient_id = i.ingredient_id) AS recipe_count
                FROM user_cooked_history h
                JOIN recipe_ingredients ri ON ri.recipe_id = h.recipe_id AND NOT ri.is_optional
                JOIN ingredients i ON i.ingredient_id = ri.ingredient_id
                WHERE h.cooked_on >= :from AND h.cooked_on < :to
                GROUP BY i.ingredient_id, i.name
                ORDER BY cook_count DESC, i.name ASC
                LIMIT :limit
                """, range(from, to).addValue("limit", limit),
                (rs, i) -> new AdminDtos.IngredientStat(rs.getLong("ingredient_id"), rs.getString("name"),
                        rs.getLong("cook_count"), rs.getLong("recipe_count")));
    }

    public List<AdminDtos.RecipeStat> topRated(OffsetDateTime from, OffsetDateTime to, int limit) {
        return jdbc.query("""
                SELECT r.recipe_id, r.title, r.rating_avg, r.rating_count,
                       (SELECT COUNT(*) FROM user_cooked_history h WHERE h.recipe_id = r.recipe_id
                          AND h.cooked_on >= :from AND h.cooked_on < :to) AS cook_count
                FROM recipes r
                WHERE r.status = 'APPROVED' AND r.rating_count > 0
                ORDER BY r.rating_avg DESC, r.rating_count DESC, r.title ASC, r.recipe_id ASC
                LIMIT :limit
                """, range(from, to).addValue("limit", limit), AnalyticsRepository::recipeStat);
    }

    public List<AdminDtos.RecipeStat> mostCooked(OffsetDateTime from, OffsetDateTime to, int limit) {
        return jdbc.query("""
                SELECT r.recipe_id, r.title, r.rating_avg, r.rating_count, COUNT(h.history_id) AS cook_count
                FROM user_cooked_history h JOIN recipes r ON r.recipe_id = h.recipe_id
                WHERE h.cooked_on >= :from AND h.cooked_on < :to
                GROUP BY r.recipe_id, r.title, r.rating_avg, r.rating_count
                ORDER BY cook_count DESC, r.title ASC, r.recipe_id ASC
                LIMIT :limit
                """, range(from, to).addValue("limit", limit), AnalyticsRepository::recipeStat);
    }

    /** Daily counts per local date in the given zone; days without activity are filled with zeros. */
    public List<AdminDtos.ActivityPoint> dailyActivity(OffsetDateTime from, OffsetDateTime to, String zone,
                                                       LocalDate firstDay, LocalDate lastDay) {
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (LocalDate d = firstDay; !d.isAfter(lastDay); d = d.plusDays(1)) {
            byDay.put(d, new long[4]);
        }
        String[] queries = {
                "SELECT (created_at AT TIME ZONE :tz)::date AS d, COUNT(*) AS n FROM users WHERE created_at >= :from AND created_at < :to GROUP BY 1",
                "SELECT (cooked_on AT TIME ZONE :tz)::date AS d, COUNT(*) AS n FROM user_cooked_history WHERE cooked_on >= :from AND cooked_on < :to GROUP BY 1",
                "SELECT (created_at AT TIME ZONE :tz)::date AS d, COUNT(*) AS n FROM reviews WHERE created_at >= :from AND created_at < :to GROUP BY 1",
                "SELECT (created_at AT TIME ZONE :tz)::date AS d, COUNT(*) AS n FROM recipes WHERE NOT is_official AND created_at >= :from AND created_at < :to GROUP BY 1"
        };
        for (int q = 0; q < queries.length; q++) {
            int column = q;
            jdbc.query(queries[q], range(from, to).addValue("tz", zone), rs -> {
                long[] counts = byDay.get(rs.getObject("d", LocalDate.class));
                if (counts != null) {
                    counts[column] = rs.getLong("n");
                }
            });
        }
        return byDay.entrySet().stream()
                .map(e -> new AdminDtos.ActivityPoint(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
    }

    static MapSqlParameterSource range(OffsetDateTime from, OffsetDateTime to) {
        return new MapSqlParameterSource().addValue("from", from).addValue("to", to);
    }

    private static AdminDtos.RecipeStat recipeStat(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        BigDecimal avg = rs.getBigDecimal("rating_avg");
        return new AdminDtos.RecipeStat(rs.getLong("recipe_id"), rs.getString("title"), avg,
                rs.getLong("rating_count"), rs.getLong("cook_count"));
    }
}

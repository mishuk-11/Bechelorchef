package com.bachelorchef.repository;

import com.bachelorchef.entity.Difficulty;
import com.bachelorchef.service.PantryMatcher;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * One grouped query computes, for every public recipe that uses at least one selected ingredient,
 * its required-ingredient count and how many of those the user selected. Optional ingredients are
 * excluded from both counts. All values are bound parameters.
 */
@Repository
public class PantryMatchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PantryMatchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PantryMatcher.Candidate> findCandidates(Collection<Long> ingredientIds, int maxTotalTimeMins,
                                                        Difficulty difficulty, Collection<Long> tagIds, BigDecimal maxCost) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.recipe_id, r.title, r.total_time_mins, r.rating_avg,
                       COUNT(*) FILTER (WHERE NOT ri.is_optional) AS required_count,
                       COUNT(*) FILTER (WHERE NOT ri.is_optional AND ri.ingredient_id IN (:ids)) AS matched_count
                FROM recipes r
                JOIN recipe_ingredients ri ON ri.recipe_id = r.recipe_id
                WHERE r.status = 'APPROVED'
                  AND r.total_time_mins <= :maxTime
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", ingredientIds)
                .addValue("maxTime", maxTotalTimeMins);
        if (difficulty != null) {
            sql.append(" AND r.difficulty = :difficulty");
            params.addValue("difficulty", difficulty.name());
        }
        if (maxCost != null) {
            sql.append(" AND r.estimated_cost_bdt <= :maxCost");
            params.addValue("maxCost", maxCost);
        }
        if (tagIds != null && !tagIds.isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM recipe_tags rt WHERE rt.recipe_id = r.recipe_id AND rt.tag_id IN (:tagIds))");
            params.addValue("tagIds", tagIds);
        }
        sql.append("""
                 GROUP BY r.recipe_id, r.title, r.total_time_mins, r.rating_avg
                 HAVING COUNT(*) FILTER (WHERE NOT ri.is_optional AND ri.ingredient_id IN (:ids)) > 0
                """);
        return jdbc.query(sql.toString(), params, (rs, i) -> new PantryMatcher.Candidate(
                rs.getLong("recipe_id"), rs.getString("title"), rs.getInt("total_time_mins"),
                rs.getBigDecimal("rating_avg"), rs.getInt("required_count"), rs.getInt("matched_count")));
    }
}

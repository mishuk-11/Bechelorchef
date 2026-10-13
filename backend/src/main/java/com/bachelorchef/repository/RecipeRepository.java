package com.bachelorchef.repository;

import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.RecipeStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {

    @EntityGraph(attributePaths = {"createdBy", "tags"})
    @Query("select r from Recipe r where r.id = :id")
    Optional<Recipe> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"createdBy"})
    List<Recipe> findByIdIn(Collection<Long> ids);

    long countByStatus(RecipeStatus status);

    @Query("""
            select count(r) > 0 from Recipe r
            where r.normalizedTitle = :normalizedTitle and r.official = true and r.status <> :excluded
            """)
    boolean existsOfficialWithTitle(@Param("normalizedTitle") String normalizedTitle,
                                    @Param("excluded") RecipeStatus excluded);
}

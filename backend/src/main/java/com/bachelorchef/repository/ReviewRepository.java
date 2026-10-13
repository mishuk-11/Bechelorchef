package com.bachelorchef.repository;

import com.bachelorchef.entity.Review;
import com.bachelorchef.entity.ReviewStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByRecipeIdAndUserId(Long recipeId, Long userId);

    @EntityGraph(attributePaths = "user")
    Page<Review> findByRecipeIdAndStatus(Long recipeId, ReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "recipe"})
    Page<Review> findByStatusIn(Collection<ReviewStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "recipe"})
    @Query("""
            select r from Review r where r.id in (
                select cr.targetId from ContentReport cr
                where cr.targetType = com.bachelorchef.entity.ReportTargetType.REVIEW
                  and cr.status = com.bachelorchef.entity.ReportStatus.OPEN)
            """)
    Page<Review> findFlagged(Pageable pageable);

    @EntityGraph(attributePaths = {"recipe"})
    Page<Review> findByUserId(Long userId, Pageable pageable);

    long countByStatus(ReviewStatus status);

    /** Aggregate of approved reviews only — the single source for public rating numbers. */
    @Query("""
            select new com.bachelorchef.repository.RatingAggregate(count(r), coalesce(avg(r.rating), 0.0))
            from Review r where r.recipe.id = :recipeId and r.status = com.bachelorchef.entity.ReviewStatus.APPROVED
            """)
    RatingAggregate aggregateApproved(@Param("recipeId") Long recipeId);
}

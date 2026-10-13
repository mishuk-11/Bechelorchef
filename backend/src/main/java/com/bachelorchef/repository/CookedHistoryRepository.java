package com.bachelorchef.repository;

import com.bachelorchef.entity.CookedHistory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CookedHistoryRepository extends JpaRepository<CookedHistory, Long> {

    @Query(value = "select h from CookedHistory h join fetch h.recipe where h.user.id = :userId",
            countQuery = "select count(h) from CookedHistory h where h.user.id = :userId")
    Page<CookedHistory> findForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select h from CookedHistory h join fetch h.recipe
            where h.user.id = :userId and h.cookedOn >= :from and h.cookedOn < :to
            """)
    List<CookedHistory> findForUserBetween(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    long countByUserId(Long userId);

    @Query("select count(distinct h.recipe.id) from CookedHistory h where h.user.id = :userId")
    long countDistinctRecipesForUser(@Param("userId") Long userId);
}

package com.bachelorchef.repository;

import com.bachelorchef.entity.SavedRecipe;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedRecipeRepository extends JpaRepository<SavedRecipe, SavedRecipe.Key> {

    @Query(value = "select s from SavedRecipe s join fetch s.recipe r left join fetch r.createdBy where s.user.id = :userId",
            countQuery = "select count(s) from SavedRecipe s where s.user.id = :userId")
    Page<SavedRecipe> findForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select s.recipe.id from SavedRecipe s where s.user.id = :userId and s.recipe.id in :recipeIds")
    List<Long> findSavedRecipeIds(@Param("userId") Long userId, @Param("recipeIds") Collection<Long> recipeIds);
}

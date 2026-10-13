package com.bachelorchef.repository;

import com.bachelorchef.entity.RecipeStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, Long> {

    @Query("select s from RecipeStep s where s.recipe.id = :recipeId order by s.stepNumber")
    List<RecipeStep> findForRecipe(@Param("recipeId") Long recipeId);

    @Modifying(flushAutomatically = true)
    @Query("delete from RecipeStep s where s.recipe.id = :recipeId")
    int deleteForRecipe(@Param("recipeId") Long recipeId);
}

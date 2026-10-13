package com.bachelorchef.repository;

import com.bachelorchef.entity.RecipeIngredient;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, RecipeIngredient.Key> {

    @Query("""
            select ri from RecipeIngredient ri join fetch ri.ingredient
            where ri.recipe.id = :recipeId order by ri.sortOrder, ri.ingredient.name
            """)
    List<RecipeIngredient> findForRecipe(@Param("recipeId") Long recipeId);

    @Query("""
            select ri from RecipeIngredient ri join fetch ri.ingredient
            where ri.recipe.id in :recipeIds order by ri.recipe.id, ri.sortOrder
            """)
    List<RecipeIngredient> findForRecipes(@Param("recipeIds") Collection<Long> recipeIds);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from RecipeIngredient ri where ri.recipe.id = :recipeId")
    int deleteForRecipe(@Param("recipeId") Long recipeId);
}

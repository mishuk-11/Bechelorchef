package com.bachelorchef.repository;

import com.bachelorchef.entity.Ingredient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface IngredientRepository extends JpaRepository<Ingredient, Long>, JpaSpecificationExecutor<Ingredient> {

    Optional<Ingredient> findByNormalizedName(String normalizedName);

    List<Ingredient> findByNormalizedNameIn(Collection<String> normalizedNames);

    @Query("select distinct i.category from Ingredient i where i.category is not null order by i.category")
    List<String> findCategories();
}

package com.bachelorchef.service;

import com.bachelorchef.entity.Recipe;
import com.bachelorchef.repository.RatingAggregate;
import com.bachelorchef.repository.RecipeRepository;
import com.bachelorchef.repository.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps recipes.rating_avg / rating_count equal to the aggregate of APPROVED reviews. It is
 * recomputed from scratch inside the same transaction as every review status/rating change, so
 * the denormalised values cannot drift.
 */
@Service
public class RatingAggregateService {

    private final ReviewRepository reviews;
    private final RecipeRepository recipes;

    public RatingAggregateService(ReviewRepository reviews, RecipeRepository recipes) {
        this.reviews = reviews;
        this.recipes = recipes;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recompute(Long recipeId) {
        RatingAggregate aggregate = reviews.aggregateApproved(recipeId);
        Recipe recipe = recipes.findById(recipeId).orElseThrow();
        recipe.setRatingCount(aggregate.count().intValue());
        recipe.setRatingAvg(average(aggregate.count(), aggregate.average()));
    }

    static BigDecimal average(long count, double average) {
        if (count == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
    }
}

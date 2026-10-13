package com.bachelorchef.repository;

/** Count and average of approved review ratings for one recipe. */
public record RatingAggregate(Long count, Double average) {
}

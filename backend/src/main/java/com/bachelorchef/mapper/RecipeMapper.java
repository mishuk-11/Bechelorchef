package com.bachelorchef.mapper;

import com.bachelorchef.dto.RecipeDtos;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.service.TimeCategory;

public final class RecipeMapper {

    private RecipeMapper() {
    }

    /** Official recipes are attributed to Bachelor Chef rather than to the admin account. */
    public static RecipeDtos.RecipeSummary summary(Recipe r) {
        TimeCategory category = TimeCategory.of(r.getTotalTimeMins());
        return new RecipeDtos.RecipeSummary(
                r.getId(), r.getTitle(), r.getImageUrl(),
                r.getPrepTimeMins(), r.getCookTimeMins(), r.getTotalTimeMins(),
                category, category.label(),
                r.getDifficulty(), r.getEstimatedCostBdt(), r.getBaseServings(),
                r.getRatingAvg(), r.getRatingCount(),
                r.isOfficial(), r.getStatus(),
                r.isOfficial() ? null : UserMapper.author(r.getCreatedBy()),
                r.getCreatedAt());
    }
}

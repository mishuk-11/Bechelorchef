package com.bachelorchef.service;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.Pagination;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.SavedRecipe;
import com.bachelorchef.mapper.RecipeMapper;
import com.bachelorchef.repository.SavedRecipeRepository;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.AuthUser;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personal cookbook. Only ever reads/writes rows of the authenticated user. */
@Service
public class SavedRecipeService {

    private final SavedRecipeRepository saved;
    private final UserRepository users;
    private final RecipeQueryService recipeQueries;

    public SavedRecipeService(SavedRecipeRepository saved, UserRepository users, RecipeQueryService recipeQueries) {
        this.saved = saved;
        this.users = users;
        this.recipeQueries = recipeQueries;
    }

    /** Idempotent: saving an already-saved recipe is a no-op. */
    @Transactional
    public void save(AuthUser actor, Long recipeId) {
        Recipe recipe = recipeQueries.requireViewable(recipeId, actor);
        SavedRecipe.Key key = new SavedRecipe.Key(actor.id(), recipeId);
        if (!saved.existsById(key)) {
            saved.save(new SavedRecipe(users.getReferenceById(actor.id()), recipe));
        }
    }

    @Transactional
    public void unsave(AuthUser actor, Long recipeId) {
        saved.deleteById(new SavedRecipe.Key(actor.id(), recipeId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityDtos.SavedRecipeView> list(AuthUser actor, Integer page, Integer size) {
        return PageResponse.of(saved.findForUser(actor.id(),
                        Pagination.of(page, size, Sort.by(Sort.Order.desc("savedAt"), Sort.Order.desc("recipe.id")))),
                s -> new CommunityDtos.SavedRecipeView(RecipeMapper.summary(s.getRecipe()), s.getSavedAt()));
    }
}

package com.bachelorchef.service;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.Pagination;
import com.bachelorchef.entity.CookedHistory;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.repository.CookedHistoryRepository;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.AuthUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Recipes Done". Multiple events per recipe are allowed. Every mutation refreshes goal completion
 * for the affected month(s) in the same transaction.
 */
@Service
public class CookingHistoryService {

    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);
    static final Duration MAX_BACKDATE = Duration.ofDays(366);

    private final CookedHistoryRepository history;
    private final UserRepository users;
    private final RecipeQueryService recipeQueries;
    private final GoalService goals;
    private final AppProperties properties;
    private final Clock clock;

    public CookingHistoryService(CookedHistoryRepository history, UserRepository users, RecipeQueryService recipeQueries,
                                 GoalService goals, AppProperties properties, Clock clock) {
        this.history = history;
        this.users = users;
        this.recipeQueries = recipeQueries;
        this.goals = goals;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CommunityDtos.HistoryView log(AuthUser actor, Long recipeId, CommunityDtos.CookRequest request) {
        Recipe recipe = recipeQueries.requireViewable(recipeId, actor);
        if (recipe.getStatus() == RecipeStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "RECIPE_ARCHIVED", "This recipe is no longer available.");
        }
        Instant cookedOn = validateCookedOn(request == null ? null : request.cookedOn());
        String notes = request == null ? null : TextNormalizer.trimToNull(request.notes());
        CookedHistory entry = history.save(new CookedHistory(users.getReferenceById(actor.id()), recipe, cookedOn, notes));
        goals.refreshCompletion(actor.id(), Set.of(monthOf(cookedOn)));
        return view(entry);
    }

    @Transactional(readOnly = true)
    public CommunityDtos.HistoryPage list(AuthUser actor, Integer page, Integer size) {
        PageResponse<CommunityDtos.HistoryView> result = PageResponse.of(history.findForUser(actor.id(),
                        Pagination.of(page, size, Sort.by(Sort.Order.desc("cookedOn"), Sort.Order.desc("id")))),
                CookingHistoryService::view);
        return new CommunityDtos.HistoryPage(result, history.countByUserId(actor.id()),
                history.countDistinctRecipesForUser(actor.id()));
    }

    @Transactional
    public CommunityDtos.HistoryView update(AuthUser actor, Long historyId, CommunityDtos.CookRequest request) {
        CookedHistory entry = own(actor, historyId);
        Set<YearMonth> months = new LinkedHashSet<>();
        months.add(monthOf(entry.getCookedOn()));
        if (request.cookedOn() != null) {
            entry.setCookedOn(validateCookedOn(request.cookedOn()));
        }
        if (request.notes() != null) {
            entry.setNotes(TextNormalizer.trimToNull(request.notes()));
        }
        months.add(monthOf(entry.getCookedOn()));
        history.flush();
        goals.refreshCompletion(actor.id(), months);
        return view(entry);
    }

    @Transactional
    public void delete(AuthUser actor, Long historyId) {
        CookedHistory entry = own(actor, historyId);
        YearMonth month = monthOf(entry.getCookedOn());
        history.delete(entry);
        history.flush();
        goals.refreshCompletion(actor.id(), Set.of(month));
    }

    public Instant validateCookedOn(Instant requested) {
        Instant now = clock.instant();
        if (requested == null) {
            return now;
        }
        if (requested.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw ApiException.validation("cookedOn", "Cannot be in the future.");
        }
        if (requested.isBefore(now.minus(MAX_BACKDATE))) {
            throw ApiException.validation("cookedOn", "Cannot be more than a year ago.");
        }
        return requested;
    }

    private CookedHistory own(AuthUser actor, Long historyId) {
        CookedHistory entry = history.findById(historyId).orElseThrow(() -> ApiException.notFound("History entry"));
        if (!entry.getUser().getId().equals(actor.id())) {
            throw ApiException.notFound("History entry");
        }
        return entry;
    }

    private YearMonth monthOf(Instant instant) {
        return YearMonth.from(instant.atZone(properties.zone()));
    }

    static CommunityDtos.HistoryView view(CookedHistory h) {
        Recipe r = h.getRecipe();
        return new CommunityDtos.HistoryView(h.getId(),
                new CommunityDtos.HistoryRecipe(r.getId(), r.getTitle(), r.getImageUrl(), r.getTotalTimeMins(),
                        r.getStatus() == RecipeStatus.APPROVED),
                h.getCookedOn(), h.getNotes(), h.getCreatedAt());
    }
}

package com.bachelorchef.service;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.entity.CookedHistory;
import com.bachelorchef.entity.GoalCountMode;
import com.bachelorchef.entity.Tag;
import com.bachelorchef.entity.UserGoal;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.mapper.CatalogMapper;
import com.bachelorchef.repository.CookedHistoryRepository;
import com.bachelorchef.repository.TagRepository;
import com.bachelorchef.repository.UserGoalRepository;
import com.bachelorchef.repository.UserRepository;
import com.bachelorchef.security.AuthUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly cooking goals. Progress is computed by {@link GoalProgressCalculator} from history rows;
 * {@code completed_at} is refreshed transactionally whenever history or the goal changes.
 */
@Service
public class GoalService {

    static final int MAX_MONTHS_AHEAD = 12;
    static final int MAX_GOALS_PER_MONTH = 20;

    private final UserGoalRepository goals;
    private final CookedHistoryRepository history;
    private final TagRepository tags;
    private final UserRepository users;
    private final AppProperties properties;
    private final Clock clock;

    public GoalService(UserGoalRepository goals, CookedHistoryRepository history, TagRepository tags, UserRepository users,
                       AppProperties properties, Clock clock) {
        this.goals = goals;
        this.history = history;
        this.tags = tags;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CommunityDtos.GoalView> list(AuthUser actor) {
        List<UserGoal> all = goals.findByUserIdOrderByTargetMonthDescIdDesc(actor.id());
        Map<YearMonth, List<GoalProgressCalculator.HistoryEntry>> cache = new HashMap<>();
        return all.stream().map(g -> view(g, progressOf(g, cache))).toList();
    }

    @Transactional
    public CommunityDtos.GoalView create(AuthUser actor, CommunityDtos.GoalRequest request) {
        UserGoal goal = new UserGoal(users.getReferenceById(actor.id()));
        apply(goal, request, true);
        long sameMonth = goals.findByUserIdAndTargetMonthIn(actor.id(), List.of(goal.getTargetMonth())).size();
        if (sameMonth >= MAX_GOALS_PER_MONTH) {
            throw ApiException.validation("month", "You can have at most " + MAX_GOALS_PER_MONTH + " goals per month.");
        }
        goals.save(goal);
        int progress = refresh(goal);
        return view(goal, progress);
    }

    @Transactional
    public CommunityDtos.GoalView update(AuthUser actor, Long goalId, CommunityDtos.GoalRequest request) {
        UserGoal goal = own(actor, goalId);
        apply(goal, request, false);
        int progress = refresh(goal);
        return view(goal, progress);
    }

    @Transactional
    public void delete(AuthUser actor, Long goalId) {
        goals.delete(own(actor, goalId));
    }

    /** Re-evaluates completion of the user's goals in the given months (called by history changes). */
    @Transactional
    public void refreshCompletion(Long userId, Collection<YearMonth> months) {
        List<LocalDate> firstDays = months.stream().map(m -> m.atDay(1)).toList();
        Map<YearMonth, List<GoalProgressCalculator.HistoryEntry>> cache = new HashMap<>();
        for (UserGoal goal : goals.findByUserIdAndTargetMonthIn(userId, firstDays)) {
            updateCompletion(goal, progressOf(goal, cache));
        }
    }

    private int refresh(UserGoal goal) {
        int progress = progressOf(goal, new HashMap<>());
        updateCompletion(goal, progress);
        return progress;
    }

    private void updateCompletion(UserGoal goal, int progress) {
        boolean complete = GoalProgressCalculator.isComplete(progress, goal.getTargetCount());
        if (complete && goal.getCompletedAt() == null) {
            goal.setCompletedAt(clock.instant());
        } else if (!complete && goal.getCompletedAt() != null) {
            goal.setCompletedAt(null);
        }
    }

    private int progressOf(UserGoal goal, Map<YearMonth, List<GoalProgressCalculator.HistoryEntry>> cache) {
        YearMonth month = goal.getYearMonth();
        List<GoalProgressCalculator.HistoryEntry> entries = cache.computeIfAbsent(month, m -> loadEntries(goal.getUser().getId(), m));
        return GoalProgressCalculator.progress(entries, new GoalProgressCalculator.Criteria(month, goal.getCountMode(),
                goal.getTag() == null ? null : goal.getTag().getId(), goal.getMaxTotalTimeMins()), properties.zone());
    }

    private List<GoalProgressCalculator.HistoryEntry> loadEntries(Long userId, YearMonth month) {
        Instant from = GoalProgressCalculator.monthStart(month, properties.zone());
        Instant to = GoalProgressCalculator.monthEnd(month, properties.zone());
        List<CookedHistory> rows = history.findForUserBetween(userId, from, to);
        return rows.stream().map(h -> new GoalProgressCalculator.HistoryEntry(h.getRecipe().getId(), h.getCookedOn(),
                h.getRecipe().getTotalTimeMins(),
                h.getRecipe().getTags().stream().map(Tag::getId).collect(Collectors.toSet()))).toList();
    }

    private void apply(UserGoal goal, CommunityDtos.GoalRequest r, boolean creating) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (creating || r.title() != null) {
            String title = TextNormalizer.clean(r.title());
            if (title == null || TextNormalizer.length(title) < 3 || TextNormalizer.length(title) > 120) {
                errors.put("title", "Title must be 3 to 120 characters.");
            } else {
                goal.setTitle(title);
            }
        }
        if (creating || r.targetCount() != null) {
            if (!GoalProgressCalculator.isValidTarget(r.targetCount())) {
                errors.put("targetCount", "Target must be a whole number from " + GoalProgressCalculator.TARGET_MIN
                        + " to " + GoalProgressCalculator.TARGET_MAX + ".");
            } else {
                goal.setTargetCount(r.targetCount());
            }
        }
        if (creating || r.month() != null) {
            YearMonth month = GoalProgressCalculator.parseMonth(r.month());
            YearMonth current = YearMonth.now(clock.withZone(properties.zone()));
            if (month == null) {
                errors.put("month", "Month must use the format YYYY-MM.");
            } else if (month.isBefore(current) || month.isAfter(current.plusMonths(MAX_MONTHS_AHEAD))) {
                errors.put("month", "Choose the current month or up to " + MAX_MONTHS_AHEAD + " months ahead.");
            } else {
                goal.setTargetMonth(month);
            }
        }
        if (r.countMode() != null || creating) {
            goal.setCountMode(r.countMode() == null ? GoalCountMode.UNIQUE_RECIPES : r.countMode());
        }
        if (r.tagId() != null) {
            if (r.tagId() <= 0) {
                goal.setTag(null);
            } else {
                tags.findById(r.tagId()).ifPresentOrElse(goal::setTag, () -> errors.put("tagId", "Unknown tag."));
            }
        }
        if (r.maxTotalTimeMins() != null) {
            if (r.maxTotalTimeMins() == 0) {
                goal.setMaxTotalTimeMins(null);
            } else if (r.maxTotalTimeMins() < 1 || r.maxTotalTimeMins() > TimeCategory.TOTAL_MAX) {
                errors.put("maxTotalTimeMins", "Must be between 1 and " + TimeCategory.TOTAL_MAX + " minutes.");
            } else {
                goal.setMaxTotalTimeMins(r.maxTotalTimeMins());
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    private UserGoal own(AuthUser actor, Long goalId) {
        UserGoal goal = goals.findById(goalId).orElseThrow(() -> ApiException.notFound("Goal"));
        if (!goal.getUser().getId().equals(actor.id())) {
            throw ApiException.notFound("Goal");
        }
        return goal;
    }

    private static CommunityDtos.GoalView view(UserGoal g, int progress) {
        int target = g.getTargetCount();
        int percent = (int) Math.min(100, Math.round(progress * 100.0 / target));
        return new CommunityDtos.GoalView(g.getId(), g.getTitle(), target, g.getYearMonth().toString(), g.getCountMode(),
                CatalogMapper.tag(g.getTag()), g.getMaxTotalTimeMins(), progress, percent,
                GoalProgressCalculator.isComplete(progress, target), g.getCompletedAt(), g.getCreatedAt());
    }
}

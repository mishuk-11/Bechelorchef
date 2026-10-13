package com.bachelorchef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "user_goals")
public class UserGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "target_count", nullable = false)
    private Integer targetCount;

    /** First day of the target month. */
    @Column(name = "target_month", nullable = false)
    private LocalDate targetMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "count_mode", nullable = false, length = 20)
    private GoalCountMode countMode = GoalCountMode.UNIQUE_RECIPES;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    @Column(name = "max_total_time_mins")
    private Integer maxTotalTimeMins;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserGoal() {
    }

    public UserGoal(User user) {
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public YearMonth getYearMonth() {
        return YearMonth.from(targetMonth);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }
    public LocalDate getTargetMonth() { return targetMonth; }
    public void setTargetMonth(YearMonth month) { this.targetMonth = month.atDay(1); }
    public GoalCountMode getCountMode() { return countMode; }
    public void setCountMode(GoalCountMode countMode) { this.countMode = countMode; }
    public Tag getTag() { return tag; }
    public void setTag(Tag tag) { this.tag = tag; }
    public Integer getMaxTotalTimeMins() { return maxTotalTimeMins; }
    public void setMaxTotalTimeMins(Integer maxTotalTimeMins) { this.maxTotalTimeMins = maxTotalTimeMins; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

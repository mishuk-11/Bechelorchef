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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Recipe aggregate root. Ingredient lines and steps are separate entities managed by
 * {@code RecipeService} so they can be replaced atomically inside one transaction.
 */
@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_id")
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "normalized_title", nullable = false, length = 150)
    private String normalizedTitle;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "prep_time_mins", nullable = false)
    private Integer prepTimeMins;

    @Column(name = "cook_time_mins", nullable = false)
    private Integer cookTimeMins;

    /** Always prep + cook; set only through {@link #setTimes(int, int)}. */
    @Column(name = "total_time_mins", nullable = false)
    private Integer totalTimeMins;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "estimated_cost_bdt", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedCostBdt;

    @Column(name = "base_servings", nullable = false)
    private Integer baseServings;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_asset_id", length = 255)
    private String imageAssetId;

    @Column(name = "is_official", nullable = false)
    private boolean official;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecipeStatus status = RecipeStatus.PENDING;

    @Column(name = "moderation_note", length = 1000)
    private String moderationNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "import_job_id")
    private Long importJobId;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "recipe_tags",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void setTimes(int prep, int cook) {
        this.prepTimeMins = prep;
        this.cookTimeMins = cook;
        this.totalTimeMins = prep + cook;
    }

    public boolean isPublic() {
        return status == RecipeStatus.APPROVED;
    }

    public boolean isOwnedBy(Long userId) {
        return createdBy != null && userId != null && userId.equals(createdBy.getId());
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNormalizedTitle() { return normalizedTitle; }
    public void setNormalizedTitle(String normalizedTitle) { this.normalizedTitle = normalizedTitle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getPrepTimeMins() { return prepTimeMins; }
    public Integer getCookTimeMins() { return cookTimeMins; }
    public Integer getTotalTimeMins() { return totalTimeMins; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public BigDecimal getEstimatedCostBdt() { return estimatedCostBdt; }
    public void setEstimatedCostBdt(BigDecimal estimatedCostBdt) { this.estimatedCostBdt = estimatedCostBdt; }
    public Integer getBaseServings() { return baseServings; }
    public void setBaseServings(Integer baseServings) { this.baseServings = baseServings; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImageAssetId() { return imageAssetId; }
    public void setImageAssetId(String imageAssetId) { this.imageAssetId = imageAssetId; }
    public boolean isOfficial() { return official; }
    public void setOfficial(boolean official) { this.official = official; }
    public boolean isSample() { return sample; }
    public RecipeStatus getStatus() { return status; }
    public void setStatus(RecipeStatus status) { this.status = status; }
    public String getModerationNote() { return moderationNote; }
    public void setModerationNote(String moderationNote) { this.moderationNote = moderationNote; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public User getApprovedBy() { return approvedBy; }
    public void setApprovedBy(User approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Long getImportJobId() { return importJobId; }
    public void setImportJobId(Long importJobId) { this.importJobId = importJobId; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public void setRatingAvg(BigDecimal ratingAvg) { this.ratingAvg = ratingAvg; }
    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
    public Set<Tag> getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.bachelorchef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "saved_recipes")
public class SavedRecipe {

    @EmbeddedId
    private Key id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("recipeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;

    protected SavedRecipe() {
    }

    public SavedRecipe(User user, Recipe recipe) {
        this.id = new Key(user.getId(), recipe.getId());
        this.user = user;
        this.recipe = recipe;
    }

    @PrePersist
    void onCreate() {
        savedAt = Instant.now();
    }

    public Key getId() { return id; }
    public User getUser() { return user; }
    public Recipe getRecipe() { return recipe; }
    public Instant getSavedAt() { return savedAt; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "user_id")
        private Long userId;
        @Column(name = "recipe_id")
        private Long recipeId;

        protected Key() {
        }

        public Key(Long userId, Long recipeId) {
            this.userId = userId;
            this.recipeId = recipeId;
        }

        public Long getUserId() { return userId; }
        public Long getRecipeId() { return recipeId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(recipeId, k.recipeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, recipeId);
        }
    }
}

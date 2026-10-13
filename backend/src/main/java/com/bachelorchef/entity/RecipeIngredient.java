package com.bachelorchef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredient {

    @EmbeddedId
    private Key id;

    @MapsId("recipeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @MapsId("ingredientId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "is_optional", nullable = false)
    private boolean optional;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected RecipeIngredient() {
    }

    public RecipeIngredient(Recipe recipe, Ingredient ingredient, BigDecimal quantity, String unit,
                            boolean optional, int sortOrder) {
        this.id = new Key(recipe.getId(), ingredient.getId());
        this.recipe = recipe;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit;
        this.optional = optional;
        this.sortOrder = sortOrder;
    }

    public Key getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public Ingredient getIngredient() { return ingredient; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public boolean isOptional() { return optional; }
    public int getSortOrder() { return sortOrder; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "recipe_id")
        private Long recipeId;
        @Column(name = "ingredient_id")
        private Long ingredientId;

        protected Key() {
        }

        public Key(Long recipeId, Long ingredientId) {
            this.recipeId = recipeId;
            this.ingredientId = ingredientId;
        }

        public Long getRecipeId() { return recipeId; }
        public Long getIngredientId() { return ingredientId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(recipeId, k.recipeId) && Objects.equals(ingredientId, k.ingredientId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recipeId, ingredientId);
        }
    }
}

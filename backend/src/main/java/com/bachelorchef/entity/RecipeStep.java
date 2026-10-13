package com.bachelorchef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipe_steps")
public class RecipeStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "step_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "instruction_text", nullable = false, columnDefinition = "text")
    private String instructionText;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_asset_id", length = 255)
    private String imageAssetId;

    protected RecipeStep() {
    }

    public RecipeStep(Recipe recipe, int stepNumber, String instructionText, String imageUrl, String imageAssetId) {
        this.recipe = recipe;
        this.stepNumber = stepNumber;
        this.instructionText = instructionText;
        this.imageUrl = imageUrl;
        this.imageAssetId = imageAssetId;
    }

    public Long getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public int getStepNumber() { return stepNumber; }
    public String getInstructionText() { return instructionText; }
    public String getImageUrl() { return imageUrl; }
    public String getImageAssetId() { return imageAssetId; }
}

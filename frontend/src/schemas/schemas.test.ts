import { describe, expect, it } from "vitest";
import { goalSchema, recipeSchema, registerSchema, reviewSchema } from "./index";

const recipe = {
  title: "Egg Fried Rice",
  description: "",
  prepTimeMins: 5,
  cookTimeMins: 10,
  difficulty: "EASY" as const,
  estimatedCostBdt: 80,
  baseServings: 2,
  imageUrl: "",
  imageAssetId: null,
  tags: ["Quick"],
  ingredients: [{ ingredientId: 1, name: "Egg", quantity: 2, unit: "pcs", optional: false }],
  steps: [{ instruction: "Cook.", imageUrl: "", imageAssetId: null }],
};

describe("recipeSchema", () => {
  it("accepts a valid recipe", () => {
    expect(recipeSchema.safeParse(recipe).success).toBe(true);
  });
  it.each([
    [0, 0, false], [0, 1, true], [0, 60, true], [0, 61, false], [1, 60, false], [30, 30, true], [61, 1, false],
  ])("prep %i + cook %i valid=%s", (prep, cook, valid) => {
    expect(recipeSchema.safeParse({ ...recipe, prepTimeMins: prep, cookTimeMins: cook }).success).toBe(valid);
  });
  it.each([[0, false], [1, true], [20, true], [21, false]])("servings %i valid=%s", (servings, valid) => {
    expect(recipeSchema.safeParse({ ...recipe, baseServings: servings }).success).toBe(valid);
  });
  it("requires one ingredient, one step and one non-optional ingredient", () => {
    expect(recipeSchema.safeParse({ ...recipe, ingredients: [] }).success).toBe(false);
    expect(recipeSchema.safeParse({ ...recipe, steps: [] }).success).toBe(false);
    expect(recipeSchema.safeParse({ ...recipe, ingredients: [{ ...recipe.ingredients[0], optional: true }] }).success).toBe(false);
  });
  it("rejects more than 2 decimals in cost and non-https images", () => {
    expect(recipeSchema.safeParse({ ...recipe, estimatedCostBdt: 1.005 }).success).toBe(false);
    expect(recipeSchema.safeParse({ ...recipe, estimatedCostBdt: 80.1 }).success).toBe(true);
    expect(recipeSchema.safeParse({ ...recipe, imageUrl: "http://x.test/a.png" }).success).toBe(false);
  });
});

describe("reviewSchema rating BVA", () => {
  it.each([[0, false], [1, true], [3, true], [5, true], [6, false], [3.5, false]])("rating %s valid=%s", (rating, valid) => {
    expect(reviewSchema.safeParse({ rating }).success).toBe(valid);
  });
});

describe("goalSchema target BVA", () => {
  const goal = { title: "Cook more", targetCount: 5, month: "2026-10", countMode: "UNIQUE_RECIPES" as const };
  it.each([[0, false], [1, true], [30, true], [31, false]])("target %i valid=%s", (targetCount, valid) => {
    expect(goalSchema.safeParse({ ...goal, targetCount }).success).toBe(valid);
  });
  it.each(["2026-13", "2026-1", "26-10"])("rejects month %s", (month) => {
    expect(goalSchema.safeParse({ ...goal, month }).success).toBe(false);
  });
});

describe("registerSchema", () => {
  it("requires matching passwords of 8+ characters", () => {
    const base = { fullName: "Nadia", email: "n@example.com", password: "Password1", confirmPassword: "Password1" };
    expect(registerSchema.safeParse(base).success).toBe(true);
    expect(registerSchema.safeParse({ ...base, confirmPassword: "nope" }).success).toBe(false);
    expect(registerSchema.safeParse({ ...base, password: "short", confirmPassword: "short" }).success).toBe(false);
  });
});

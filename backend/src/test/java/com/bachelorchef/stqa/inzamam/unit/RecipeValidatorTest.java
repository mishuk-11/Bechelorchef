package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bachelorchef.service.MediaUrlPolicy;
import com.bachelorchef.service.RecipeDraft;
import com.bachelorchef.service.RecipeValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** BVA / equivalence-class tests for the shared recipe rules (API + importer). */
class RecipeValidatorTest {

    private RecipeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RecipeValidator(new MediaUrlPolicy(List.of("res.cloudinary.com")));
    }

    static RecipeDraft draft(Integer prep, Integer cook) {
        return new RecipeDraft("Egg Fried Rice", "desc", prep, cook, "EASY", new BigDecimal("80.00"), 2, null,
                List.of(new RecipeDraft.IngredientLine("1", new BigDecimal("2"), "pcs", false)),
                List.of(new RecipeDraft.StepLine("Cook it.", null)), List.of("Quick"));
    }

    static RecipeDraft with(RecipeDraft d, String title, BigDecimal cost, Integer servings) {
        return new RecipeDraft(title, d.description(), d.prepTimeMins(), d.cookTimeMins(), d.difficulty(), cost, servings,
                d.imageUrl(), d.ingredients(), d.steps(), d.tags());
    }

    @Test
    @DisplayName("A complete, typical recipe is valid")
    void validRecipe() {
        assertThat(validator.validate(draft(5, 10))).isEmpty();
    }

    @Nested
    @DisplayName("cook_time_mins field boundaries (prep = 0)")
    class CookTime {
        @ParameterizedTest(name = "cook={0} -> valid={1}")
        @CsvSource({"0,false", "1,true", "15,true", "20,true", "21,true", "60,true", "61,false"})
        void cookBoundaries(int cook, boolean valid) {
            Map<String, String> errors = validator.validate(draft(0, cook));
            assertThat(errors.containsKey("cookTimeMins")).isEqualTo(!valid);
        }
    }

    @Nested
    @DisplayName("prep_time_mins field boundaries (cook = 1)")
    class PrepTime {
        @ParameterizedTest(name = "prep={0} -> valid={1}")
        @CsvSource({"-1,false", "0,true", "1,true", "59,true", "60,false", "61,false"})
        void prepBoundaries(int prep, boolean fieldValid) {
            Map<String, String> errors = validator.validate(draft(prep, 1));
            // prep=60 is a valid field value, but 60 + 1 = 61 exceeds the calculated total ceiling
            if (prep == 60) {
                assertThat(errors).doesNotContainKey("prepTimeMins").containsKey("totalTimeMins");
            } else {
                assertThat(errors.containsKey("prepTimeMins")).isEqualTo(!fieldValid);
            }
        }
    }

    @Nested
    @DisplayName("Calculated total time boundaries")
    class TotalTime {
        @ParameterizedTest(name = "prep={0}, cook={1} (total {2}) -> valid={3}")
        @CsvSource({"5,10,15,true", "6,10,16,true", "10,10,20,true", "1,20,21,true", "30,30,60,true", "1,60,61,false", "30,31,61,false"})
        void totalBoundaries(int prep, int cook, int total, boolean valid) {
            assertThat(prep + cook).isEqualTo(total);
            assertThat(validator.validate(draft(prep, cook)).containsKey("totalTimeMins")).isEqualTo(!valid);
        }

        @Test
        @DisplayName("cook=60 with non-zero prep is rejected on the total, not on the field")
        void cookSixtyWithPrep() {
            Map<String, String> errors = validator.validate(draft(5, 60));
            assertThat(errors).containsKey("totalTimeMins").doesNotContainKeys("cookTimeMins", "prepTimeMins");
        }

        @Test
        @DisplayName("Invalid field values do not also produce a total-time error")
        void noDoubleReport() {
            assertThat(validator.validate(draft(0, 61))).containsKey("cookTimeMins").doesNotContainKey("totalTimeMins");
        }

        @Test
        void missingTimes() {
            assertThat(validator.validate(draft(null, null))).containsKeys("prepTimeMins", "cookTimeMins");
        }
    }

    @Nested
    class Title {
        @ParameterizedTest
        @ValueSource(ints = {2, 151})
        void outOfRange(int length) {
            assertThat(validator.validate(with(draft(5, 5), "x".repeat(length), new BigDecimal("1"), 1))).containsKey("title");
        }

        @ParameterizedTest
        @ValueSource(ints = {3, 150})
        void boundaryAccepted(int length) {
            assertThat(validator.validate(with(draft(5, 5), "x".repeat(length), new BigDecimal("1"), 1))).doesNotContainKey("title");
        }

        @ParameterizedTest(name = "{0} emoji -> valid={1}")
        @CsvSource({"2,false", "3,true", "80,true", "150,true", "151,false"})
        @DisplayName("DEF-021 regression: length is counted in characters (code points), like PostgreSQL")
        void emojiTitles(int count, boolean valid) {
            String title = "🍳".repeat(count); // one character, two UTF-16 units
            assertThat(validator.validate(with(draft(5, 5), title, BigDecimal.ONE, 1)).containsKey("title")).isEqualTo(!valid);
        }

        @Test
        void blankAndTrimmed() {
            assertThat(validator.validate(with(draft(5, 5), "   ", BigDecimal.ONE, 1))).containsEntry("title", "Title is required.");
            assertThat(validator.validate(with(draft(5, 5), "  ab  ", BigDecimal.ONE, 1))).containsKey("title");
        }
    }

    @Nested
    @DisplayName("Base servings 0/1/20/21")
    class Servings {
        @ParameterizedTest(name = "servings={0} -> valid={1}")
        @CsvSource({"0,false", "1,true", "20,true", "21,false"})
        void servings(int servings, boolean valid) {
            assertThat(validator.validate(with(draft(5, 5), "Valid title", BigDecimal.TEN, servings)).containsKey("baseServings"))
                    .isEqualTo(!valid);
        }
    }

    @Nested
    class Cost {
        @ParameterizedTest(name = "cost={0} -> valid={1}")
        @CsvSource({"-0.01,false", "0,true", "0.01,true", "80.50,true", "100000.00,true", "100000.01,false", "1.005,false"})
        void cost(String cost, boolean valid) {
            assertThat(validator.validate(with(draft(5, 5), "Valid title", new BigDecimal(cost), 2)).containsKey("estimatedCostBdt"))
                    .isEqualTo(!valid);
        }

        @Test
        void required() {
            assertThat(validator.validate(with(draft(5, 5), "Valid title", null, 2))).containsKey("estimatedCostBdt");
        }
    }

    @Nested
    class IngredientsAndSteps {
        @Test
        void atLeastOneIngredientAndStep() {
            RecipeDraft d = draft(5, 5);
            RecipeDraft empty = new RecipeDraft(d.title(), null, 5, 5, "EASY", BigDecimal.ONE, 1, null, List.of(), List.of(), null);
            assertThat(validator.validate(empty)).containsKeys("ingredients", "steps");
        }

        @Test
        void quantityMustBePositiveAndUnitRequired() {
            RecipeDraft d = draft(5, 5);
            RecipeDraft bad = new RecipeDraft(d.title(), null, 5, 5, "EASY", BigDecimal.ONE, 1, null,
                    List.of(new RecipeDraft.IngredientLine("1", BigDecimal.ZERO, " ", false)), d.steps(), null);
            assertThat(validator.validate(bad)).containsKeys("ingredients[0].quantity", "ingredients[0].unit");
        }

        @Test
        void duplicateIngredient() {
            RecipeDraft d = draft(5, 5);
            RecipeDraft dup = new RecipeDraft(d.title(), null, 5, 5, "EASY", BigDecimal.ONE, 1, null,
                    List.of(new RecipeDraft.IngredientLine("1", BigDecimal.ONE, "pcs", false),
                            new RecipeDraft.IngredientLine("1", BigDecimal.ONE, "pcs", false)), d.steps(), null);
            assertThat(validator.validate(dup)).containsKey("ingredients[1].ingredient");
        }

        @Test
        @DisplayName("All-optional ingredient list is rejected (match % would divide by zero)")
        void allOptional() {
            RecipeDraft d = draft(5, 5);
            RecipeDraft opt = new RecipeDraft(d.title(), null, 5, 5, "EASY", BigDecimal.ONE, 1, null,
                    List.of(new RecipeDraft.IngredientLine("1", BigDecimal.ONE, "pcs", true)), d.steps(), null);
            assertThat(validator.validate(opt)).containsKey("ingredients");
        }

        @Test
        void emptyStepText() {
            RecipeDraft d = draft(5, 5);
            List<RecipeDraft.StepLine> steps = new ArrayList<>(d.steps());
            steps.add(new RecipeDraft.StepLine("   ", null));
            RecipeDraft bad = new RecipeDraft(d.title(), null, 5, 5, "EASY", BigDecimal.ONE, 1, null, d.ingredients(), steps, null);
            assertThat(validator.validate(bad)).containsKey("steps[1].instruction");
        }

        @Test
        void tooManyTags() {
            RecipeDraft d = draft(5, 5);
            RecipeDraft bad = new RecipeDraft(d.title(), null, 5, 5, "EASY", BigDecimal.ONE, 1, null, d.ingredients(), d.steps(),
                    Collections.nCopies(11, "Quick"));
            assertThat(validator.validate(bad)).containsKey("tags");
        }
    }

    @Nested
    class DifficultyAndUrls {
        @ParameterizedTest
        @ValueSource(strings = {"EASY", "medium", "Hard"})
        void acceptedDifficulty(String value) {
            RecipeDraft d = draft(5, 5);
            RecipeDraft r = new RecipeDraft(d.title(), null, 5, 5, value, BigDecimal.ONE, 1, null, d.ingredients(), d.steps(), null);
            assertThat(validator.validate(r)).doesNotContainKey("difficulty");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "EXTREME", "1"})
        void rejectedDifficulty(String value) {
            RecipeDraft d = draft(5, 5);
            RecipeDraft r = new RecipeDraft(d.title(), null, 5, 5, value, BigDecimal.ONE, 1, null, d.ingredients(), d.steps(), null);
            assertThat(validator.validate(r)).containsKey("difficulty");
        }

        @ParameterizedTest
        @ValueSource(strings = {"http://res.cloudinary.com/a.jpg", "javascript:alert(1)", "file:///etc/passwd",
                "https://evil.example.org/x.png", "https://user:pw@res.cloudinary.com/a.jpg"})
        void unsafeImageUrls(String url) {
            RecipeDraft d = draft(5, 5);
            RecipeDraft r = new RecipeDraft(d.title(), null, 5, 5, "EASY", BigDecimal.ONE, 1, url, d.ingredients(), d.steps(), null);
            assertThat(validator.validate(r)).containsKey("imageUrl");
        }

        @Test
        void allowedImageUrl() {
            RecipeDraft d = draft(5, 5);
            RecipeDraft r = new RecipeDraft(d.title(), null, 5, 5, "EASY", BigDecimal.ONE, 1,
                    "https://res.cloudinary.com/demo/image/upload/a.jpg", d.ingredients(), d.steps(), null);
            assertThat(validator.validate(r)).doesNotContainKey("imageUrl");
        }
    }
}

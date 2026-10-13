package com.bachelorchef.e2e.inzamam;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** A new student registers, finds a meal from their pantry, saves and cooks it, and sets a goal. */
class UserJourneyTest extends UiTestBase {

    @Test
    @DisplayName("SEL-01 Register → pantry match → save → I cooked this → cookbook, history and goal progress")
    void studentJourney() {
        String email = "e2e-" + System.currentTimeMillis() + "@example.test";
        open("/register");
        type(By.id("fullName"), "E2E Student");
        type(By.id("email"), email);
        type(By.id("password"), "E2ePassword123");
        type(By.id("confirmPassword"), "E2ePassword123");
        click(testId("register-submit"));
        waitForUrlContaining("/match");
        visible(testId("logout"));

        // Pantry matcher: select egg + onion in any order
        for (String ingredient : new String[]{"onion", "egg"}) {
            type(By.id("pantry-search"), ingredient);
            By option = By.xpath("//li[@role='option'][span[translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + ingredient + "']]");
            click(option);
        }
        click(testId("run-match"));
        WebElement first = visible(testId("match-result"));
        assertTrue(first.getText().contains("% match"));
        first.findElement(By.tagName("a")).click();

        String title = visible(testId("recipe-title")).getText();
        assertFalse(title.isBlank());

        // Portion scaling is computed by the API
        String baseCost = visible(testId("total-cost")).getText();
        click(By.cssSelector("button[aria-label='More servings']"));
        wait.until(ExpectedConditions.not(ExpectedConditions.textToBe(testId("total-cost"), baseCost)));

        click(testId("save-recipe"));
        waitForText(testId("save-recipe"), "Saved");
        click(testId("cooked-this"));
        click(testId("confirm-cook"));
        visible(testId("toast-success"));

        open("/cookbook");
        waitForText(By.tagName("main"), title);

        open("/cooking-history");
        waitForText(testId("history-list"), title);

        open("/goals");
        type(By.id("goal-title"), "Cook one thing this month");
        WebElement target = visible(By.id("goal-target"));
        target.sendKeys(Keys.chord(Keys.CONTROL, "a"), "1");
        click(testId("create-goal"));
        waitForText(testId("goal-list"), "Cook one thing this month");
        waitForText(testId("goal-progress"), "1 / 1");
        assertTrue(driver.findElement(testId("goal-list")).getText().contains("Complete"));

        click(testId("logout"));
        visible(By.linkText("Log in"));
    }

    @Test
    @DisplayName("SEL-02 Anonymous visitors are sent to login from protected pages; non-admins cannot open the admin portal")
    void routeGuards() {
        open("/cookbook");
        waitForUrlContaining("/login?next=%2Fcookbook");

        String email = "e2e-guard-" + System.currentTimeMillis() + "@example.test";
        open("/register");
        type(By.id("fullName"), "E2E Guard");
        type(By.id("email"), email);
        type(By.id("password"), "E2ePassword123");
        type(By.id("confirmPassword"), "E2ePassword123");
        click(testId("register-submit"));
        visible(testId("logout"));
        open("/admin");
        waitForText(By.tagName("main"), "Administrators only");
    }

    @Test
    @DisplayName("SEL-03 Unknown recipe shows a real not-found state")
    void recipeNotFound() {
        open("/recipes/999999999");
        waitForText(By.tagName("main"), "Recipe not found");
    }
}

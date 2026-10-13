package com.bachelorchef.e2e.inzamam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

/**
 * UI checks for the three controllers under test (AdminManagement, Profile, Personal).
 * Each test drives the real web app and expects the documented behaviour; a failure is a defect
 * (see docs/stqa/Inzamam/BachelorChef_DefectReport.xlsx).
 * Needs E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD and E2E_USER_EMAIL/E2E_USER_PASSWORD.
 */
class SeededDefectUiTest extends UiTestBase {

    private static final By USER_ROLE_CELLS = By.cssSelector("[aria-label='Users'] tbody tr td:nth-child(3)");
    private static final By INGREDIENT_CATEGORY_CELLS = By.cssSelector("[aria-label='Ingredients'] tbody tr td:nth-child(2)");

    /** Profile form is filled from the signed-in user and the "My recipes" list has loaded. */
    private void waitForProfile() {
        wait.until(d -> !d.findElement(By.id("p-name")).getDomProperty("value").isBlank());
        wait.until(d -> d.findElement(By.tagName("main")).getText().matches("(?s).*(No submissions yet|Showing).*")
                || !d.findElements(By.cssSelector("main a[href^='/recipes/']")).isEmpty());
    }

    /** Goals page has loaded its list (or the empty state), so typing is not reset by a re-render. */
    private void waitForGoals() {
        wait.until(d -> !d.findElements(testId("goal-list")).isEmpty()
                || d.findElement(By.tagName("main")).getText().contains("No goals yet"));
    }

    private static boolean allEqual(WebDriver d, By cells, String expected) {
        List<WebElement> found = d.findElements(cells);
        return !found.isEmpty() && found.stream().allMatch(c -> c.getText().trim().equals(expected));
    }

    @Test
    @DisplayName("SEL-12 Admin > Users: role filter 'Admin' lists only ADMIN accounts")
    void adminRoleFilter() {
        String[] admin = adminCredentials();
        login(admin[0], admin[1]);
        open("/admin/users");
        visible(USER_ROLE_CELLS);
        new Select(visible(By.id("user-role"))).selectByValue("ADMIN");
        assertTrue(eventually(d -> allEqual(d, USER_ROLE_CELLS, "ADMIN"), 10),
                "Role filter 'Admin' still lists non-admin rows: " + driver.findElements(USER_ROLE_CELLS).stream().map(WebElement::getText).distinct().toList());
    }

    @Test
    @DisplayName("SEL-13 Admin > Ingredients: category filter 'Spices' lists only Spices")
    void ingredientCategoryFilter() {
        String[] admin = adminCredentials();
        login(admin[0], admin[1]);
        open("/admin/ingredients");
        visible(INGREDIENT_CATEGORY_CELLS);
        Select filter = new Select(visible(By.id("ing-filter")));
        eventually(d -> filter.getOptions().stream().anyMatch(o -> o.getText().equals("Spices")), 10);
        filter.selectByVisibleText("Spices");
        assertTrue(eventually(d -> allEqual(d, INGREDIENT_CATEGORY_CELLS, "Spices"), 10),
                "Category filter 'Spices' still lists: " + driver.findElements(INGREDIENT_CATEGORY_CELLS).stream().map(WebElement::getText).distinct().toList());
    }

    @Test
    @DisplayName("SEL-14 Profile: a saved bio is still there after reloading the page")
    void bioIsSaved() {
        String[] user = userCredentials();
        login(user[0], user[1]);
        open("/profile");
        waitForProfile();
        String bio = "Selenium bio " + System.currentTimeMillis();
        type(By.id("p-bio"), bio);
        click(By.xpath("//button[normalize-space()='Save profile']"));
        visible(testId("toast-success"));
        driver.navigate().refresh();
        waitForProfile();
        String after = visible(By.id("p-bio")).getDomProperty("value");
        assertEquals(bio, after, "Bio was not saved");
    }

    @Test
    @DisplayName("SEL-15 Goals: a goal with the maximum target 30 can be created")
    void goalTargetThirty() {
        String[] user = userCredentials();
        login(user[0], user[1]);
        open("/goals");
        waitForGoals();
        String title = "Thirty dinners " + System.currentTimeMillis();
        type(By.id("goal-title"), title);
        visible(By.id("goal-target")).sendKeys(Keys.chord(Keys.CONTROL, "a"), "30");
        click(testId("create-goal"));
        assertTrue(eventually(d -> !d.findElements(testId("goal-list")).isEmpty()
                        && d.findElement(testId("goal-list")).getText().contains(title), 10),
                "Goal with target 30 was not created. Page says: " + driver.findElement(By.tagName("main")).getText());
    }

    @Test
    @DisplayName("SEL-16 Goals: 'Only recipes up to 15 minutes' is kept on the new goal")
    void goalTimeLimitKept() {
        String[] user = userCredentials();
        login(user[0], user[1]);
        open("/goals");
        waitForGoals();
        String title = "Quick dinners " + System.currentTimeMillis();
        type(By.id("goal-title"), title);
        visible(By.id("goal-target")).sendKeys(Keys.chord(Keys.CONTROL, "a"), "3");
        type(By.id("goal-time"), "15");
        click(testId("create-goal"));
        waitForText(testId("goal-list"), title);
        WebElement card = driver.findElement(By.xpath("//*[@data-testid='goal-list']/li[contains(., '" + title + "')]"));
        assertTrue(card.getText().contains("≤ 15 min"), "Time limit missing on the goal card: " + card.getText());
    }
}

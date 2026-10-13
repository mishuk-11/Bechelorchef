package com.bachelorchef.e2e.inzamam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** Admin bulk CSV import through the real UI: upload → preview → confirm → recipe visible in admin list. */
class AdminCsvImportTest extends UiTestBase {

    private Path csv;
    private String suffix;

    @BeforeEach
    void writeFixture() throws Exception {
        suffix = String.valueOf(System.currentTimeMillis());
        csv = Files.createTempFile("e2e-bulk-recipes-", ".csv");
        Files.writeString(csv, String.join("\n",
                "title,prep_time_mins,cook_time_mins,difficulty_level,estimated_cost_bdt,ingredients_csv,instructions_pipe,tags_pipe",
                "E2E Egg Toast " + suffix + ",3,5,EASY,40.00,Bread:2:slice|Egg:1:pcs,Toast the bread|Fry the egg|Assemble,Breakfast",
                "\"E2E Chili, Garlic Noodles " + suffix + "\",3,10,EASY,60.00,Noodles:1:pack|Garlic:2:clove|Green Chili:1:pcs,Boil noodles|Fry garlic and chili|Toss,Noodles",
                "E2E Masala Oats " + suffix + ",2,8,EASY,45.00,Oats:1:cup|Water:2:cup,Boil water|Add oats,Breakfast",
                "E2E Too Slow Stew " + suffix + ",10,60,EASY,90.00,Potato:2:pcs,Boil|Simmer,Dinner") + "\n", StandardCharsets.UTF_8);
    }

    @AfterEach
    void deleteFixture() throws Exception {
        Files.deleteIfExists(csv);
    }

    @Test
    @DisplayName("SEL-04 Admin uploads a CSV, sees row-level validation, imports valid rows and finds them in the recipe list")
    void importCsvThroughUi() {
        String[] admin = adminCredentials();
        login(admin[0], admin[1]);

        open("/admin/imports");
        present(testId("import-file")).sendKeys(csv.toAbsolutePath().toString());
        waitForText(testId("chosen-file"), csv.getFileName().toString());
        click(testId("preview-import"));

        visible(testId("preview-summary"));
        assertEquals("3", driver.findElement(testId("valid-count")).getText());
        assertEquals("1", driver.findElement(testId("invalid-count")).getText());
        String issues = driver.findElement(testId("import-issues")).getText();
        assertTrue(issues.contains("TOTAL_TIME_EXCEEDED"), "expected the 70-minute row to be reported: " + issues);

        click(testId("confirm-import"));
        visible(testId("import-result"));
        assertEquals("3", driver.findElement(testId("imported-count")).getText());
        assertEquals("1", driver.findElement(testId("failed-count")).getText());
        wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("import-jobs"), "COMPLETED_WITH_ERRORS"));

        open("/admin/recipes");
        type(By.id("admin-recipe-q"), "E2E Egg Toast " + suffix);
        driver.findElement(By.id("admin-recipe-q")).sendKeys(Keys.ENTER);
        waitForText(testId("admin-recipe-rows"), "E2E Egg Toast " + suffix);
        assertTrue(driver.findElement(testId("admin-recipe-rows")).getText().contains("Published"));
    }

    @Test
    @DisplayName("SEL-05 A file over the 500-row limit is rejected before anything is written")
    void tooManyRowsRejected() throws Exception {
        String[] admin = adminCredentials();
        StringBuilder big = new StringBuilder("title,prep_time_mins,cook_time_mins,difficulty_level,estimated_cost_bdt,ingredients_csv,instructions_pipe\n");
        for (int i = 1; i <= 501; i++) {
            big.append("E2E Bulk ").append(suffix).append('-').append(i).append(",1,2,EASY,10,Egg:1:pcs,Cook\n");
        }
        Files.writeString(csv, big.toString(), StandardCharsets.UTF_8);
        login(admin[0], admin[1]);
        open("/admin/imports");
        present(testId("import-file")).sendKeys(csv.toAbsolutePath().toString());
        click(testId("preview-import"));
        waitForText(testId("import-file-error"), "more than 500");
    }
}

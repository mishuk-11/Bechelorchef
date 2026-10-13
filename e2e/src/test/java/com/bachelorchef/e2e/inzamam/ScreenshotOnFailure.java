package com.bachelorchef.e2e.inzamam;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

/**
 * Saves a screenshot and the page text to target/e2e-failures when a test fails. Runs before
 * {@code @AfterEach}, so the browser is still open.
 */
public class ScreenshotOnFailure implements AfterTestExecutionCallback {

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        if (context.getExecutionException().isEmpty() || !(context.getRequiredTestInstance() instanceof UiTestBase test) || test.driver == null) {
            return;
        }
        Path dir = Path.of("target", "e2e-failures");
        Files.createDirectories(dir);
        String name = context.getRequiredTestClass().getSimpleName() + "-" + context.getRequiredTestMethod().getName();
        Path shot = ((TakesScreenshot) test.driver).getScreenshotAs(OutputType.FILE).toPath();
        Files.copy(shot, dir.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(dir.resolve(name + ".txt"), test.driver.getCurrentUrl() + "\n\n"
                + test.driver.findElement(org.openqa.selenium.By.tagName("body")).getText());
    }
}

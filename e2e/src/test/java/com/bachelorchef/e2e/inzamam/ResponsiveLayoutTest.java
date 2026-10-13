package com.bachelorchef.e2e.inzamam;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Public pages at phone width (390 px, Chrome mobile emulation) must not scroll horizontally. */
class ResponsiveLayoutTest {

    private WebDriver driver;
    private WebDriverWait wait;
    private String baseUrl;

    @BeforeEach
    void phone() {
        baseUrl = UiTestBase.config("E2E_BASE_URL", "http://localhost:3000").replaceAll("/$", "");
        ChromeOptions options = new ChromeOptions();
        if (Boolean.parseBoolean(UiTestBase.config("E2E_HEADLESS", "true"))) {
            options.addArguments("--headless=new");
        }
        options.setExperimentalOption("mobileEmulation",
                Map.of("deviceMetrics", Map.of("width", 390, "height", 844, "pixelRatio", 3.0), "userAgent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0 Mobile Safari/537.36"));
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    @AfterEach
    void close() {
        if (driver != null) {
            driver.quit();
        }
    }

    @ParameterizedTest(name = "{0} Phone width 390 px: {1} has no horizontal scroll")
    @CsvSource({"SEL-06, /", "SEL-07, /recipes", "SEL-08, /recipes/1", "SEL-09, /match", "SEL-10, /login", "SEL-11, /register"})
    void noHorizontalScroll(String id, String path) {
        driver.get(baseUrl + path);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("footer")));
        wait.until(d -> !d.findElement(By.tagName("main")).getText().contains("Loading"));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long scrollWidth = ((Number) js.executeScript("return document.documentElement.scrollWidth;")).longValue();
        long viewport = ((Number) js.executeScript("return window.innerWidth;")).longValue();
        assertTrue(scrollWidth <= viewport, path + " overflows horizontally: scrollWidth=" + scrollWidth + " > viewport=" + viewport
                + " widest=" + js.executeScript("""
                        let w = null; let max = 0;
                        document.querySelectorAll('body *').forEach(e => { const r = e.getBoundingClientRect(); if (r.right > max) { max = r.right; w = e; } });
                        return w ? (w.tagName + '.' + w.className + ' right=' + max) : '';
                        """));
    }
}

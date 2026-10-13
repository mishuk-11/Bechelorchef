package com.bachelorchef.e2e.inzamam;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Shared Selenium setup. Configuration (environment variables or -D system properties):
 * <ul>
 *   <li>E2E_BASE_URL (default http://localhost:3000)</li>
 *   <li>E2E_BROWSER: chrome (default), edge or firefox</li>
 *   <li>E2E_HEADLESS: true (default) or false</li>
 *   <li>E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD: the bootstrap admin of the environment under test</li>
 * </ul>
 * Synchronisation uses explicit WebDriverWait conditions only (no Thread.sleep).
 */
@ExtendWith(ScreenshotOnFailure.class)
abstract class UiTestBase {

    /**
     * Chrome/Edge show a modal "change your password" warning after logging in with a leaked password
     * (such as a short test password). The modal swallows every later click, so tests turn it off.
     */
    static final java.util.Map<String, Object> NO_PASSWORD_PROMPTS = java.util.Map.of(
            "credentials_enable_service", false,
            "profile.password_manager_enabled", false,
            "profile.password_manager_leak_detection", false);

    protected WebDriver driver;
    protected WebDriverWait wait;
    protected String baseUrl;

    static String config(String name, String fallback) {
        String value = System.getProperty(name, System.getenv(name));
        return value == null || value.isBlank() ? fallback : value;
    }

    @BeforeEach
    void startBrowser() {
        baseUrl = config("E2E_BASE_URL", "http://localhost:3000").replaceAll("/$", "");
        boolean headless = Boolean.parseBoolean(config("E2E_HEADLESS", "true"));
        String browser = config("E2E_BROWSER", "chrome").toLowerCase(Locale.ROOT);
        driver = switch (browser) {
            case "edge" -> {
                EdgeOptions o = new EdgeOptions();
                if (headless) o.addArguments("--headless=new");
                o.addArguments("--window-size=1366,900", "--disable-features=PasswordLeakDetection");
                o.setExperimentalOption("prefs", NO_PASSWORD_PROMPTS);
                yield new EdgeDriver(o);
            }
            case "firefox" -> {
                FirefoxOptions o = new FirefoxOptions();
                if (headless) o.addArguments("-headless");
                yield new FirefoxDriver(o);
            }
            default -> {
                ChromeOptions o = new ChromeOptions();
                if (headless) o.addArguments("--headless=new");
                o.addArguments("--window-size=1366,900", "--disable-gpu", "--no-first-run", "--disable-features=PasswordLeakDetection");
                o.setExperimentalOption("prefs", NO_PASSWORD_PROMPTS);
                yield new ChromeDriver(o);
            }
        };
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    /** Always runs, even when a test fails, so no browser processes leak. */
    @AfterEach
    void stopBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected void open(String path) {
        driver.get(baseUrl + path);
    }

    protected WebElement visible(By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    /** Present in the DOM (e.g. a visually hidden file input), waiting for client-side rendering. */
    protected WebElement present(By by) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    protected WebElement clickable(By by) {
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    protected void click(By by) {
        WebElement element = clickable(by);
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
        element.click();
    }

    protected void type(By by, String text) {
        WebElement element = visible(by);
        element.clear();
        element.sendKeys(text);
    }

    protected static By testId(String id) {
        return By.cssSelector("[data-testid='" + id + "']");
    }

    protected void waitForText(By by, String text) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(by, text));
    }

    protected void waitForUrlContaining(String fragment) {
        wait.until(ExpectedConditions.urlContains(fragment));
    }

    protected List<WebElement> all(By by) {
        return driver.findElements(by);
    }

    protected String[] adminCredentials() {
        String email = config("E2E_ADMIN_EMAIL", null);
        String password = config("E2E_ADMIN_PASSWORD", null);
        Assumptions.assumeTrue(email != null && password != null,
                "Set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD to the environment's bootstrap admin to run admin UI tests");
        return new String[]{email, password};
    }

    /** A normal (USER) account of the environment under test, from E2E_USER_EMAIL / E2E_USER_PASSWORD. */
    protected String[] userCredentials() {
        String email = config("E2E_USER_EMAIL", null);
        String password = config("E2E_USER_PASSWORD", null);
        Assumptions.assumeTrue(email != null && password != null,
                "Set E2E_USER_EMAIL and E2E_USER_PASSWORD to a normal user account to run the seeded-defect UI tests");
        return new String[]{email, password};
    }

    /**
     * Explicit wait that reports instead of throwing: true as soon as the condition holds, false if it
     * still does not hold after the timeout. Lets a test fail with a clear assertion message.
     */
    protected boolean eventually(java.util.function.Function<WebDriver, Boolean> condition, int seconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(seconds)).ignoring(org.openqa.selenium.StaleElementReferenceException.class)
                    .until(condition);
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    protected void login(String email, String password) {
        open("/login");
        type(By.id("email"), email);
        type(By.id("password"), password);
        click(testId("login-submit"));
        visible(testId("logout"));
    }
}

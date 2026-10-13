# Selenium UI tests (owner: Inzamam)

Java 21 + Selenium 4 + JUnit 5. The tests drive a real browser against a running environment (API + web app + database).
They wait only with explicit `WebDriverWait` conditions (no `Thread.sleep`) and always close the browser in `@AfterEach`. When a test fails, they save a screenshot and the page text to `target/e2e-failures/`.

Code: `src/test/java/com/bachelorchef/e2e/inzamam/`. Each test's display name starts with its case ID (SEL-01..16). The report is `docs/stqa/Inzamam/BachelorChef_SeleniumReport.xlsx`.

| Test class | Cases | What it covers |
|---|---|---|
| `UserJourneyTest` | SEL-01..03 | Register → pantry matcher → recipe → servings → save → “I cooked this” → cookbook, Recipes Done, goal 1/1. Route guards. Not-found state. |
| `AdminCsvImportTest` | SEL-04, 05 | Admin CSV upload: preview 3 valid / 1 invalid, import, recipe listed as Published. 501-row file rejected. |
| `ResponsiveLayoutTest` | SEL-06..11 | Public pages at 390 px (Chrome mobile emulation) have no horizontal scroll. |
| `SeededDefectUiTest` | SEL-12..16 | Checks the three controllers under test through the UI: admin role and category filters, profile bio, goal target 30, goal time limit. |

## Run

1. Start the app with `run.bat` (or see the root README).
2. Configure (environment variables or `-D` properties):

| Name | Default | |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:3000` | web app URL |
| `E2E_BROWSER` | `chrome` | `chrome`, `edge` or `firefox` |
| `E2E_HEADLESS` | `true` | `false` to watch the browser |
| `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD` | - | an admin account; admin tests are **skipped** if unset |
| `E2E_USER_EMAIL` / `E2E_USER_PASSWORD` | - | a normal user account; SEL-14..16 are **skipped** if unset |

```powershell
cd e2e
$env:E2E_ADMIN_EMAIL='inzamam@admin.su'; $env:E2E_ADMIN_PASSWORD='…'
$env:E2E_USER_EMAIL='user9@user.com';    $env:E2E_USER_PASSWORD='…'
.\mvnw.cmd test "-Dmaven.test.failure.ignore=true"
```

Selenium Manager downloads a matching driver automatically (set `SE_CACHE_PATH` to choose where).
Chrome's password-manager prompts and its "leaked password" warning are switched off in the test browser. Otherwise that modal blocks every click after logging in with a weak test password.
Do not point these tests at production: they create users, recipes, goals and import jobs (titles start with `E2E`, `Thirty dinners` or `Quick dinners`).

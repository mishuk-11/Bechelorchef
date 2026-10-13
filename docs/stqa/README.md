# STQA - Bachelor Chef

Three team members test three controllers. Each member owns their own test code and their own result folder, so everyone commits only their own files.

| Member | Module | Techniques | Extra parts |
|---|---|---|---|
| **Inzamam** | `AdminManagementController` | BVA, ECT, basis path (5 functions), 30 test cases | Defect report (30 defects), JUnit unit tests for the whole backend, Selenium automation |
| **Cherry** | `ProfileController` | BVA, ECT, basis path (5 functions), 30 test cases | |
| **Saima** | `PersonalController` | BVA, ECT, basis path (5 functions), 30 test cases | |

Latest numbers: [RESULTS.md](RESULTS.md).

## Where everything is

```text
backend/src/test/java/com/bachelorchef/stqa/
├── inzamam/                       Inzamam - AdminManagementController
│   ├── AdminTestKit.java          shared setup (MockMvc + mocked services)
│   ├── AdminManagementBvaTest.java        TC-01..10
│   ├── AdminManagementEctTest.java        TC-11..20
│   ├── AdminManagementBasisPathTest.java  TC-21..30
│   └── unit/                      JUnit unit tests for the rest of the backend (287 tests)
├── cherry/                        Cherry - ProfileController   (same layout, TC-01..30)
└── saima/                         Saima - PersonalController   (same layout, TC-01..30)

e2e/src/test/java/com/bachelorchef/e2e/inzamam/   Inzamam - Selenium (SEL-01..16)

docs/stqa/
├── generate_reports.py            builds every workbook/report below from the JUnit XML
├── RESULTS.md                     one-page overview
├── Inzamam/
│   ├── design.py                  test design: BVA + ECT tables, 30 cases, flow graphs
│   ├── AdminManagement_TestCases.xlsx    the 30 cases in the team format
│   ├── AdminManagement_TestDesign.xlsx   summary, BVA, ECT, full case sheet, defects
│   ├── AdminManagement_BasisPath.xlsx    V(G), paths, node legend
│   ├── AdminManagement_FlowGraphs.png    control-flow graphs of the 5 functions
│   ├── AdminManagement_TestReport.md     everything above in one readable page
│   ├── defects.json + BachelorChef_DefectReport.xlsx/.md   30 defects
│   ├── BachelorChef_UnitTestReport.xlsx  every JUnit test of the backend
│   ├── selenium.py + BachelorChef_SeleniumReport.xlsx      Selenium cases
│   └── evidence/                  JUnit XML of the run (unit/, selenium/ inside)
├── Cherry/                        Profile_* files + design.py + evidence/
└── Saima/                         Personal_* files + design.py + evidence/
```

Test case format (all `*_TestCases.xlsx` and the Selenium report):
`TC ID | Action/Feature | Test Data | Steps | Expected Result | Actual Result | Status | xUnit Method`.
*Status* and *Actual Result* are never typed by hand - the generator copies them from the JUnit XML.

## How the controller tests work

- **JUnit 5** (the xUnit family for Java) + **Mockito** + **MockMvc**.
- The controller runs in a standalone MockMvc with its real request validation and real error handler. The services behind it are Mockito mocks. A test therefore checks only what the controller itself does, needs no database, and runs in about a second.
- **BVA** and **ECT** tests send real HTTP requests (`MockMvc`), so validation, status codes and JSON are tested.
- **Basis path** tests call each of the 5 functions directly, so each test runs exactly one independent path of the flow graph. V(G) is counted from the current source (decisions + 1) and cross-checked with E − N + 2.
- Each test's display name starts with its TC ID (for example `TC-08 ...`). That is how the generator matches a test to its row.

## Run the tests and rebuild the reports

```powershell
# 1. Unit tests (all three members + the whole backend). Failing tests = defects found.
cd backend
.\mvnw.cmd test "-Dmaven.test.failure.ignore=true"

# Only one member:  .\mvnw.cmd test "-Dtest=com.bachelorchef.stqa.cherry.*" "-Dmaven.test.failure.ignore=true"

# 2. Selenium (start the app first with run.bat)
cd ..\e2e
$env:E2E_ADMIN_EMAIL='inzamam@admin.su'; $env:E2E_ADMIN_PASSWORD='<password>'
$env:E2E_USER_EMAIL='user9@user.com';    $env:E2E_USER_PASSWORD='<password>'
.\mvnw.cmd test "-Dmaven.test.failure.ignore=true"

# 3. Copy the fresh JUnit XML into */evidence and rebuild every workbook (needs: pip install openpyxl)
cd ..
python docs\stqa\generate_reports.py --collect
```

`-Dmaven.test.failure.ignore=true` lets Maven finish and write all results even though the defect-finding tests fail. That is intentional: the seeded defects are still in the code.

The flow-graph PNGs need Graphviz `dot` (on PATH, in `GRAPHVIZ_DOT`, or the portable copy in `.devtools\graphviz`).

## How each contributor works (same as the reference repo)

1. Clone the repository and set your own Git name and email (`git config user.name`, `git config user.email`), so the commits show who did the work.
2. Make your own branch, for example `stqa/cherry`.
3. Change **only your own folders**: `backend/src/test/java/com/bachelorchef/stqa/<you>/` and `docs/stqa/<You>/`. Never edit another member's files.
4. To add or change a test case, edit your test class **and** your `design.py` (same TC ID), run the tests, then run `generate_reports.py --collect`.
5. Commit your tests, your `design.py`, the regenerated files in `docs/stqa/<You>/` and your `evidence/`, for example `Cherry: add ProfileController BVA, ECT and basis-path tests`.
6. Push and open a pull request. Inzamam reviews and merges, then reruns the generator so `RESULTS.md`, the defect report and the unit-test report include everyone.

## Defects

The 30 defects are seeded on purpose: 10 in each controller, all Minor or Low. None of them touches security, permissions, passwords or data integrity. Every one is confirmed by at least one failing automated test. See `Inzamam/BachelorChef_DefectReport.xlsx`.

The clean controllers are kept outside the repository (`.devtools/BUGLOG/original/`). Once they are restored, every defect test should pass, and those tests become regression tests.

## Note for manual testing in Chrome

Chrome shows a modal "Change your password" warning after you log in with a password from a public breach list (for example `123456`). Until you close it, clicks on the page do nothing. The Selenium tests switch this warning off.

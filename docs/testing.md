# Testing

Testing is organised per STQA team member. The full guide is [stqa/README.md](stqa/README.md).

| Layer | Tool | Where | Owner |
|---|---|---|---|
| Controller tests: BVA, ECT, basis path (30 cases per controller) | JUnit 5, Mockito, MockMvc | `backend/src/test/java/com/bachelorchef/stqa/{inzamam,cherry,saima}/` | Inzamam (AdminManagementController), Cherry (ProfileController), Saima (PersonalController) |
| Unit tests for the rest of the backend (validation, pantry matching, imports, goals, auth rules) | JUnit 5, Mockito | `backend/src/test/java/com/bachelorchef/stqa/inzamam/unit/` | Inzamam |
| UI automation | Selenium 4 + JUnit 5 | `e2e/src/test/java/com/bachelorchef/e2e/inzamam/` | Inzamam |
| Frontend | Vitest, ESLint, TypeScript | `frontend/` | - |

## Run

```bash
cd backend && ./mvnw test -Dmaven.test.failure.ignore=true      # 377 tests, no database needed
cd e2e && ./mvnw test -Dmaven.test.failure.ignore=true          # app must be running; see e2e/README.md
python docs/stqa/generate_reports.py --collect                  # rebuild workbooks from the JUnit XML
```

The three controllers contain 30 deliberately seeded defects (Minor/Low). The tests that expose them fail by design; see [stqa/Inzamam/BachelorChef_DefectReport.md](stqa/Inzamam/BachelorChef_DefectReport.md).

## Test design rules used by every member

- **BVA:** seven points per bounded input (min−1, min, min+1, nominal, max−1, max, max+1), taken from the real constraint (DTO annotation, database column or business rule in AGENTS.md section 5).
- **ECT:** one representative value per valid and invalid class.
- **Basis path:** V(G) is counted from the current source (decisions + 1) and checked against E − N + 2. There is one test per independent path, and the test calls the function directly.
- **Test cases:** each test's display name starts with its TC ID. Status and Actual Result in the workbooks come from the JUnit XML, never typed by hand.

## Earlier suites

The original integration/security suite (Testcontainers/PostgreSQL) and the old single STQA workbook were replaced by this per-member layout. A copy is kept locally in `.devtools/test-backup/2026-10-13/` (not committed).

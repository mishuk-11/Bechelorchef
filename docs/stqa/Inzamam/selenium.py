"""Selenium UI automation cases (owner: Inzamam). Results come from evidence/selenium/*.xml."""

TEST_DIR = "e2e/src/test/java/com/bachelorchef/e2e/inzamam"

# id, feature, test data, steps, expected, method, defect (if the test fails)
CASES = [
    ("SEL-01", "Student journey (register to goal)", "new e2e-<time>@example.test; pantry: onion, egg; goal target 1",
     "1. Register  2. Pantry match  3. Open recipe, more servings  4. Save  5. I cooked this  6. Cookbook, Recipes Done  7. Create goal",
     "Recipe in cookbook and history; goal shows 1 / 1 Complete", "UserJourneyTest.studentJourney", "BUG-27"),
    ("SEL-02", "Route guards", "anonymous visitor, then a new USER account",
     "1. Open /cookbook anonymously  2. Register a new user  3. Open /admin",
     "Anonymous -> /login?next=/cookbook; USER -> 'Administrators only'", "UserJourneyTest.routeGuards", ""),
    ("SEL-03", "Recipe not found", "/recipes/999999999",
     "1. Open an unknown recipe id", "'Recipe not found' state, no blank page", "UserJourneyTest.recipeNotFound", ""),
    ("SEL-04", "Admin CSV import", "generated CSV: 3 valid rows, 1 row of 70 min",
     "1. Admin login  2. /admin/imports  3. Upload file  4. Preview  5. Confirm  6. Find recipe in /admin/recipes",
     "Preview 3 valid / 1 invalid (TOTAL_TIME_EXCEEDED); 3 imported; recipe listed as Published", "AdminCsvImportTest.importCsvThroughUi", ""),
    ("SEL-05", "Admin CSV import limit", "generated CSV with 501 data rows",
     "1. Admin login  2. Upload file  3. Preview", "Rejected: more than 500 rows; nothing written", "AdminCsvImportTest.tooManyRowsRejected", ""),
    ("SEL-06", "Phone layout (390 px)", "/", "1. Open page in Chrome mobile emulation", "No horizontal scroll", "ResponsiveLayoutTest.noHorizontalScroll(SEL-06, /)", ""),
    ("SEL-07", "Phone layout (390 px)", "/recipes", "1. Open page in Chrome mobile emulation", "No horizontal scroll", "ResponsiveLayoutTest.noHorizontalScroll(SEL-07, /recipes)", ""),
    ("SEL-08", "Phone layout (390 px)", "/recipes/1", "1. Open page in Chrome mobile emulation", "No horizontal scroll", "ResponsiveLayoutTest.noHorizontalScroll(SEL-08, /recipes/1)", ""),
    ("SEL-09", "Phone layout (390 px)", "/match", "1. Open page in Chrome mobile emulation", "No horizontal scroll", "ResponsiveLayoutTest.noHorizontalScroll(SEL-09, /match)", ""),
    ("SEL-10", "Phone layout (390 px)", "/login", "1. Open page in Chrome mobile emulation", "No horizontal scroll", "ResponsiveLayoutTest.noHorizontalScroll(SEL-10, /login)", ""),
    ("SEL-11", "Phone layout (390 px)", "/register", "1. Open page in Chrome mobile emulation", "No horizontal scroll", "ResponsiveLayoutTest.noHorizontalScroll(SEL-11, /register)", ""),
    ("SEL-12", "Admin users: role filter", "Role = Admin",
     "1. Admin login  2. /admin/users  3. Choose role 'Admin'", "Only ADMIN rows listed", "SeededDefectUiTest.adminRoleFilter", "BUG-07"),
    ("SEL-13", "Admin ingredients: category filter", "Category = Spices",
     "1. Admin login  2. /admin/ingredients  3. Choose category 'Spices'", "Only Spices rows listed", "SeededDefectUiTest.ingredientCategoryFilter", "BUG-01"),
    ("SEL-14", "Profile: bio", "bio = 'Selenium bio <time>'",
     "1. User login  2. /profile  3. Type bio  4. Save profile  5. Reload", "Bio still shown after reload", "SeededDefectUiTest.bioIsSaved", "BUG-11"),
    ("SEL-15", "Goals: maximum target", "target = 30",
     "1. User login  2. /goals  3. Create goal with target 30", "Goal created and listed", "SeededDefectUiTest.goalTargetThirty", "BUG-28"),
    ("SEL-16", "Goals: time limit", "target = 3, only recipes up to 15 min",
     "1. User login  2. /goals  3. Create goal with a 15-minute limit", "Goal card shows '<= 15 min'", "SeededDefectUiTest.goalTimeLimitKept", "BUG-29"),
]

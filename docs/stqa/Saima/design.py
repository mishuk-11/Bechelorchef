"""Test design for PersonalController (owner: Saima).

Read by ../generate_reports.py. Results (Pass/Fail, time, failure text) are NOT written here:
they come from the JUnit XML in evidence/ when the generator runs.
"""

MEMBER = "Saima"
CONTROLLER = "PersonalController"
SHORT = "Personal"
SOURCE = "backend/src/main/java/com/bachelorchef/controller/PersonalController.java"
TEST_DIR = "backend/src/test/java/com/bachelorchef/stqa/saima"
SUITE_PREFIX = "Saima - PersonalController"
BVA_CLASS, ECT_CLASS, BP_CLASS = "PersonalBvaTest", "PersonalEctTest", "PersonalBasisPathTest"
LOGIN = "Signed in as USER (user2@user.com)"

BVA = [
    ("Goal target count", "createGoal (POST /users/me/goals)", "1..30 (AGENTS.md 5; GoalService)",
     "0", "1", "2", "5", "29", "30", "31", "0 and 31 rejected (400); 1..30 created (201)", "TC-01..05, TC-28"),
    ("Cook notes length", "updateHistory (PATCH /users/me/cooked-history/{id})", "<= 1000 chars (CookRequest @Size(max = 1000))",
     "-", "0", "1", "50", "999", "1000", "1001", "1001 rejected (400); up to 1000 saved", "TC-06..08, TC-22"),
    ("Cookbook page size", "savedRecipes (GET /users/me/saved-recipes)", "1..100 (Pagination.MAX_SIZE)",
     "0", "1", "2", "12", "99", "100", "101", "Size passed unchanged (the web app asks for 12)", "TC-09, TC-10, TC-20"),
]

ECT = [
    ("Save to cookbook", "EC1", "Existing recipe", "Valid", "recipe 1", "204 No Content", "TC-11"),
    ("Remove from cookbook", "EC2", "Saved recipe", "Valid", "recipe 1", "204 No Content", "TC-12"),
    ("Save to cookbook", "EC3", "Unknown recipe", "Invalid", "recipe 999", "404 NOT_FOUND", "TC-13"),
    ("Goal count mode", "EC4", "COOK_EVENTS", "Valid", "COOK_EVENTS", "Mode changed", "TC-14"),
    ("Goal count mode", "EC5", "UNIQUE_RECIPES (default)", "Valid", "UNIQUE_RECIPES", "Mode kept", "TC-27"),
    ("Goal time limit", "EC6", "Limit given", "Valid", "15 minutes", "Limit saved on the goal", "TC-15, TC-29"),
    ("Goal time limit", "EC7", "No limit", "Valid", "(none)", "Goal without a limit", "TC-27"),
    ("Goal state", "EC8", "Completed goal", "Valid", "progress 1/1", "Listed as completed", "TC-16, TC-26"),
    ("Goal state", "EC9", "Goal in progress", "Valid", "progress 2/5", "Listed", "TC-25"),
    ("Cook date edit", "EC10", "New date given", "Valid", "2026-10-12T12:00:00Z", "Date changed", "TC-17, TC-23"),
    ("Cook history", "EC11", "Same recipe cooked twice", "Valid", "2 cooks of recipe 30", "2 cooks, 1 different recipe", "TC-18, TC-30"),
]

_P = "1. " + LOGIN + "  2. "
CASES = [
    ("TC-01", "BVA", "createGoal", "Create goal: target count (BVA)", "targetCount = 0",
     _P + "POST /api/v1/users/me/goals", "400 'Must be between 1 and 30.'", "PersonalBvaTest.goalTarget(TC-01, 0, false)", ""),
    ("TC-02", "BVA", "createGoal", "Create goal: target count (BVA)", "targetCount = 1 (min)",
     _P + "POST /api/v1/users/me/goals", "201 Created", "PersonalBvaTest.goalTarget(TC-02, 1, true)", ""),
    ("TC-03", "BVA", "createGoal", "Create goal: target count (BVA)", "targetCount = 29",
     _P + "POST /api/v1/users/me/goals", "201 Created", "PersonalBvaTest.goalTarget(TC-03, 29, true)", ""),
    ("TC-04", "BVA", "createGoal", "Create goal: target count (BVA)", "targetCount = 30 (max)",
     _P + "POST /api/v1/users/me/goals", "201 Created: 30 is the maximum allowed", "PersonalBvaTest.goalTarget(TC-04, 30, true)", "BUG-28"),
    ("TC-05", "BVA", "createGoal", "Create goal: target count (BVA)", "targetCount = 31",
     _P + "POST /api/v1/users/me/goals", "400 'Must be between 1 and 30.'", "PersonalBvaTest.goalTarget(TC-05, 31, false)", ""),
    ("TC-06", "BVA", "updateHistory", "Edit cook: notes length (BVA)", "notes = 999 chars",
     _P + "PATCH /api/v1/users/me/cooked-history/3", "200; notes saved", "PersonalBvaTest.notesLength(TC-06, 999, true)", ""),
    ("TC-07", "BVA", "updateHistory", "Edit cook: notes length (BVA)", "notes = 1000 chars (max)",
     _P + "PATCH /api/v1/users/me/cooked-history/3", "200; notes saved", "PersonalBvaTest.notesLength(TC-07, 1000, true)", "BUG-26"),
    ("TC-08", "BVA", "updateHistory", "Edit cook: notes length (BVA)", "notes = 1001 chars",
     _P + "PATCH /api/v1/users/me/cooked-history/3", "400 fieldErrors.notes", "PersonalBvaTest.notesLength(TC-08, 1001, false)", ""),
    ("TC-09", "BVA", "savedRecipes", "Cookbook: page size (BVA)", "size = 12",
     _P + "GET /api/v1/users/me/saved-recipes?size=12", "Service asked for size 12", "PersonalBvaTest.cookbookPageSize(TC-09, 12)", "BUG-22"),
    ("TC-10", "BVA", "savedRecipes", "Cookbook: page size (BVA)", "size = 10",
     _P + "GET /api/v1/users/me/saved-recipes?size=10", "Service asked for size 10", "PersonalBvaTest.cookbookPageSize(TC-10, 10)", ""),
    ("TC-11", "ECT", "save", "Save recipe to cookbook (ECT)", "recipe 1",
     _P + "PUT /api/v1/users/me/saved-recipes/1", "204 No Content", "PersonalEctTest.saveRecipe", "BUG-21"),
    ("TC-12", "ECT", "unsave", "Remove recipe from cookbook (ECT)", "recipe 1",
     _P + "DELETE /api/v1/users/me/saved-recipes/1", "204 No Content", "PersonalEctTest.unsaveRecipe", ""),
    ("TC-13", "ECT", "save", "Save unknown recipe (ECT)", "recipe 999 (not found)",
     _P + "PUT /api/v1/users/me/saved-recipes/999", "404, code NOT_FOUND", "PersonalEctTest.saveUnknownRecipe", ""),
    ("TC-14", "ECT", "updateGoal", "Edit goal: count mode (ECT)", "countMode = COOK_EVENTS",
     _P + "PATCH /api/v1/users/me/goals/4", "Service gets countMode COOK_EVENTS", "PersonalEctTest.changeCountMode", "BUG-30"),
    ("TC-15", "ECT", "createGoal", "Create goal: time limit (ECT)", "maxTotalTimeMins = 15",
     _P + "POST /api/v1/users/me/goals", "201; service gets maxTotalTimeMins 15", "PersonalEctTest.goalWithTimeLimit", "BUG-29"),
    ("TC-16", "ECT", "goals", "Goals list: completed goal (ECT)", "goals: 1/1 (done), 2/5",
     _P + "GET /api/v1/users/me/goals", "Both goals listed; first is completed", "PersonalEctTest.completedGoalListed", "BUG-27"),
    ("TC-17", "ECT", "updateHistory", "Edit cook: new date (ECT)", "cookedOn = 2026-10-12T12:00:00Z",
     _P + "PATCH /api/v1/users/me/cooked-history/3", "Service gets the new date", "PersonalEctTest.changeCookDate", "BUG-25"),
    ("TC-18", "ECT", "history", "Cook history: same recipe twice (ECT)", "2 cooks of recipe 30",
     _P + "GET /api/v1/users/me/cooked-history", "totalCookEvents 2, distinctRecipes 1", "PersonalEctTest.repeatRecipeTotals", "BUG-23"),
    ("TC-19", "Basis path", "savedRecipes", "savedRecipes P1 (basis path)", "size = null",
     "1. Call savedRecipes(user, 0, null)", "Service gets size null", "PersonalBasisPathTest.savedRecipesP1", ""),
    ("TC-20", "Basis path", "savedRecipes", "savedRecipes P2 (basis path)", "size = 50",
     "1. Call savedRecipes(user, 0, 50)", "Service gets size 50", "PersonalBasisPathTest.savedRecipesP2", "BUG-22"),
    ("TC-21", "Basis path", "updateHistory", "updateHistory P1 (basis path)", "notes = null, cookedOn = null",
     "1. Call updateHistory(user, 3, request)", "Request sent unchanged", "PersonalBasisPathTest.updateHistoryP1", ""),
    ("TC-22", "Basis path", "updateHistory", "updateHistory P2 (basis path)", "notes = 1200 chars",
     "1. Call updateHistory(user, 3, request)", "ApiException 400; nothing saved", "PersonalBasisPathTest.updateHistoryP2", ""),
    ("TC-23", "Basis path", "updateHistory", "updateHistory P3 (basis path)", "notes = 'Tasty', new date",
     "1. Call updateHistory(user, 3, request)", "Request (date + notes) sent unchanged", "PersonalBasisPathTest.updateHistoryP3", "BUG-25"),
    ("TC-24", "Basis path", "goals", "goals P1 (basis path)", "no goals",
     "1. Call goals(user)", "Empty list", "PersonalBasisPathTest.goalsP1", ""),
    ("TC-25", "Basis path", "goals", "goals P2 (basis path)", "1 goal in progress",
     "1. Call goals(user)", "Goal listed", "PersonalBasisPathTest.goalsP2", ""),
    ("TC-26", "Basis path", "goals", "goals P3 (basis path)", "1 completed goal",
     "1. Call goals(user)", "Goal listed as completed", "PersonalBasisPathTest.goalsP3", "BUG-27"),
    ("TC-27", "Basis path", "createGoal", "createGoal P1 (basis path)", "targetCount = null",
     "1. Call createGoal(user, request)", "Request sent to the service unchanged", "PersonalBasisPathTest.createGoalP1", ""),
    ("TC-28", "Basis path", "createGoal", "createGoal P2 (basis path)", "targetCount = 45",
     "1. Call createGoal(user, request)", "ApiException 400; nothing saved", "PersonalBasisPathTest.createGoalP2", ""),
    ("TC-29", "Basis path", "createGoal", "createGoal P3 (basis path)", "targetCount = 5, limit 20 min",
     "1. Call createGoal(user, request)", "Request saved unchanged; 201 Created", "PersonalBasisPathTest.createGoalP3", "BUG-29"),
    ("TC-30", "Basis path", "history", "history P1 (basis path)", "2 cooks of 1 recipe, newest first",
     "1. Call history(user, 0, 15)", "Service page returned unchanged", "PersonalBasisPathTest.historyP1", "BUG-23, BUG-24"),
]

FUNCTIONS = [
    {
        "name": "savedRecipes", "lines": "45-49", "predicates": ["size == null"],
        "nodes": [(1, "start", "45-47", "receive user, page, size"),
                  (2, "decision", "48", "size == null ?"),
                  (3, "process", "48", "sizeArg = null"),
                  (4, "process", "48", "sizeArg = Math.min(size, 10)"),
                  (5, "return", "48", "return saved.list(user, page, sizeArg)")],
        "edges": [(1, 2, ""), (2, 3, "T"), (2, 4, "F"), (3, 5, ""), (4, 5, ""), (5, "End", "")],
        "paths": [("P1", [1, 2, 3, 5], "size == null: T", "Service gets size null", "TC-19"),
                  ("P2", [1, 2, 4, 5], "size == null: F", "Service gets the requested size", "TC-20")],
    },
    {
        "name": "updateHistory", "lines": "75-81", "predicates": ["notes != null", "notes.length() >= 1000"],
        "nodes": [(1, "start", "75-76", "receive user, historyId, @Valid request"),
                  (2, "decision", "77", "request.notes() != null ?"),
                  (3, "decision", "77", "notes.length() >= 1000 ?"),
                  (4, "error", "78", "throw ApiException.validation(notes)"),
                  (5, "return", "80", "return history.update(user, id, new CookRequest(null, notes))")],
        "edges": [(1, 2, ""), (2, 3, "T"), (2, 5, "F"), (3, 4, "T"), (3, 5, "F"), (4, "End", ""), (5, "End", "")],
        "paths": [("P1", [1, 2, 5], "notes != null: F", "Request sent unchanged", "TC-21"),
                  ("P2", [1, 2, 3, 4], "notes != null: T; >= 1000: T", "400 validation error", "TC-22"),
                  ("P3", [1, 2, 3, 5], "notes != null: T; >= 1000: F", "Request (date + notes) sent", "TC-23")],
    },
    {
        "name": "goals", "lines": "90-92", "predicates": ["stream: another goal?", "!goal.completed()"],
        "nodes": [(1, "start", "90", "receive user"),
                  (2, "process", "91", "list = goals.list(user)"),
                  (3, "decision", "91", "another goal in list ?"),
                  (4, "decision", "91", "filter: !g.completed() ?"),
                  (5, "process", "91", "keep goal"),
                  (6, "return", "91", "return kept goals (toList)")],
        "edges": [(1, 2, ""), (2, 3, ""), (3, 4, "T"), (4, 5, "T"), (4, 3, "F"), (5, 3, ""), (3, 6, "F"), (6, "End", "")],
        "paths": [("P1", [1, 2, 3, 6], "loop: 0 times", "Empty list", "TC-24"),
                  ("P2", [1, 2, 3, 4, 5, 3, 6], "loop once; !completed: T", "Goal in progress listed", "TC-25"),
                  ("P3", [1, 2, 3, 4, 3, 6], "loop once; !completed: F", "Completed goal listed", "TC-26")],
    },
    {
        "name": "createGoal", "lines": "97-105", "predicates": ["targetCount != null", "targetCount >= 30"],
        "nodes": [(1, "start", "97-98", "receive user, request (no @Valid)"),
                  (2, "decision", "99", "request.targetCount() != null ?"),
                  (3, "decision", "99", "targetCount >= 30 ?"),
                  (4, "error", "100", "throw ApiException.validation(targetCount)"),
                  (5, "process", "102-103", "goal = new GoalRequest(..., maxTotalTimeMins = null)"),
                  (6, "return", "104", "return 201 Created(goals.create(user, goal))")],
        "edges": [(1, 2, ""), (2, 3, "T"), (2, 5, "F"), (3, 4, "T"), (3, 5, "F"), (4, "End", ""), (5, 6, ""), (6, "End", "")],
        "paths": [("P1", [1, 2, 5, 6], "targetCount != null: F", "Request sent unchanged", "TC-27"),
                  ("P2", [1, 2, 3, 4], "!= null: T; >= 30: T", "400 validation error", "TC-28"),
                  ("P3", [1, 2, 3, 5, 6], "!= null: T; >= 30: F", "Saved unchanged, 201 Created", "TC-29")],
    },
    {
        "name": "history", "lines": "65-72", "predicates": [],
        "nodes": [(1, "start", "65-67", "receive user, page, size"),
                  (2, "process", "68", "result = history.list(user, page, size)"),
                  (3, "process", "69", "p = result.page()"),
                  (4, "return", "70-71", "return new HistoryPage(p with items.reversed(), total, total)")],
        "edges": [(1, 2, ""), (2, 3, ""), (3, 4, ""), (4, "End", "")],
        "paths": [("P1", [1, 2, 3, 4], "(no decision)", "Service page returned unchanged", "TC-30")],
    },
]

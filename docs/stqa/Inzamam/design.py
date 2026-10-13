"""Test design for AdminManagementController (owner: Inzamam).

Read by ../generate_reports.py. Results (Pass/Fail, time, failure text) are NOT written here:
they come from the JUnit XML in evidence/ when the generator runs.
"""

MEMBER = "Inzamam"
CONTROLLER = "AdminManagementController"
SHORT = "AdminManagement"
SOURCE = "backend/src/main/java/com/bachelorchef/controller/AdminManagementController.java"
TEST_DIR = "backend/src/test/java/com/bachelorchef/stqa/inzamam"
SUITE_PREFIX = "Inzamam - AdminManagementController"
BVA_CLASS, ECT_CLASS, BP_CLASS = "AdminManagementBvaTest", "AdminManagementEctTest", "AdminManagementBasisPathTest"
LOGIN = "Signed in as ADMIN (inzamam@admin.su)"

# ---------------------------------------------------------------- BVA table
# input, function, constraint, below min, min, min+1, nominal, max-1, max, above max, expected, cases
BVA = [
    ("Ingredient name length", "createIngredient (POST /admin/ingredients)", "1..80 chars (IngredientRequest @NotBlank @Size(max = 80))",
     "0 (blank)", "1", "2", "20", "79", "80", "81", "0 and 81 rejected (400); 1..80 accepted", "TC-01..05"),
    ("Ingredient page size", "ingredients (GET /admin/ingredients)", "1..100 (Pagination.MAX_SIZE)",
     "0", "1", "2", "20", "99", "100", "101", "Size passed to the catalogue unchanged; service rejects 0 and 101", "TC-06, TC-07, TC-22"),
    ("Audit log page index", "auditLogs (GET /admin/audit-logs)", "page >= 0, 0 = first page",
     "-1", "0", "1", "3", "-", "-", "-", "Page passed unchanged; null means the first page", "TC-08, TC-28, TC-29"),
    ("Dashboard date range end", "summary (GET /admin/dashboard/summary)", "from..to inclusive (Asia/Dhaka)",
     "to = from - 1", "to = from", "to = from + 1", "30 days", "-", "today", "-", "End date passed unchanged; one-day range is valid", "TC-09, TC-26, TC-27"),
    ("Tag name length", "createTag (POST /admin/tags)", "1..40 chars (TagRequest @Size(max = 40))",
     "0", "1", "2", "10", "39", "40", "41", "41 rejected (400)", "TC-10"),
]

# ---------------------------------------------------------------- ECT table
# input, class, description, valid/invalid, representative, expected, cases
ECT = [
    ("Category filter", "EC1", "Category given", "Valid", "category=Spices", "Only that category is requested", "TC-11"),
    ("Category filter", "EC2", "Category absent", "Valid", "(none)", "No category filter", "TC-12"),
    ("Role filter", "EC3", "Role given", "Valid", "role=ADMIN", "Only that role is requested", "TC-13"),
    ("Status filter", "EC4", "Status given", "Valid", "status=SUSPENDED", "Only that status is requested", "TC-14"),
    ("Create ingredient", "EC5", "Valid new ingredient", "Valid", "Garlic / Spices / g", "201 Created with the saved ingredient", "TC-15, TC-25"),
    ("Tag name text", "EC6", "Text with surrounding spaces", "Valid", "'   Spicy Food   '", "Saved and answered trimmed", "TC-16"),
    ("Tag name text", "EC7", "Clean text", "Valid", "'Spicy'", "Saved as typed", "(covered by TC-16 setup)"),
    ("Category list", "EC8", "Non-empty list", "Valid", "Dairy, Grains, Spices", "Returned in catalogue A-Z order", "TC-17"),
    ("Status change", "EC9", "Valid status", "Valid", "SUSPENDED + reason", "Updated row returned unchanged", "TC-18, TC-30"),
    ("Status change", "EC10", "Status missing", "Invalid", "{reason only}", "400, field error 'Status is required.'", "TC-19"),
    ("Dashboard date", "EC11", "Valid ISO date", "Valid", "2026-10-13", "Accepted", "TC-09"),
    ("Dashboard date", "EC12", "Impossible calendar date", "Invalid", "2026-02-30", "400, analytics not called", "TC-20"),
]

# ---------------------------------------------------------------- 30 test cases
# id, technique, function, feature, test data, steps, expected, method, defect (if the test fails)
_P = "1. " + LOGIN + "  2. "
CASES = [
    ("TC-01", "BVA", "createIngredient", "Create ingredient: name length (BVA)", "name = '' (0 chars)",
     _P + "POST /api/v1/admin/ingredients", "400, fieldErrors.name; nothing saved", "AdminManagementBvaTest.ingredientNameLength(TC-01, 0, false)", ""),
    ("TC-02", "BVA", "createIngredient", "Create ingredient: name length (BVA)", "name = 1 char",
     _P + "POST /api/v1/admin/ingredients", "Accepted (2xx); catalogue.createIngredient called", "AdminManagementBvaTest.ingredientNameLength(TC-02, 1, true)", ""),
    ("TC-03", "BVA", "createIngredient", "Create ingredient: name length (BVA)", "name = 79 chars",
     _P + "POST /api/v1/admin/ingredients", "Accepted (2xx)", "AdminManagementBvaTest.ingredientNameLength(TC-03, 79, true)", ""),
    ("TC-04", "BVA", "createIngredient", "Create ingredient: name length (BVA)", "name = 80 chars (max)",
     _P + "POST /api/v1/admin/ingredients", "Accepted (2xx): 80 is the maximum allowed", "AdminManagementBvaTest.ingredientNameLength(TC-04, 80, true)", "BUG-05"),
    ("TC-05", "BVA", "createIngredient", "Create ingredient: name length (BVA)", "name = 81 chars",
     _P + "POST /api/v1/admin/ingredients", "400, fieldErrors.name; nothing saved", "AdminManagementBvaTest.ingredientNameLength(TC-05, 81, false)", ""),
    ("TC-06", "BVA", "ingredients", "Ingredient list: page size (BVA)", "size = 1 (min)",
     _P + "GET /api/v1/admin/ingredients?size=1", "Catalogue asked for size 1", "AdminManagementBvaTest.ingredientPageSize(TC-06, 1)", "BUG-02"),
    ("TC-07", "BVA", "ingredients", "Ingredient list: page size (BVA)", "size = 100 (max)",
     _P + "GET /api/v1/admin/ingredients?size=100", "Catalogue asked for size 100", "AdminManagementBvaTest.ingredientPageSize(TC-07, 100)", "BUG-02"),
    ("TC-08", "BVA", "auditLogs", "Audit log: first page (BVA)", "page = 0, size = 20",
     _P + "GET /api/v1/admin/audit-logs?page=0&size=20", "Audit service asked for page 0", "AdminManagementBvaTest.auditFirstPage", "BUG-10"),
    ("TC-09", "BVA", "summary", "Dashboard: one-day range (BVA)", "from = to = 2026-10-13",
     _P + "GET /api/v1/admin/dashboard/summary?from=2026-10-13&to=2026-10-13", "Analytics asked for 2026-10-13..2026-10-13 (inclusive)", "AdminManagementBvaTest.dashboardOneDayRange", "BUG-09"),
    ("TC-10", "BVA", "createTag", "Create tag: name length (BVA)", "name = 41 chars",
     _P + "POST /api/v1/admin/tags", "400 'Must be at most 40 characters.'; nothing saved", "AdminManagementBvaTest.tagNameTooLong", ""),
    ("TC-11", "ECT", "ingredients", "Ingredient list: category given (ECT)", "category = Spices",
     _P + "GET /api/v1/admin/ingredients?category=Spices", "Catalogue asked for category 'Spices'", "AdminManagementEctTest.categoryGiven", "BUG-01"),
    ("TC-12", "ECT", "ingredients", "Ingredient list: category absent (ECT)", "(no category)",
     _P + "GET /api/v1/admin/ingredients", "Catalogue asked with no category filter", "AdminManagementEctTest.categoryAbsent", ""),
    ("TC-13", "ECT", "users", "User list: role filter (ECT)", "role = ADMIN",
     _P + "GET /api/v1/admin/users?role=ADMIN", "User search asked for role ADMIN", "AdminManagementEctTest.roleFilter", "BUG-07"),
    ("TC-14", "ECT", "users", "User list: status filter (ECT)", "status = SUSPENDED",
     _P + "GET /api/v1/admin/users?status=SUSPENDED", "User search asked for status SUSPENDED", "AdminManagementEctTest.statusFilter", ""),
    ("TC-15", "ECT", "createIngredient", "Create ingredient: valid request (ECT)", "name = Garlic, Spices, g",
     _P + "POST /api/v1/admin/ingredients", "201 Created, body name 'Garlic'", "AdminManagementEctTest.createIngredientStatus", "BUG-04"),
    ("TC-16", "ECT", "createTag", "Create tag: padded name (ECT)", "name = '   Spicy Food   '; saved as 'Spicy Food'",
     _P + "POST /api/v1/admin/tags", "201, response name 'Spicy Food' (same as saved)", "AdminManagementEctTest.paddedTagName", "BUG-06"),
    ("TC-17", "ECT", "categories", "Ingredient categories order (ECT)", "catalogue returns Dairy, Grains, Spices",
     _P + "GET /api/v1/admin/ingredients/categories", "Same A-Z order: Dairy first, Spices last", "AdminManagementEctTest.categoriesOrder", "BUG-03"),
    ("TC-18", "ECT", "updateStatus", "Suspend user: response counts (ECT)", "user 7: recipes 4, reviews 1, cooks 9",
     _P + "PATCH /api/v1/admin/users/7/status {SUSPENDED}", "200; recipeCount 4, reviewCount 1, cookCount 9", "AdminManagementEctTest.suspendResponse", "BUG-08"),
    ("TC-19", "ECT", "updateStatus", "Change status: status missing (ECT)", "{\"reason\":\"x\"}",
     _P + "PATCH /api/v1/admin/users/7/status", "400 'Status is required.'; service not called", "AdminManagementEctTest.missingStatus", ""),
    ("TC-20", "ECT", "summary", "Dashboard: impossible date (ECT)", "from = 2026-02-30",
     _P + "GET /api/v1/admin/dashboard/summary?from=2026-02-30", "400; analytics not called", "AdminManagementEctTest.impossibleDate", ""),
    ("TC-21", "Basis path", "ingredients", "ingredients P1 (basis path)", "size = null",
     "1. Call ingredients('rice', null, null, 0, null)", "Catalogue called with size null", "AdminManagementBasisPathTest.ingredientsP1", ""),
    ("TC-22", "Basis path", "ingredients", "ingredients P2 (basis path)", "size = 20",
     "1. Call ingredients('rice', null, null, 0, 20)", "Catalogue called with size 20", "AdminManagementBasisPathTest.ingredientsP2", "BUG-02"),
    ("TC-23", "Basis path", "createIngredient", "createIngredient P1 (basis path)", "name = null (bypasses @Valid)",
     "1. Call createIngredient(admin, request)", "Length check skipped; catalogue called", "AdminManagementBasisPathTest.createIngredientP1", ""),
    ("TC-24", "Basis path", "createIngredient", "createIngredient P2 (basis path)", "name = 85 chars",
     "1. Call createIngredient(admin, request)", "ApiException 400; nothing saved", "AdminManagementBasisPathTest.createIngredientP2", ""),
    ("TC-25", "Basis path", "createIngredient", "createIngredient P3 (basis path)", "name = Garlic",
     "1. Call createIngredient(admin, request)", "201 Created with the saved ingredient", "AdminManagementBasisPathTest.createIngredientP3", "BUG-04"),
    ("TC-26", "Basis path", "summary", "summary P1 (basis path)", "from = 2026-10-01, to = null",
     "1. Call summary(from, null)", "Analytics called with (2026-10-01, null)", "AdminManagementBasisPathTest.summaryP1", ""),
    ("TC-27", "Basis path", "summary", "summary P2 (basis path)", "from = 2026-10-01, to = 2026-10-31",
     "1. Call summary(from, to)", "Analytics called with (2026-10-01, 2026-10-31)", "AdminManagementBasisPathTest.summaryP2", "BUG-09"),
    ("TC-28", "Basis path", "auditLogs", "auditLogs P1 (basis path)", "page = null, size = 20",
     "1. Call auditLogs(null, 20)", "Audit service asked for the first page (null or 0)", "AdminManagementBasisPathTest.auditLogsP1", "BUG-10"),
    ("TC-29", "Basis path", "auditLogs", "auditLogs P2 (basis path)", "page = 3, size = 20",
     "1. Call auditLogs(3, 20)", "Audit service asked for page 3", "AdminManagementBasisPathTest.auditLogsP2", "BUG-10"),
    ("TC-30", "Basis path", "updateStatus", "updateStatus P1 (basis path)", "SUSPENDED, 'Spam'; service row 4/1/9",
     "1. Call updateStatus(admin, 7, request)", "Exactly the service's row is returned", "AdminManagementBasisPathTest.updateStatusP1", "BUG-08"),
]

# ---------------------------------------------------------------- basis path: 5 functions
# node: (id, kind, source line(s), text)   kind: start | process | decision | return | error
# edge: (from, to, label)                  "End" is the exit node
FUNCTIONS = [
    {
        "name": "ingredients", "lines": "53-59", "predicates": ["size == null"],
        "nodes": [(1, "start", "53-57", "receive query, category, sort, page, size"),
                  (2, "decision", "58", "size == null ?"),
                  (3, "process", "58", "sizeArg = null"),
                  (4, "process", "58", "sizeArg = size - 1"),
                  (5, "return", "58", "return catalog.searchIngredients(query, null, true, sort, page, sizeArg)")],
        "edges": [(1, 2, ""), (2, 3, "T"), (2, 4, "F"), (3, 5, ""), (4, 5, ""), (5, "End", "")],
        "paths": [("P1", [1, 2, 3, 5], "size == null: T", "Catalogue called with size null", "TC-21"),
                  ("P2", [1, 2, 4, 5], "size == null: F", "Catalogue called with the requested size", "TC-22")],
    },
    {
        "name": "createIngredient", "lines": "67-73", "predicates": ["request.name() != null", "name.strip().length() >= 80"],
        "nodes": [(1, "start", "67-68", "receive admin, @Valid request"),
                  (2, "decision", "69", "request.name() != null ?"),
                  (3, "decision", "69", "name.strip().length() >= 80 ?"),
                  (4, "error", "70", "throw ApiException.validation(name)"),
                  (5, "return", "72", "return ResponseEntity.ok(catalog.createIngredient(request, admin.id()))")],
        "edges": [(1, 2, ""), (2, 3, "T"), (2, 5, "F"), (3, 4, "T"), (3, 5, "F"), (4, "End", ""), (5, "End", "")],
        "paths": [("P1", [1, 2, 5], "name != null: F", "Catalogue called (length check skipped)", "TC-23"),
                  ("P2", [1, 2, 3, 4], "name != null: T, length >= 80: T", "400 validation error, nothing saved", "TC-24"),
                  ("P3", [1, 2, 3, 5], "name != null: T, length >= 80: F", "Saved, 201 Created", "TC-25")],
    },
    {
        "name": "summary", "lines": "137-140", "predicates": ["to == null"],
        "nodes": [(1, "start", "137-138", "receive from, to"),
                  (2, "decision", "139", "to == null ?"),
                  (3, "process", "139", "toArg = null"),
                  (4, "process", "139", "toArg = to.minusDays(1)"),
                  (5, "return", "139", "return analytics.summary(from, toArg)")],
        "edges": [(1, 2, ""), (2, 3, "T"), (2, 4, "F"), (3, 5, ""), (4, 5, ""), (5, "End", "")],
        "paths": [("P1", [1, 2, 3, 5], "to == null: T", "Analytics called with (from, null)", "TC-26"),
                  ("P2", [1, 2, 4, 5], "to == null: F", "Analytics called with (from, to)", "TC-27")],
    },
    {
        "name": "auditLogs", "lines": "155-158", "predicates": ["page == null"],
        "nodes": [(1, "start", "155-156", "receive page, size"),
                  (2, "decision", "157", "page == null ?"),
                  (3, "process", "157", "pageArg = 1"),
                  (4, "process", "157", "pageArg = page + 1"),
                  (5, "return", "157", "return audit.list(pageArg, size)")],
        "edges": [(1, 2, ""), (2, 3, "T"), (2, 4, "F"), (3, 5, ""), (4, 5, ""), (5, "End", "")],
        "paths": [("P1", [1, 2, 3, 5], "page == null: T", "First page requested", "TC-28"),
                  ("P2", [1, 2, 4, 5], "page == null: F", "Requested page passed unchanged", "TC-29")],
    },
    {
        "name": "updateStatus", "lines": "119-124", "predicates": [],
        "nodes": [(1, "start", "119-120", "receive admin, userId, @Valid request"),
                  (2, "process", "121", "row = users.updateStatus(admin, userId, request)"),
                  (3, "return", "122-123", "return new AdminUserRow(..., row.reviewCount(), row.recipeCount(), ...)")],
        "edges": [(1, 2, ""), (2, 3, ""), (3, "End", "")],
        "paths": [("P1", [1, 2, 3], "(no decision)", "Service row returned unchanged", "TC-30")],
    },
]

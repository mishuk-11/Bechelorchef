"""Test design for ProfileController (owner: Cherry).

Read by ../generate_reports.py. Results (Pass/Fail, time, failure text) are NOT written here:
they come from the JUnit XML in evidence/ when the generator runs.
"""

MEMBER = "Cherry"
CONTROLLER = "ProfileController"
SHORT = "Profile"
SOURCE = "backend/src/main/java/com/bachelorchef/controller/ProfileController.java"
TEST_DIR = "backend/src/test/java/com/bachelorchef/stqa/cherry"
SUITE_PREFIX = "Cherry - ProfileController"
BVA_CLASS, ECT_CLASS, BP_CLASS = "ProfileBvaTest", "ProfileEctTest", "ProfileBasisPathTest"
LOGIN = "Signed in as USER (user1@user.com)"

BVA = [
    ("New password length", "changePassword (POST /users/me/password)", "8..72 chars (ChangePasswordRequest @Size)",
     "7", "8", "9", "12", "71", "72", "73", "7 and 73 rejected (400); 8..72 accepted", "TC-01..04"),
    ("Phone number length", "update (PATCH /users/me)", "<= 20 chars (UpdateProfileRequest @Size(max = 20))",
     "-", "0", "1", "14", "19", "20", "21", "21 rejected (400); up to 20 sent to the service", "TC-05, TC-06"),
    ("Full name length", "update (PATCH /users/me)", "2..100 chars (users.full_name VARCHAR(100))",
     "1", "2", "3", "20", "99", "100", "101", "Up to 100 accepted; longer rejected", "TC-07, TC-08, TC-22"),
    ("My submissions page index", "myRecipes (GET /users/me/recipes)", "page >= 0",
     "-1", "0", "1", "2", "-", "-", "-", "Requested page passed unchanged", "TC-09, TC-28"),
    ("Reviews total vs page size", "myReviews (GET /users/me/reviews)", "totalElements counts all pages",
     "-", "total = 0", "total = 1", "-", "total = size", "-", "total = size + 1", "totalElements = all reviews; totalPages = ceil(total / size)", "TC-10, TC-26"),
]

ECT = [
    ("Bio", "EC1", "Non-empty text", "Valid", "Student cook from Dhaka", "Sent to the service and saved", "TC-11"),
    ("Phone", "EC2", "Empty string (clear)", "Valid", "\"\"", "Sent as \"\" so the number is removed", "TC-12, TC-24"),
    ("Phone", "EC3", "Valid number", "Valid", "+8801712345678", "Sent unchanged", "TC-13, TC-25"),
    ("Phone", "EC4", "Too long", "Invalid", "21 digits", "400", "TC-06"),
    ("Submission status filter", "EC5", "Known status", "Valid", "APPROVED", "Only that status listed", "TC-14"),
    ("Submission status filter", "EC6", "No status", "Valid", "(none)", "All statuses listed", "TC-15"),
    ("Submission status filter", "EC7", "Unknown status", "Invalid", "PUBLISHED", "400; search not called", "TC-16"),
    ("Current password", "EC8", "Wrong password", "Invalid", "wrong-one", "400 fieldErrors.currentPassword", "TC-17"),
    ("Delete account", "EC9", "Last active admin", "Invalid", "only admin left", "409 LAST_ADMIN", "TC-18"),
    ("Delete account", "EC10", "Blank password", "Invalid", "'  '", "400 'Password is required.'", "TC-19"),
    ("My review state", "EC11", "Rejected with reason", "Valid", "REJECTED + 'Off topic'", "Reason shown to the author", "TC-20, TC-27"),
]

_P = "1. " + LOGIN + "  2. "
CASES = [
    ("TC-01", "BVA", "changePassword", "Change password: new length (BVA)", "newPassword = 7 chars",
     _P + "POST /api/v1/users/me/password", "400 fieldErrors.newPassword; not changed", "ProfileBvaTest.newPasswordLength(TC-01, 7, false)", ""),
    ("TC-02", "BVA", "changePassword", "Change password: new length (BVA)", "newPassword = 8 chars (min)",
     _P + "POST /api/v1/users/me/password", "Accepted (2xx); password changed", "ProfileBvaTest.newPasswordLength(TC-02, 8, true)", ""),
    ("TC-03", "BVA", "changePassword", "Change password: new length (BVA)", "newPassword = 72 chars (max)",
     _P + "POST /api/v1/users/me/password", "Accepted (2xx); password changed", "ProfileBvaTest.newPasswordLength(TC-03, 72, true)", ""),
    ("TC-04", "BVA", "changePassword", "Change password: new length (BVA)", "newPassword = 73 chars",
     _P + "POST /api/v1/users/me/password", "400 fieldErrors.newPassword; not changed", "ProfileBvaTest.newPasswordLength(TC-04, 73, false)", ""),
    ("TC-05", "BVA", "update", "Update profile: phone length (BVA)", "phoneNumber = 20 digits (max)",
     _P + "PATCH /api/v1/users/me", "200; phone sent to the service unchanged", "ProfileBvaTest.phoneLength(TC-05, 20, true)", ""),
    ("TC-06", "BVA", "update", "Update profile: phone length (BVA)", "phoneNumber = 21 digits",
     _P + "PATCH /api/v1/users/me", "400 fieldErrors.phoneNumber", "ProfileBvaTest.phoneLength(TC-06, 21, false)", ""),
    ("TC-07", "BVA", "update", "Update profile: full name length (BVA)", "fullName = 99 chars",
     _P + "PATCH /api/v1/users/me", "200; name sent to the service", "ProfileBvaTest.fullNameLength(TC-07, 99)", ""),
    ("TC-08", "BVA", "update", "Update profile: full name length (BVA)", "fullName = 100 chars (max)",
     _P + "PATCH /api/v1/users/me", "200; name sent to the service", "ProfileBvaTest.fullNameLength(TC-08, 100)", "BUG-13"),
    ("TC-09", "BVA", "myRecipes", "My submissions: second page (BVA)", "page = 1, size = 12",
     _P + "GET /api/v1/users/me/recipes?page=1&size=12", "Search asked for page 1", "ProfileBvaTest.secondPageOfSubmissions", "BUG-18"),
    ("TC-10", "BVA", "myReviews", "My reviews: totals across pages (BVA)", "3 reviews, size = 2",
     _P + "GET /api/v1/users/me/reviews?page=0&size=2", "2 items, totalElements 3, totalPages 2", "ProfileBvaTest.reviewTotalsAcrossPages", "BUG-19"),
    ("TC-11", "ECT", "update", "Update profile: bio (ECT)", "bio = 'Student cook from Dhaka'",
     _P + "PATCH /api/v1/users/me", "Bio sent to the service", "ProfileEctTest.bioSaved", "BUG-11"),
    ("TC-12", "ECT", "update", "Update profile: clear phone (ECT)", "phoneNumber = \"\"",
     _P + "PATCH /api/v1/users/me", "\"\" sent so the phone is removed", "ProfileEctTest.clearPhone", "BUG-12"),
    ("TC-13", "ECT", "update", "Update profile: valid phone (ECT)", "phoneNumber = +8801712345678",
     _P + "PATCH /api/v1/users/me", "Phone sent unchanged", "ProfileEctTest.validPhone", ""),
    ("TC-14", "ECT", "myRecipes", "My submissions: status filter (ECT)", "status = APPROVED",
     _P + "GET /api/v1/users/me/recipes?status=APPROVED", "Search asked for [APPROVED] of my recipes", "ProfileEctTest.statusFilterApplied", "BUG-16"),
    ("TC-15", "ECT", "myRecipes", "My submissions: no filter (ECT)", "(no status)",
     _P + "GET /api/v1/users/me/recipes", "Search asked for all statuses of my recipes", "ProfileEctTest.noStatusFilter", ""),
    ("TC-16", "ECT", "myRecipes", "My submissions: unknown status (ECT)", "status = PUBLISHED",
     _P + "GET /api/v1/users/me/recipes?status=PUBLISHED", "400; search not called", "ProfileEctTest.unknownStatus", ""),
    ("TC-17", "ECT", "changePassword", "Change password: wrong current (ECT)", "currentPassword = wrong-one",
     _P + "POST /api/v1/users/me/password", "400 fieldErrors.currentPassword", "ProfileEctTest.wrongCurrentPassword", ""),
    ("TC-18", "ECT", "delete", "Delete account: last admin (ECT)", "service reports LAST_ADMIN",
     _P + "DELETE /api/v1/users/me {password}", "409, code LAST_ADMIN", "ProfileEctTest.lastAdminCannotDelete", ""),
    ("TC-19", "ECT", "delete", "Delete account: blank password (ECT)", "password = '  '",
     _P + "DELETE /api/v1/users/me", "400 'Password is required.'; nothing deleted", "ProfileEctTest.blankDeletePassword", ""),
    ("TC-20", "ECT", "myReviews", "My reviews: rejected review (ECT)", "REJECTED, reason 'Off topic'",
     _P + "GET /api/v1/users/me/reviews", "Status REJECTED with reason 'Off topic'", "ProfileEctTest.rejectedReviewShowsReason", "BUG-20"),
    ("TC-21", "Basis path", "update", "update P1 (basis path)", "fullName = null, phone = null",
     "1. Call update(user, request)", "Service gets fullName null, phone null", "ProfileBasisPathTest.updateP1", ""),
    ("TC-22", "Basis path", "update", "update P2 (basis path)", "fullName = 120 chars",
     "1. Call update(user, request)", "ApiException 400; nothing saved", "ProfileBasisPathTest.updateP2", ""),
    ("TC-23", "Basis path", "update", "update P3 (basis path)", "fullName = 'Cherry', phone = null",
     "1. Call update(user, request)", "Service gets 'Cherry' and phone null", "ProfileBasisPathTest.updateP3", ""),
    ("TC-24", "Basis path", "update", "update P4 (basis path)", "phone = \"\"",
     "1. Call update(user, request)", "Service gets phone \"\" (clear)", "ProfileBasisPathTest.updateP4", "BUG-12"),
    ("TC-25", "Basis path", "update", "update P5 (basis path)", "phone = +8801712345678",
     "1. Call update(user, request)", "Service gets the phone unchanged", "ProfileBasisPathTest.updateP5", ""),
    ("TC-26", "Basis path", "myReviews", "myReviews P1 (basis path)", "no reviews",
     "1. Call myReviews(user, 0, 10)", "Empty items, totalElements 0", "ProfileBasisPathTest.myReviewsP1", ""),
    ("TC-27", "Basis path", "myReviews", "myReviews P2 (basis path)", "1 REJECTED review with reason",
     "1. Call myReviews(user, 0, 10)", "Same review returned, reason kept", "ProfileBasisPathTest.myReviewsP2", "BUG-20"),
    ("TC-28", "Basis path", "myRecipes", "myRecipes P1 (basis path)", "page = 2, size = 12",
     "1. Call myRecipes(user, null, 2, 12)", "Search: my recipes, sort 'newest', page 2", "ProfileBasisPathTest.myRecipesP1", "BUG-17, BUG-18"),
    ("TC-29", "Basis path", "changePassword", "changePassword P1 (basis path)", "valid request",
     "1. Call changePassword(user, request)", "Password changed; 204 No Content", "ProfileBasisPathTest.changePasswordP1", "BUG-14"),
    ("TC-30", "Basis path", "delete", "delete P1 (basis path)", "password = 123456",
     "1. Call delete(user, request)", "Deleted; 204 and Set-Cookie clears bc_refresh", "ProfileBasisPathTest.deleteP1", "BUG-15"),
]

FUNCTIONS = [
    {
        "name": "update", "lines": "53-60",
        "predicates": ["fullName != null", "fullName.strip().length() >= 100", "phoneNumber == null", "phoneNumber.isBlank()"],
        "nodes": [(1, "start", "53", "receive user, @Valid request"),
                  (2, "decision", "54", "request.fullName() != null ?"),
                  (3, "decision", "54", "fullName.strip().length() >= 100 ?"),
                  (4, "error", "55", "throw ApiException.validation(fullName)"),
                  (5, "decision", "57", "phoneNumber() == null ?"),
                  (6, "decision", "57", "phoneNumber().isBlank() ?"),
                  (7, "process", "57", "phone = null"),
                  (8, "process", "57", "phone = phoneNumber()"),
                  (9, "return", "58-59", "return users.updateProfile(id, new Request(fullName, phone, avatar, assetId, null))")],
        "edges": [(1, 2, ""), (2, 3, "T"), (2, 5, "F"), (3, 4, "T"), (3, 5, "F"), (4, "End", ""),
                  (5, 7, "T"), (5, 6, "F"), (6, 7, "T"), (6, 8, "F"), (7, 9, ""), (8, 9, ""), (9, "End", "")],
        "paths": [("P1", [1, 2, 5, 7, 9], "fullName: F; phone null: T", "Service gets (null, null)", "TC-21"),
                  ("P2", [1, 2, 3, 4], "fullName: T; length >= 100: T", "400 validation error", "TC-22"),
                  ("P3", [1, 2, 3, 5, 7, 9], "fullName: T; length: F; phone null: T", "Service gets ('Cherry', null)", "TC-23"),
                  ("P4", [1, 2, 5, 6, 7, 9], "fullName: F; phone null: F; blank: T", "Service gets phone \"\" (clear)", "TC-24"),
                  ("P5", [1, 2, 5, 6, 8, 9], "fullName: F; phone null: F; blank: F", "Service gets the phone", "TC-25")],
    },
    {
        "name": "myReviews", "lines": "90-99", "predicates": ["stream: another review to map?"],
        "nodes": [(1, "start", "90-92", "receive user, page, size"),
                  (2, "process", "93", "result = reviews.mine(user, page, size)"),
                  (3, "decision", "94", "another item in result.items() ?"),
                  (4, "process", "95-96", "map item to MyReviewView(..., moderationReason = null, ...)"),
                  (5, "process", "97", "items = toList()"),
                  (6, "return", "98", "return PageResponse.of(items, page, size, items.size())")],
        "edges": [(1, 2, ""), (2, 3, ""), (3, 4, "T"), (4, 3, ""), (3, 5, "F"), (5, 6, ""), (6, "End", "")],
        "paths": [("P1", [1, 2, 3, 5, 6], "loop: 0 times", "Empty page, total 0", "TC-26"),
                  ("P2", [1, 2, 3, 4, 3, 5, 6], "loop: 1+ times", "Reviews returned as the service gave them", "TC-27")],
    },
    {
        "name": "myRecipes", "lines": "79-86", "predicates": [],
        "nodes": [(1, "start", "79-82", "receive user, status, page, size"),
                  (2, "process", "83", "statuses = List.of()"),
                  (3, "return", "84-85", "return recipeQueries.search(filters(statuses, user.id()), \"title\", 0, size)")],
        "edges": [(1, 2, ""), (2, 3, ""), (3, "End", "")],
        "paths": [("P1", [1, 2, 3], "(no decision)", "My recipes, newest first, requested page", "TC-28")],
    },
    {
        "name": "changePassword", "lines": "64-68", "predicates": [],
        "nodes": [(1, "start", "64-65", "receive user, @Valid request"),
                  (2, "process", "66", "users.changePassword(user.id(), request)"),
                  (3, "return", "67", "return ResponseEntity.ok().build()")],
        "edges": [(1, 2, ""), (2, 3, ""), (3, "End", "")],
        "paths": [("P1", [1, 2, 3], "(no decision)", "Password changed, 204 No Content", "TC-29")],
    },
    {
        "name": "delete", "lines": "72-75", "predicates": [],
        "nodes": [(1, "start", "72", "receive user, @Valid request"),
                  (2, "process", "73", "users.deleteAccount(user.id(), request.password())"),
                  (3, "return", "74", "return ResponseEntity.noContent().build()")],
        "edges": [(1, 2, ""), (2, 3, ""), (3, "End", "")],
        "paths": [("P1", [1, 2, 3], "(no decision)", "204 and refresh cookie cleared", "TC-30")],
    },
]

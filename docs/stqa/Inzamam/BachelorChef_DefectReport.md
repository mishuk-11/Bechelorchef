# Bachelor Chef - defect report

Reported by **Inzamam**. 30 defects, all Open. Severity: Minor 23, Low 7.
Each defect lists the automated test cases that confirm it (team unit tests and Selenium).

| ID | Title | Module | Severity | Priority | Confirmed by |
|---|---|---|---|---|---|
| BUG-01 | Ingredient category filter is ignored | AdminManagementController | Minor | Medium | TC-11 (Inzamam), SEL-13 (Selenium) |
| BUG-02 | Ingredient page size is one less than requested | AdminManagementController | Minor | Medium | TC-06 (Inzamam), TC-07 (Inzamam), TC-22 (Inzamam) |
| BUG-03 | Ingredient category suggestions are in reverse (Z -> A) order | AdminManagementController | Low | Low | TC-17 (Inzamam) |
| BUG-04 | Creating an ingredient returns 200 OK instead of 201 Created | AdminManagementController | Low | Low | TC-15 (Inzamam), TC-25 (Inzamam) |
| BUG-05 | Ingredient name of exactly 80 characters is rejected | AdminManagementController | Minor | Medium | TC-04 (Inzamam) |
| BUG-06 | Create-tag response returns the untrimmed name | AdminManagementController | Low | Low | TC-16 (Inzamam) |
| BUG-07 | User list role filter is ignored | AdminManagementController | Minor | Medium | TC-13 (Inzamam), SEL-12 (Selenium) |
| BUG-08 | Status-change response swaps the Recipes and Reviews counts | AdminManagementController | Low | Low | TC-18 (Inzamam), TC-30 (Inzamam) |
| BUG-09 | Dashboard "to" date is excluded from the range | AdminManagementController | Minor | Medium | TC-09 (Inzamam), TC-27 (Inzamam) |
| BUG-10 | Audit log first page skips the newest entries | AdminManagementController | Minor | Medium | TC-08 (Inzamam), TC-28 (Inzamam), TC-29 (Inzamam) |
| BUG-11 | Bio changes are not saved | ProfileController | Minor | Medium | TC-11 (Cherry), SEL-14 (Selenium) |
| BUG-12 | Phone number cannot be cleared | ProfileController | Minor | Medium | TC-12 (Cherry), TC-24 (Cherry) |
| BUG-13 | Full name of exactly 100 characters is rejected | ProfileController | Minor | Medium | TC-08 (Cherry) |
| BUG-14 | Change password returns 200 OK instead of 204 No Content | ProfileController | Low | Low | TC-29 (Cherry) |
| BUG-15 | Deleting the account does not clear the refresh cookie | ProfileController | Low | Low | TC-30 (Cherry) |
| BUG-16 | "My submissions" status filter is ignored | ProfileController | Minor | Medium | TC-14 (Cherry) |
| BUG-17 | "My submissions" are sorted by title instead of newest first | ProfileController | Minor | Low | TC-28 (Cherry) |
| BUG-18 | "My submissions" paging always returns the first page | ProfileController | Minor | Medium | TC-09 (Cherry), TC-28 (Cherry) |
| BUG-19 | "My reviews" totals only count the current page | ProfileController | Minor | Medium | TC-10 (Cherry) |
| BUG-20 | Moderator's rejection reason is not shown to the author | ProfileController | Minor | Medium | TC-20 (Cherry), TC-27 (Cherry) |
| BUG-21 | Saving a recipe returns 201 Created instead of 204 No Content | PersonalController | Low | Low | TC-11 (Saima) |
| BUG-22 | Cookbook page size is capped at 10 | PersonalController | Minor | Medium | TC-09 (Saima), TC-20 (Saima) |
| BUG-23 | "Different recipes" equals total cooks | PersonalController | Minor | Medium | TC-18 (Saima), TC-30 (Saima) |
| BUG-24 | Cooking history is oldest-first within a page | PersonalController | Minor | Low | TC-30 (Saima) |
| BUG-25 | Editing the date of a cook event is not saved | PersonalController | Minor | Medium | TC-17 (Saima), TC-23 (Saima) |
| BUG-26 | Notes of exactly 1000 characters are rejected | PersonalController | Minor | Medium | TC-07 (Saima) |
| BUG-27 | Completed goals disappear from the goals list | PersonalController | Minor | Medium | TC-16 (Saima), TC-26 (Saima), SEL-01 (Selenium) |
| BUG-28 | Goal target of 30 is rejected | PersonalController | Minor | Medium | TC-04 (Saima), SEL-15 (Selenium) |
| BUG-29 | "Only recipes up to N minutes" is ignored when creating a goal | PersonalController | Minor | Medium | TC-15 (Saima), TC-29 (Saima), SEL-16 (Selenium) |
| BUG-30 | Changing a goal's count mode is ignored | PersonalController | Minor | Low | TC-14 (Saima) |

## BUG-01 - Ingredient category filter is ignored

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Minor / Medium  
- **Location:** `AdminManagementController.java:58  (ingredients)`  
- **Confirmed by:** TC-11 (Inzamam), SEL-13 (Selenium)

**Steps to reproduce**

```
1. Log in as admin -> /admin/ingredients
2. Choose a category, e.g. "Spices" (or call ?category=Spices)
```

**Expected:** Only ingredients of that category are listed.  
**Actual:** All categories are listed; the category is never passed on.  
**Root cause:** catalog.searchIngredients(query, null, ...) instead of category.

## BUG-02 - Ingredient page size is one less than requested

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Minor / Medium  
- **Location:** `AdminManagementController.java:58`  
- **Confirmed by:** TC-06 (Inzamam), TC-07 (Inzamam), TC-22 (Inzamam)

**Steps to reproduce**

```
GET /api/v1/admin/ingredients?size=20 (the UI asks for 20)
Also try size=1 and size=100.
```

**Expected:** 20 rows, "size":20; size=1 returns 1 row; size=100 returns 100.  
**Actual:** 19 rows, "size":19; size=1 -> 400 "size must be between 1 and
100"; size=101 is accepted (becomes 100).  
**Root cause:** passes size - 1 to the service.

## BUG-03 - Ingredient category suggestions are in reverse (Z -> A) order

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Low / Low  
- **Location:** `AdminManagementController.java:63  (categories)`  
- **Confirmed by:** TC-17 (Inzamam)

**Steps to reproduce**

```
/admin/ingredients -> Add ingredient -> open the Category list,
or GET /api/v1/admin/ingredients/categories
```

**Expected:** Alphabetical A -> Z (Condiments, Dairy, Fruit, ...).  
**Actual:** Reverse order (Vegetables, Spices, Protein, ...).  
**Root cause:** .reversed() on the sorted list.

## BUG-04 - Creating an ingredient returns 200 OK instead of 201 Created

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Low / Low  
- **Location:** `AdminManagementController.java:72  (createIngredient)`  
- **Confirmed by:** TC-15 (Inzamam), TC-25 (Inzamam)

**Steps to reproduce**

```
POST /api/v1/admin/ingredients {"name":"Test Leaf"}
```

**Expected:** HTTP 201 Created (AGENTS.md section 7).  
**Actual:** HTTP 200 OK.  
**Root cause:** ResponseEntity.ok(...) instead of status(HttpStatus.CREATED).

## BUG-05 - Ingredient name of exactly 80 characters is rejected

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Minor / Medium  
- **Location:** `AdminManagementController.java:69`  
- **Confirmed by:** TC-04 (Inzamam)

**Steps to reproduce**

```
Add ingredient with a name of 79, 80 and 81 characters.
```

**Expected:** 79 and 80 accepted, 81 rejected ("Must be at most 80 characters").  
**Actual:** 80 is rejected with "Must be at most 80 characters." (off by one).  
**Root cause:** check uses ">= 80" instead of "> 80".

## BUG-06 - Create-tag response returns the untrimmed name

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Low / Low  
- **Location:** `AdminManagementController.java:96  (createTag)`  
- **Confirmed by:** TC-16 (Inzamam)

**Steps to reproduce**

```
POST /api/v1/admin/tags {"name":"   Spicy Food   "}
then GET /api/v1/tags
```

**Expected:** Response name "Spicy Food" (same as stored).  
**Actual:** Response name "   Spicy Food   "; GET /tags shows "Spicy Food".
Response does not match the saved data.  
**Root cause:** builds the response from request.name() instead of the saved tag.

## BUG-07 - User list role filter is ignored

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Minor / Medium  
- **Location:** `AdminManagementController.java:114  (users)`  
- **Confirmed by:** TC-13 (Inzamam), SEL-12 (Selenium)

**Steps to reproduce**

```
/admin/users -> Role filter = "Admin"
```

**Expected:** Only the admins (inzamam, cherry, saima, ...).  
**Actual:** All users, including user1..user10.  
**Root cause:** role is replaced by null when calling the service.

## BUG-08 - Status-change response swaps the Recipes and Reviews counts

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Low / Low  
- **Location:** `AdminManagementController.java:121-123  (updateStatus)`  
- **Confirmed by:** TC-18 (Inzamam), TC-30 (Inzamam)

**Steps to reproduce**

```
Pick a user who has recipes but no reviews (or the reverse).
PATCH /api/v1/admin/users/{id}/status {"status":"SUSPENDED"}
Compare with GET /api/v1/admin/users for the same user.
(Reactivate the user afterwards with {"status":"ACTIVE"}.)
```

**Expected:** recipeCount / reviewCount equal to the list values.  
**Actual:** recipeCount and reviewCount are swapped in the PATCH response.  
**Root cause:** AdminUserRow built with reviewCount and recipeCount swapped.

## BUG-09 - Dashboard "to" date is excluded from the range

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Minor / Medium  
- **Location:** `AdminManagementController.java:139  (summary)`  
- **Confirmed by:** TC-09 (Inzamam), TC-27 (Inzamam)

**Steps to reproduce**

```
1. As user1, click "I cooked this" on any recipe (today).
2. As admin, /admin -> set From = today, To = today.
```

**Expected:** Range is inclusive; today's cook event is counted, "to" = today.  
**Actual:** Response "to" is yesterday; today's events are missing (or a
400 "from must be before to" because to < from).  
**Root cause:** passes to.minusDays(1).

## BUG-10 - Audit log first page skips the newest entries

- **Module:** AdminManagementController (tested by Inzamam)  
- **Severity / priority:** Minor / Medium  
- **Location:** `AdminManagementController.java:157  (auditLogs)`  
- **Confirmed by:** TC-08 (Inzamam), TC-28 (Inzamam), TC-29 (Inzamam)

**Steps to reproduce**

```
GET /api/v1/admin/audit-logs?page=0&size=5 (do a few admin
actions first, e.g. suspend/reactivate a user).
```

**Expected:** "page":0 with the 5 newest entries.  
**Actual:** "page":1, entries 6-10; the newest entries are never shown on
the first page. Last page returns an empty list.  
**Root cause:** page + 1 (and default page 1 instead of 0).

## BUG-11 - Bio changes are not saved

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Minor / Medium  
- **Location:** `ProfileController.java:58-59  (update)`  
- **Confirmed by:** TC-11 (Cherry), SEL-14 (Selenium)

**Steps to reproduce**

```
/profile -> type a Bio -> Save -> reload the page.
```

**Expected:** Bio is saved and shown after reload.  
**Actual:** "Saved" feedback, but the bio is empty/unchanged after reload.  
**Root cause:** bio is replaced by null (null means "unchanged").

## BUG-12 - Phone number cannot be cleared

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Minor / Medium  
- **Location:** `ProfileController.java:57`  
- **Confirmed by:** TC-12 (Cherry), TC-24 (Cherry)

**Steps to reproduce**

```
/profile -> set phone +8801712345678 -> Save -> clear the phone
field -> Save -> reload.
```

**Expected:** Phone is removed (empty string clears optional fields).  
**Actual:** The old phone number is still there.  
**Root cause:** "" is converted to null, which the service treats as "unchanged".

## BUG-13 - Full name of exactly 100 characters is rejected

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Minor / Medium  
- **Location:** `ProfileController.java:54`  
- **Confirmed by:** TC-08 (Cherry)

**Steps to reproduce**

```
/profile -> Full name with 99, 100, 101 characters -> Save.
```

**Expected:** 2..100 accepted, 101 rejected.  
**Actual:** 100 is rejected with "Must be 2 to 100 characters."  
**Root cause:** ">= 100" instead of "> 100".

## BUG-14 - Change password returns 200 OK instead of 204 No Content

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Low / Low  
- **Location:** `ProfileController.java:67  (changePassword)`  
- **Confirmed by:** TC-29 (Cherry)

**Steps to reproduce**

```
POST /api/v1/users/me/password
{"currentPassword":"123456","newPassword":"12345678"}
(then change it back to 123456 with the same endpoint)
```

**Expected:** HTTP 204 No Content.  
**Actual:** HTTP 200 OK with an empty body.  
**Root cause:** ResponseEntity.ok() instead of noContent().

## BUG-15 - Deleting the account does not clear the refresh cookie

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Low / Low  
- **Location:** `ProfileController.java:74  (delete)`  
- **Confirmed by:** TC-30 (Cherry)

**Steps to reproduce**

```
Register a throw-away account -> /profile -> Delete account
-> DevTools > Network: look at the DELETE /users/me response
headers; DevTools > Application > Cookies.
Note: Do NOT use the inzamam/cherry/saima/userN accounts for this test.
```

**Expected:** Set-Cookie header that expires the refresh-token cookie.  
**Actual:** No Set-Cookie header; the dead cookie stays in the browser
(the token itself is revoked on the server, so no access is gained).  
**Root cause:** Set-Cookie header removed from the response.

## BUG-16 - "My submissions" status filter is ignored

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Minor / Medium  
- **Location:** `ProfileController.java:83  (myRecipes)`  
- **Confirmed by:** TC-14 (Cherry)

**Steps to reproduce**

```
As user1 submit a recipe (it becomes PENDING) -> /profile ->
filter submissions by "Approved" (or GET /users/me/recipes?status=APPROVED).
```

**Expected:** Only recipes with the chosen status.  
**Actual:** Recipes of every status are listed.  
**Root cause:** statuses list is always empty.

## BUG-17 - "My submissions" are sorted by title instead of newest first

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Minor / Low  
- **Location:** `ProfileController.java:85`  
- **Confirmed by:** TC-28 (Cherry)

**Steps to reproduce**

```
Submit two recipes: first "Apple Oats", then "Zesty Toast".
Open /profile.
```

**Expected:** Newest first: "Zesty Toast" (newer) above "Apple Oats".  
**Actual:** Alphabetical by title regardless of submission date.  
**Root cause:** sort "title" instead of "newest".

## BUG-18 - "My submissions" paging always returns the first page

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Minor / Medium  
- **Location:** `ProfileController.java:85`  
- **Confirmed by:** TC-09 (Cherry), TC-28 (Cherry)

**Steps to reproduce**

```
Need > 12 submitted recipes (or call ?page=1&size=1 with 2 recipes).
```

**Expected:** page=1 returns the second page.  
**Actual:** Every page returns the same first-page items and "page":0.  
**Root cause:** page is hard-coded to 0.

## BUG-19 - "My reviews" totals only count the current page

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Minor / Medium  
- **Location:** `ProfileController.java:98  (myReviews)`  
- **Confirmed by:** TC-10 (Cherry)

**Steps to reproduce**

```
Write reviews on 3 recipes as user1.
GET /api/v1/users/me/reviews?page=0&size=2
```

**Expected:** "totalElements":3, "totalPages":2.  
**Actual:** "totalElements":2, "totalPages":1 -> page 2 can never be reached.  
**Root cause:** totalElements = items.size().

## BUG-20 - Moderator's rejection reason is not shown to the author

- **Module:** ProfileController (tested by Cherry)  
- **Severity / priority:** Minor / Medium  
- **Location:** `ProfileController.java:96`  
- **Confirmed by:** TC-20 (Cherry), TC-27 (Cherry)

**Steps to reproduce**

```
user1 writes a review -> admin rejects it in /admin/moderation
with reason "Off topic" -> user1 opens /profile (My reviews).
```

**Expected:** Status REJECTED with "- Off topic".  
**Actual:** Status REJECTED with no reason ("moderationReason": null).
(The recipe page still shows the reason - inconsistent.)  
**Root cause:** moderationReason mapped to null.

## BUG-21 - Saving a recipe returns 201 Created instead of 204 No Content

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Low / Low  
- **Location:** `PersonalController.java:55  (save)`  
- **Confirmed by:** TC-11 (Saima)

**Steps to reproduce**

```
PUT /api/v1/users/me/saved-recipes/1 twice.
```

**Expected:** 204 both times (idempotent PUT, nothing new is created the 2nd time).  
**Actual:** 201 Created every time.  
**Root cause:** status(HttpStatus.CREATED) instead of noContent().

## BUG-22 - Cookbook page size is capped at 10

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Minor / Medium  
- **Location:** `PersonalController.java:48  (savedRecipes)`  
- **Confirmed by:** TC-09 (Saima), TC-20 (Saima)

**Steps to reproduce**

```
As user1 save 12+ recipes -> /cookbook
(or GET /users/me/saved-recipes?size=12).
```

**Expected:** 12 recipes on the first page, "size":12.  
**Actual:** Only 10, "size":10, page count is computed from 10.  
**Root cause:** Math.min(size, 10).

## BUG-23 - "Different recipes" equals total cooks

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Minor / Medium  
- **Location:** `PersonalController.java:70-71  (history)`  
- **Confirmed by:** TC-18 (Saima), TC-30 (Saima)

**Steps to reproduce**

```
As user1 click "I cooked this" twice on the SAME recipe ->
/cooking-history.
```

**Expected:** Total cooks = 2, Different recipes = 1.  
**Actual:** Total cooks = 2, Different recipes = 2.  
**Root cause:** distinctRecipes filled with totalCookEvents.

## BUG-24 - Cooking history is oldest-first within a page

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Minor / Low  
- **Location:** `PersonalController.java:70`  
- **Confirmed by:** TC-30 (Saima)

**Steps to reproduce**

```
Log 3 cooks at different times -> /cooking-history.
```

**Expected:** Newest first.  
**Actual:** Oldest first on each page (pages themselves still go newest
-> oldest, so the order jumps between pages).  
**Root cause:** items list .reversed().

## BUG-25 - Editing the date of a cook event is not saved

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Minor / Medium  
- **Location:** `PersonalController.java:80  (updateHistory)`  
- **Confirmed by:** TC-17 (Saima), TC-23 (Saima)

**Steps to reproduce**

```
/cooking-history -> Edit an entry -> change the date/time to
yesterday -> Save -> reload.
```

**Expected:** New date is shown; goal progress for the month updates.  
**Actual:** Notes change is saved, date stays the same.  
**Root cause:** CookRequest rebuilt with cookedOn = null.

## BUG-26 - Notes of exactly 1000 characters are rejected

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Minor / Medium  
- **Location:** `PersonalController.java:77`  
- **Confirmed by:** TC-07 (Saima)

**Steps to reproduce**

```
Edit a history entry; paste notes of 999, 1000, 1001 characters.
```

**Expected:** 999 and 1000 accepted, 1001 rejected.  
**Actual:** 1000 rejected with "Must be at most 1000 characters."  
**Root cause:** ">= 1000" instead of "> 1000".

## BUG-27 - Completed goals disappear from the goals list

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Minor / Medium  
- **Location:** `PersonalController.java:91  (goals)`  
- **Confirmed by:** TC-16 (Saima), TC-26 (Saima), SEL-01 (Selenium)

**Steps to reproduce**

```
/goals -> create goal target 1 for this month -> cook any recipe
-> /goals.
Note: The Selenium UserJourneyTest (goal reaches 1/1) detects this bug.
```

**Expected:** Goal shown as Completed (1/1, 100%).  
**Actual:** The goal is no longer listed at all.  
**Root cause:** list filtered with !completed().

## BUG-28 - Goal target of 30 is rejected

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Minor / Medium  
- **Location:** `PersonalController.java:99  (createGoal)`  
- **Confirmed by:** TC-04 (Saima), SEL-15 (Selenium)

**Steps to reproduce**

```
/goals -> create a goal with target 0, 1, 30, 31.
```

**Expected:** 0 and 31 rejected, 1 and 30 accepted.  
**Actual:** 30 is rejected with "Must be between 1 and 30." (29 still works).  
**Root cause:** ">= 30" check in the controller.

## BUG-29 - "Only recipes up to N minutes" is ignored when creating a goal

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Minor / Medium  
- **Location:** `PersonalController.java:103`  
- **Confirmed by:** TC-15 (Saima), TC-29 (Saima), SEL-16 (Selenium)

**Steps to reproduce**

```
/goals -> create goal with "Only recipes up to" = 15.
```

**Expected:** Goal shows "<= 15 min" and only counts recipes <= 15 min.  
**Actual:** No time limit is saved; slower recipes also count.  
**Root cause:** maxTotalTimeMins replaced by null.

## BUG-30 - Changing a goal's count mode is ignored

- **Module:** PersonalController (tested by Saima)  
- **Severity / priority:** Minor / Low  
- **Location:** `PersonalController.java:111  (updateGoal)`  
- **Confirmed by:** TC-14 (Saima)

**Steps to reproduce**

```
Create a goal (UNIQUE_RECIPES). PATCH /api/v1/users/me/goals/{id}
{"countMode":"COOK_EVENTS"}
```

**Expected:** countMode becomes COOK_EVENTS and progress is recalculated.  
**Actual:** 200 OK but countMode stays UNIQUE_RECIPES.  
**Root cause:** countMode replaced by null (null means "unchanged").

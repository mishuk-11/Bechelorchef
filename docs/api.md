# API reference

Base path: `/api/v1`. Interactive OpenAPI docs: `GET /swagger-ui.html` (spec at `/v3/api-docs`).
Authenticate with `POST /auth/login`, then send `Authorization: Bearer <accessToken>`. The refresh token is an HttpOnly cookie handled by the browser.

## Conventions

- JSON only (except the import upload). Unknown JSON fields are ignored; server-controlled fields (status, official flag, total time, approval, role…) are never read from requests.
- Numbers are strict: `3.5` or `"5"` for an integer field is a `400`.
- Pagination: `page` (0-based, ≥ 0) and `size` (1–100). Responses: `{ items, page, size, totalElements, totalPages }`.
- Timestamps are UTC ISO-8601 instants. Date-range parameters are local dates in Asia/Dhaka.

### Error format

```json
{ "timestamp": "2026-10-13T08:00:00Z", "status": 400, "code": "VALIDATION_ERROR",
  "message": "Please correct the highlighted fields.",
  "fieldErrors": { "rating": "Must be an integer between 1 and 5." }, "traceId": "…" }
```

| Status | Typical codes |
|---|---|
| 400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, import file codes (`EMPTY_FILE`, `TOO_MANY_ROWS`, `MALFORMED_FILE`, `MISSING_COLUMNS`, `UNKNOWN_COLUMNS`, `INVALID_ROOT`, `FORMAT_MISMATCH`) |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS` |
| 403 | `FORBIDDEN`, `ACCOUNT_SUSPENDED` |
| 404 | `NOT_FOUND` (also used for other users' private/unpublished items) |
| 409 | `CONFLICT`, `EMAIL_TAKEN`, `REVIEW_EXISTS`, `INVALID_STATUS`, `RECIPE_ARCHIVED`, `ALREADY_REPORTED`, `LAST_ADMIN`, `IMPORT_IN_PROGRESS`, `PREVIEW_EXPIRED`, `CONCURRENT_UPDATE` |
| 413 | `PAYLOAD_TOO_LARGE` |
| 415 | `UNSUPPORTED_FILE_TYPE`, `UNSUPPORTED_MEDIA_TYPE` |
| 429 | `RATE_LIMITED` |
| 503 | `MEDIA_NOT_CONFIGURED` |

## Endpoints

Legend: 🌐 public · 👤 signed in · 🛡 admin

### Auth & profile

| Method | Path | | Notes |
|---|---|---|---|
| POST | `/auth/register` | 🌐 | `{fullName,email,password}` → 201 `{accessToken, accessTokenExpiresAt, user}` + refresh cookie. Role is always USER. |
| POST | `/auth/login` | 🌐 | Generic `INVALID_CREDENTIALS` for unknown email or wrong password; `ACCOUNT_SUSPENDED` (403) only with the right password. |
| POST | `/auth/refresh` | 🌐 cookie | Rotates the cookie, returns a new access token. Origin must be allowed. |
| POST | `/auth/logout` | 🌐 cookie | Revokes the refresh token, clears the cookie (204). |
| GET | `/auth/me` | 👤 | Profile (never includes credentials). |
| PATCH | `/users/me` | 👤 | `fullName, phoneNumber, avatarUrl, avatarAssetId, bio` (null = unchanged, "" clears optional). |
| POST | `/users/me/password` | 👤 | `{currentPassword,newPassword}`; revokes other sessions. |
| DELETE | `/users/me` | 👤 | `{password}`; anonymises the account (see architecture). |
| GET | `/users/me/recipes?status=` | 👤 | Own recipes in every status. |
| GET | `/users/me/reviews` | 👤 | Own reviews with moderation status/reason. |
| POST | `/users/me/avatar-upload-signature` | 👤 | Cloudinary signature for an avatar. |

### Discovery & recipes

| Method | Path | | Notes |
|---|---|---|---|
| GET | `/recipes` | 🌐 | Approved only. `q, ingredientId, tagId, difficulty (EASY/MEDIUM/HARD), source (official/community), maxCostBdt, maxTotalTimeMins (1–60), sort (newest, time, cost, rating, popular, title), page, size`. |
| GET | `/recipes/{id}` | 🌐 | Approved recipes for everyone; authors and admins also see their unpublished ones (others get 404). Includes `viewer {canEdit,isOwner,saved,myReviewId}`. |
| GET | `/recipes/{id}/portions?servings=1..20` | 🌐 | Scaled quantities, `estimatedTotalCostBdt = cost × n / base_servings` (2 dp), `costPerServingBdt`. |
| POST | `/recipes/match` | 🌐 | `{ingredientIds[1..50], maxTotalTimeMins=20 (1–60), strict=false, difficulty?, tagIds?, maxCostBdt?, page, size≤50}` → ranked results with match %, matched/missing/optional ingredients and a disclaimer. |
| POST | `/recipes` | 👤 | Community submission. `submit` true (default) → `PENDING`, false → `DRAFT`. |
| PATCH | `/recipes/{id}` | 👤 owner / 🛡 | Full editable content. See status rules below. |
| DELETE | `/recipes/{id}` | 👤 owner / 🛡 | Withdraw/archive (status `ARCHIVED`); never a hard delete. |
| POST | `/recipes/{id}/cook` | 👤 | `{cookedOn?, notes?}` → history entry (201). `cookedOn` may not be in the future or > 1 year ago. |
| GET | `/recipes/{id}/reviews` | 🌐 | Approved reviews only. |
| POST | `/recipes/{id}/reviews` | 👤 | `{rating 1–5, comment ≤2000, photoUrl?}` → 201 `PENDING`. One per user; not on your own recipe; only published recipes. |
| PATCH / DELETE | `/reviews/{id}` | 👤 owner | Edit (back to `PENDING`) or delete. |
| POST | `/reports` | 👤 | `{targetType RECIPE|REVIEW, targetId, reason}`; one open report per user and target. |
| GET | `/ingredients?query=&category=` | 🌐 | Active ingredients for autocomplete. |
| GET | `/tags` | 🌐 | All tags. |
| GET | `/media/config` · POST `/media/upload-signature` | 🌐 · 👤 | Upload availability, allowed hosts; signed upload parameters (`purpose`: RECIPE, STEP, AVATAR, REVIEW). |

### Personal cooking

| Method | Path | Notes |
|---|---|---|
| GET | `/users/me/saved-recipes` | Paginated cookbook. |
| PUT / DELETE | `/users/me/saved-recipes/{recipeId}` | Save (idempotent) / remove. |
| GET | `/users/me/cooked-history` | `{page, totalCookEvents, distinctRecipes}`. |
| PATCH / DELETE | `/users/me/cooked-history/{id}` | Own entries only; goal completion is refreshed. |
| GET / POST | `/users/me/goals` | `{title 3–120, targetCount 1–30, month YYYY-MM (current … +12), countMode UNIQUE_RECIPES|COOK_EVENTS, tagId?, maxTotalTimeMins?}`. Responses include `progress, percent, completed, completedAt`. |
| PATCH / DELETE | `/users/me/goals/{id}` | Partial update / delete. |

### Admin (🛡)

| Method | Path | Notes |
|---|---|---|
| GET | `/admin/dashboard/summary?from=&to=` | Metrics below. |
| GET | `/admin/analytics/recipes`, `/admin/analytics/ingredients` | Top rated, most cooked, daily activity; popular ingredients. |
| GET | `/admin/audit-logs` | Sensitive admin actions. |
| GET / POST | `/admin/recipes` | All statuses (`status, q, source, sort`); create official (published unless `submit=false`). |
| POST | `/admin/recipes/{id}/approve` · `/reject` · `/archive` · `/restore` | `{reason}` (required for reject). |
| GET | `/admin/moderation/recipes` · `/reviews?status=PENDING|FLAGGED|APPROVED|REJECTED|REMOVED` · `/reports?status=OPEN|DISMISSED|ACTIONED` | Queues. |
| POST | `/admin/moderation/reviews/{id}/approve|reject|remove` | Reason required for reject/remove. Admins cannot moderate their own content. |
| POST | `/admin/moderation/reports/{id}/resolve` | `{action DISMISS|REMOVE_CONTENT, note}`; resolves all open reports on that target. |
| GET / POST / PATCH | `/admin/ingredients`, `/admin/ingredients/{id}` | Includes inactive; unique normalised names (409 on duplicates). |
| POST | `/admin/ingredients/{id}/activate` · `/deactivate` | Inactive ingredients stay on existing recipes but cannot be newly added. |
| GET | `/admin/ingredients/categories` | |
| POST / PATCH | `/admin/tags`, `/admin/tags/{id}` | |
| GET | `/admin/users?q=&status=&role=` | With recipe/review/cook counts; no credentials. |
| PATCH | `/admin/users/{id}/status` · `/role` | Not on yourself; last active admin cannot be suspended. Audited. |
| POST | `/admin/imports/preview` | multipart `file`; validates, stores a `PREVIEWED` job, writes no recipes. |
| POST | `/admin/imports` | `{jobId}`; imports valid rows; idempotent. |
| GET | `/admin/imports`, `/admin/imports/{id}`, `/admin/imports/{id}/errors.csv` | Job history and row issues. |

### Dashboard metric definitions

| Field | Definition |
|---|---|
| `activeUsers` | Accounts with status ACTIVE now |
| `newUsers` | Accounts created in [from, to] |
| `activeCooks` | Distinct users with ≥ 1 cook event in range |
| `approvedRecipes`, `pendingRecipes`, `pendingReviews`, `openReports` | Current counts |
| `cookEvents` | Cook history rows with `cooked_on` in range |
| `averageRating`, `approvedReviewCount` | Over approved reviews of approved recipes (current) |
| `popularIngredients` | Required ingredients of recipes cooked in range, by cook events |
| `topRatedRecipes` | Approved recipes with ≥ 1 approved review, by average then count |
| `mostCookedRecipes` | By cook events in range |

## Status transitions

**Recipes** (`DRAFT → PENDING → APPROVED/REJECTED`, any → `ARCHIVED`)

| Actor / action | From | To |
|---|---|---|
| User creates (`submit` default true) | — | `PENDING` (`DRAFT` if `submit=false`) |
| Owner edits | DRAFT / PENDING / REJECTED | `PENDING` if `submit`, else `DRAFT` |
| Owner edits with real content change | APPROVED | `PENDING` (approval cleared) |
| Owner edits without change | APPROVED | `APPROVED` |
| Owner edits | ARCHIVED | 409 `RECIPE_ARCHIVED` |
| Admin approve | PENDING / DRAFT / REJECTED | `APPROVED` |
| Admin reject (reason) | PENDING | `REJECTED` |
| Owner withdraw / admin archive | any but ARCHIVED | `ARCHIVED` |
| Admin restore | ARCHIVED | `APPROVED` if it was approved before, else `PENDING` |
| Admin creates official | — | `APPROVED` (`DRAFT` if `submit=false`) |

**Reviews**: create/edit → `PENDING`; admin → `APPROVED` / `REJECTED` / `REMOVED`. Aggregates count `APPROVED` only.

## Bulk import formats

Limits: `.csv` or `.json`, ≤ 5 MB, 1–500 recipes. Imported recipes are official, approved, and created by the importing admin. `total_time_mins` is always prep + cook.
Duplicate policy: a title (normalised) that already exists as a non-archived official recipe is **skipped**; the same title twice in one file is an error on the second row.

### CSV

Required header columns: `title,prep_time_mins,cook_time_mins,difficulty_level,estimated_cost_bdt,ingredients_csv,instructions_pipe`.
Optional: `description,base_servings` (default 1)`,image_url,tags_pipe`. Unknown columns reject the file.

```csv
title,prep_time_mins,cook_time_mins,difficulty_level,estimated_cost_bdt,ingredients_csv,instructions_pipe
Quick Egg Noodles,3,10,EASY,60.00,Noodles:1:pack|Egg:1:pcs|Green Chili:2:pcs,Boil noodles for 3 mins|Scramble egg in a pan|Mix noodles with spices
"Chili, Garlic Toast",5,6,EASY,40.00,Bread:2:slice|Egg:1:pcs|Chili Powder:0.25:tsp:optional,Toast bread|Fry egg|Assemble
```

- `ingredients_csv`: `Name:quantity:unit` or `Name:quantity:unit:optional`, separated by `|`.
- `instructions_pipe`: steps separated by `|`.
- Literal pipe or colon inside those fields: `\|` and `\:` (backslash: `\\`).
- Fields with commas must be quoted (RFC 4180; parsed with Apache Commons CSV).
- Row numbers exclude the header; line numbers are physical lines (where the record ends).

### JSON

A top-level array of objects with keys `title, description, prep_time_mins, cook_time_mins, difficulty_level, estimated_cost_bdt, base_servings, image_url, tags[], ingredients[{name, quantity, unit, is_optional}], steps[{step_number, instruction_text, image_url}]`.
Unknown keys are row errors; `is_official`, `status`, `created_by`, `approved_by`, … are rejected; `total_time_mins` is ignored with a warning. Steps are ordered by `step_number` (all or none must have one) and renumbered 1..n.

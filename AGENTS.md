# Bachelor Chef ERP — Coding Agent Instructions

> **Purpose:** This file is the implementation contract for an AI coding agent. Build a working, maintainable end-to-end application from these instructions. Do not stop after writing a plan, mockups, or placeholder endpoints. Inspect the existing repository first, implement the system in small verified stages, run the available tests/builds, fix failures, and document any genuinely blocked work.

## 0. Mission and Definition of Done

Build **Bachelor Chef ERP**, a mobile-first recipe discovery and community cooking platform with an administrator ERP portal. It is intended for university students, young professionals, and busy people in Bangladesh. A user should be able to select the ingredients they have, find feasible recipes—especially recipes that take no more than 20 minutes—publish recipes, review dishes, record meals cooked, set cooking goals, and maintain a saved cookbook. Administrators must be able to manage recipes and ingredients, moderate community content, import up to 500 recipes per batch from CSV or JSON, manage users, and see useful analytics.

The application is complete only when all of the following are true:

- [ ] Frontend and backend run locally using documented commands.
- [ ] A PostgreSQL schema is created through versioned migrations; no manual-only database setup is required.
- [ ] Authentication and server-side role-based authorization work.
- [ ] Public recipe browsing, search, recipe details, pantry matching, and portion/cost estimation work against the real database.
- [ ] Authenticated users can submit recipes, save recipes, add/edit reviews, record cooking history, manage goals, and edit their profiles.
- [ ] Community recipes and reviews are moderated; unapproved content is not public.
- [ ] Administrators can create/edit/archive official recipes, manage users and ingredients, moderate content, and import CSV/JSON files with row-level error reporting.
- [ ] The admin dashboard displays real aggregated database metrics, not invented or hard-coded analytics.
- [ ] Unit, integration/security, import, and UI automation tests exist and pass, apart from explicitly documented infrastructure-dependent tests.
- [ ] `.env.example`, seed/development setup, API docs, test instructions, and deployment instructions are supplied.
- [ ] No secrets, production credentials, user passwords, or private configuration are committed.
- [ ] No core feature is represented by a non-functional button, dead link, fake API response, or TODO-only implementation.

If the repository already contains working code, preserve and improve it rather than replacing it indiscriminately. First inspect its structure, package manifests, current tests, and Git state. Avoid destructive changes unrelated to this project.

---

## 1. Required Technology and Architecture

Use a **decoupled monorepo** so the Java backend remains suitable for STQA coursework and JUnit testing.

### Backend

- Java 21 LTS.
- Latest stable Spring Boot release compatible with Java 21 and the selected libraries; do not use milestone, snapshot, or release-candidate dependencies.
- Spring Web, Spring Validation, Spring Security, Spring Data JPA, PostgreSQL driver, Flyway migrations, Actuator health endpoint, and springdoc-openapi.
- Maven wrapper (`mvnw`) committed to the repository.
- JUnit 5, Mockito, Spring Boot Test, MockMvc, and Testcontainers where Docker is available. Apache Commons CSV (or another standards-compliant CSV parser) is required; **never parse real CSV with `String.split(",")`**.
- Use layered packages: `config`, `security`, `controller`, `dto`, `entity`, `repository`, `service`, `mapper`, `exception`, `importer`, and `analytics`.

### Frontend

- Next.js App Router, React, TypeScript, and the current stable Node.js LTS supported by the selected Next.js release.
- Tailwind CSS with accessible reusable UI primitives. Use a component library such as shadcn/ui only if it improves consistency and is correctly configured.
- Zod for client-side schemas and React Hook Form for complex forms. Client validation is for usability; the server must validate again.
- Use a consistent server/client data-access layer and typed API DTOs. Do not scatter raw `fetch` calls through all components. Keep API URL and auth behavior centralized.
- Use the current Next.js conventions for routing and server/client components. Do not mix Pages Router and App Router patterns.

### Data and media

- PostgreSQL as the source of truth.
- Flyway owns schema migrations. Do not rely on Hibernate `ddl-auto=update` in production; use validation mode.
- Recipe, step, avatar, and review images live in a media service (Cloudinary is the preferred initial adapter). Store only durable asset URLs and, where useful, provider asset IDs in PostgreSQL.
- Use Docker Compose for local PostgreSQL. Include a health check and a persistent named volume.
- Vercel is the preferred frontend host. Deploy the JVM-based Spring Boot API separately using Docker on a platform such as Render or Railway, and use managed PostgreSQL. Do not attempt to run a long-lived Spring Boot JVM as if it were a native Vercel Function.

### Architecture rules

- Browser → Next.js UI → Spring Boot REST API → PostgreSQL.
- The backend is authoritative for permissions, validation, matching, cost/portion calculations, moderation, and analytics.
- Controllers should be thin. Business rules belong in services, and queries belong in repositories.
- Use DTOs at API boundaries. Never serialize JPA entities directly as public responses.
- Use database transactions for multi-table recipe changes and goal/history mutations.
- Use pagination for potentially large lists; do not return every recipe, user, review, or import log in one response.
- Use UTC for persisted timestamps (`Instant` or equivalent) and display dates in the user's local context; the default display timezone is `Asia/Dhaka`.
- API paths are versioned under `/api/v1`.

Official implementation references:

- Spring Boot system requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Next.js deployment: https://nextjs.org/docs/app/getting-started/deploying
- Vercel Next.js guidance: https://vercel.com/docs/frameworks/full-stack/nextjs
- Render Docker deployment: https://render.com/docs/docker

---

## 2. Repository Layout

Create or adapt the repository to this shape. Adjust only when the existing project has a clear, equivalent convention.

```text
bachelor-chef-erp/
├── AGENTS.md
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml
├── Makefile                         # optional shortcuts for common tasks
├── database/
│   └── README.md                    # backup, migration, and seed notes
├── backend/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/bachelorchef/
│       │   ├── config/
│       │   ├── security/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── entity/
│       │   ├── repository/
│       │   ├── service/
│       │   ├── mapper/
│       │   ├── exception/
│       │   ├── importer/
│       │   └── analytics/
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/V1__initial_schema.sql
│       └── test/
│           ├── java/com/bachelorchef/unit/
│           ├── java/com/bachelorchef/integration/
│           ├── java/com/bachelorchef/security/
│           └── resources/
│               ├── bulk_recipes.csv
│               ├── bulk_recipes.json
│               └── fixtures/
├── frontend/
│   ├── package.json
│   ├── next.config.*
│   ├── tsconfig.json
│   ├── .env.example
│   └── src/
│       ├── app/
│       ├── components/
│       ├── features/
│       ├── hooks/
│       ├── lib/
│       ├── schemas/
│       └── types/
├── e2e/
│   ├── package.json or documented Java Selenium test setup
│   └── README.md
└── docs/
    ├── architecture.md
    ├── api.md or generated OpenAPI link/instructions
    ├── testing.md
    └── deployment.md
```

The root `README.md` must provide the quickest reliable path from a fresh clone to a working local application.

---

## 3. Product Roles and Authorization

### Roles

- `USER`: public browsing plus the user's own profile, submissions, bookmarks, history, goals, and reviews.
- `ADMIN`: all user capabilities plus official recipe management, ingredient catalogue management, moderation, user management, imports, and analytics.

Never trust a role sent by the frontend. Derive the current user and role from the authenticated server-side security context. A user must not be able to register as `ADMIN`, modify their own role, moderate their own content, or access another user's private data by changing an ID in a URL.

### Authentication

Implement:

- Email/password registration and login.
- Password hashing using Spring Security's supported adaptive password encoder (BCrypt or Argon2); never store plaintext passwords.
- Logout and a safe authenticated-session/token refresh strategy.
- A protected current-user endpoint (`GET /api/v1/auth/me`).
- Optional Google OAuth only if it can be implemented and configured completely; it must not block email/password authentication.
- Password reset only if a complete token-expiry and email-delivery flow is implemented. Otherwise document it as a later feature and do not show a fake reset-success flow.

Recommended separated frontend/API approach: short-lived access token handled in memory and a refresh token in a `Secure`, `HttpOnly`, appropriately `SameSite` cookie over HTTPS in production. If a simpler secure session implementation is selected, document it. Avoid storing long-lived bearer/refresh tokens in `localStorage`. Configure allowed CORS origins through environment variables, not `*` in production. Protect cookie-authenticated state-changing endpoints against CSRF as appropriate to the chosen strategy.

Create the first development admin only through a controlled bootstrap mechanism using environment variables, for example `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`. Hash its password, do not log the password, and do not silently reset an existing administrator on every restart. Never commit usable admin credentials.

### User lifecycle

Support at least `ACTIVE`, `SUSPENDED`, and `DELETED`/soft-deleted account status. Suspended/deleted accounts cannot authenticate or create content. Preserve referential integrity and published community content when an account is suspended or deleted; do not cascade-delete every recipe simply because its author account is removed. Apply explicit privacy/anonymization rules and document them.

---

## 4. Functional Requirements and Screens

All screens must be responsive and keyboard accessible. Every interactive element needs a real action, loading state, empty state, error state, and success feedback where applicable.

### Public and signed-in user experience

1. **Home / discovery page**
   - Explain the value proposition: cook with what's already available, keep costs low, and cook quickly.
   - Prominent “Find a meal” action leading to the pantry matcher.
   - Show approved community/official recipes and popular recipes using actual API data.
   - Search, sorting, filters, and pagination.

2. **Recipe catalogue**
   - Search by title and description; filter by ingredient, tag/category, difficulty, source (official/community), estimated cost, and total time.
   - Default quick-meal filter can be `total_time_mins <= 20`; make the active filter visible and removable.
   - Only `APPROVED` recipes are public. An author may see their own pending/rejected content in their workspace.

3. **Pantry matcher**
   - Search/select known ingredients using an autocomplete/multi-select UI.
   - Default maximum total time: 20 minutes; user can change the maximum up to 60 minutes.
   - Do not require the user to select ingredients in a fixed sequence.
   - Show matched required ingredients, missing required ingredients, match percentage, total time, estimated cost, rating, difficulty, and recipe link.
   - Let the user choose strict “I have every required ingredient” matching or a ranked “show closest matches” mode. Optional ingredients do not count as missing.
   - The backend performs matching and ordering. The frontend must not invent matches locally.
   - Explain that pantry match is an aid, not a guarantee that the user's exact brands, equipment, or substitutions will work.

4. **Recipe detail**
   - Image, title, author/source, description, prep and cooking time, computed total time, difficulty, rating/review count, ingredients, steps with optional images, tags, estimated cost, servings, and portions control.
   - Scale ingredient quantities when servings change. Use sensible decimal formatting without altering stored values.
   - Scale the estimated total recipe cost linearly from `base_servings`; label this as an estimate. Estimated cost per serving equals scaled total cost divided by selected servings.
   - “Save to cookbook”, “I cooked this”, and “Write a review” actions require login.
   - Only render image elements with valid URLs; use a useful placeholder when no image is available.

5. **Recipe submission/editor**
   - Authenticated users can create and edit their own community recipes.
   - Fields: title, description, prep time, cook time, difficulty, base servings, estimated BDT cost, image, tags, ingredients with quantity/unit/optional flag, ordered cooking steps and optional step photos.
   - Dynamic add/remove controls for ingredients and steps; validate at least one ingredient and one instruction step.
   - New user-submitted recipes enter `PENDING` moderation status. Editing an approved community recipe should return it to `PENDING` if material recipe content changes. An admin may approve/publish official recipes directly.
   - Users cannot set `is_official`, moderation status, moderation notes, or approval identity via the request body.

6. **Reviews and ratings**
   - Signed-in users may create one review per recipe and later update or delete their own review.
   - Rating is an integer from 1 to 5. A review may include a comment and one proof photo; ratings must be required.
   - New reviews enter moderation according to the moderation policy. Pending/flagged reviews are not shown in public review lists or rating aggregates.
   - Prevent a user from reviewing a nonexistent, archived, or non-approved recipe. If the product allows reviews only after cooking, enforce that policy consistently and document it; otherwise do not invent a hidden requirement.
   - Recalculate public average rating/count from approved reviews or maintain denormalized aggregates transactionally. Do not include pending/rejected reviews in public aggregates.

7. **Saved cookbook**
   - Add/remove recipe bookmarks; unique pair per user/recipe.
   - Paginated personal list and clear empty state.

8. **Cooked history (“Recipes Done”)**
   - Log a cook event against an accessible recipe with timestamp and optional notes.
   - Support multiple cook events for the same recipe because a user may cook it more than once.
   - Show date/time, recipe, notes, and history totals. Let users edit/delete only their own history records.

9. **Cooking goals**
   - Create monthly goals such as “Cook 5 new dinner recipes this month” or “Prepare 10 breakfasts under 15 minutes”.
   - Fields: title, target count (1–30), month (`YYYY-MM`), optional criteria such as tag/category, meal type, or max total time if implemented.
   - Progress must be calculated from qualifying cooked-history events or updated transactionally from them; it must never drift from history after an event is edited/deleted.
   - Once target is reached, mark goal complete automatically. Prevent goals being created for invalid month formats or impossible target values.
   - Clearly define whether “new recipes” counts unique recipe IDs or cooking events and implement the same definition in progress, UI, and tests.

10. **Profile and preferences**
    - View/update allowed profile fields and avatar.
    - Never return password hashes, OAuth secrets, refresh tokens, internal moderation metadata, or administrative fields in profile responses.

### Admin ERP portal

Provide an admin-only section with a clear navigation structure and responsive layouts:

1. **Dashboard:** active users, total approved recipes, pending recipe submissions, pending reviews, recipes cooked/logged, average rating, popular ingredients, top-rated recipes, most-cooked recipes, and recent import jobs. All values must come from backend queries and have meaningful date-range definitions.
2. **Recipe management:** create/edit official recipes, preview, publish/approve, reject, archive, and restore where appropriate. Archiving should hide a recipe from normal public discovery while preserving history/reviews and showing an understandable state.
3. **Ingredient catalogue:** search, add, rename, categorize, and deactivate ingredients. Prevent duplicate normalized names. Renaming an ingredient must not break existing recipe references or pantry matching.
4. **Moderation queue:** filter by pending/flagged content, see submitter and content context, inspect reports/notes, approve/reject/remove with a recorded reason, and paginate results. Never expose admin-only moderation notes in public responses.
5. **Bulk import:** upload CSV or JSON, see schema/file validation, preview parsed rows, confirm import, and view per-row results. A malformed row should not hide the reason for its failure.
6. **User management:** search/paginate users; view status and basic activity; suspend/reactivate accounts; change roles only through an authorized explicit action. Do not expose credential material.
7. **Analytics and upload logs:** date-filtered charts/tables for popular ingredients, highly rated/cooked recipes, user activity, and every bulk import job, including who uploaded it, when, totals, and row-level errors.

---

## 5. Business Rules and Validation Contract

Use the same rules on the server, in UI validation where relevant, and in tests. Server-side checks are authoritative.

### Recipe timing

- `prep_time_mins`: integer, `0..60`.
- `cook_time_mins`: integer, `1..60`.
- `total_time_mins` is computed as `prep_time_mins + cook_time_mins`; do not trust a client-provided total.
- Hard recipe ceiling: total time must not exceed 60 minutes for the initial release/importer. Reject an invalid recipe with a clear field/row error.
- Timing labels use **total time**: Express `<= 15`, Bachelor Quick `16..20`, Extended `21..60`.
- The pantry matcher defaults to `total_time_mins <= 20`, but can be expanded to 60.
- A recipe with `cook_time_mins=60` and nonzero prep time exceeds the 60-minute total ceiling and must be rejected. Test total time as well as individual field boundaries.

### Other validation

- Title: required, trimmed, 3–150 characters.
- Description: optional with a documented maximum, e.g. 5,000 characters.
- Difficulty: enum `EASY`, `MEDIUM`, `HARD` in the API/database; display as Easy/Medium/Hard.
- Base servings: integer `1..20`.
- Cost: estimated BDT total cost for `base_servings`; non-negative, limited to a reasonable precision/range. Use `BigDecimal`, never floating-point arithmetic for money.
- Ingredients: at least one; ingredient must exist; quantity greater than zero and within database precision; unit required and bounded (e.g. 20 chars); optional is boolean.
- Steps: at least one; step numbers unique and contiguous from 1 after normalization; instruction required; optional image URL.
- Review rating: accept only integer values `1..5`; reject `0`, `6`, decimals, and strings.
- Goal target count: accept integers `1..30`; reject `0` and values greater than 30.
- Bulk import: file must be non-empty, format must be CSV or JSON, maximum 500 data records per batch (exclude the CSV header), impose a configurable byte limit such as 5 MB, and reject malformed/oversized files before database writes.
- Pagination values must have bounds, e.g. page size `1..100`; validate sort fields against a server-side allow-list to prevent unsafe dynamic queries.
- Normalize ingredient keys by trimming and case-folding consistently. Enforce uniqueness at the database level as well as in application code.
- Reject invalid URLs/protocols for external media URLs. Prefer provider-generated HTTPS URLs. Never allow arbitrary local filesystem paths from users.

### Cost and portion estimation

`estimated_cost_bdt` means estimated total cost to prepare the recipe at `base_servings`, not cost per serving. For target servings `n`:

`scaled_cost = estimated_cost_bdt × n / base_servings`

Round displayed money to two decimal places; do not repeatedly round stored recipe cost or ingredient quantities. This first release may use a simple linear cost estimate. Do not claim the result is a live market quote. If ingredient-level prices are introduced, document their unit/conversion rules and use a versioned price source; do not infer conversions between pieces, cups, grams, and tablespoons without explicit metadata.

### Pantry-matching algorithm

Implement this on the backend and cover it with unit tests:

1. Accept a deduplicated list of known `ingredient_id` values, max total time `1..60`, optional difficulty/tags/cost filters, and a `strict` flag.
2. Consider only public approved, non-archived recipes unless the requester is viewing their own permitted content through a dedicated owner endpoint.
3. Exclude optional recipe ingredients from the required-ingredient denominator.
4. For every candidate, compute required count, matched required count, missing required ingredients, and match percentage. If a recipe has no required ingredients, reject it during recipe validation rather than divide by zero.
5. `strict=true`: return only recipes for which all required ingredients are selected. `strict=false`: include recipes with partial matches and show missing ingredients.
6. Rank partial matches by descending match percentage, then fewer missing required ingredients, shorter total time, higher approved average rating, and stable title/ID tie-breaker. Document the actual SQL/service behavior; do not let unstable database order drive the UI.
7. Optional ingredients may be displayed as optional but do not affect required-ingredient match percentage.
8. Avoid an N+1 query pattern. Use an efficient grouped query/projection or fetch all needed ingredients in a bounded number of queries.

### Ratings and goal aggregation

- Public average ratings include only approved reviews for public recipes.
- If review moderation is enabled, rating aggregates update only after approval and are corrected after a review is edited, removed, or rejected.
- Goal counters derive from cooked-history records and goal criteria. Use the same definition in dashboard, API, and tests.

---

## 6. Database Model

Implement the schema through Flyway migrations. The following is the required logical model. Names may differ slightly if consistent across the entire application; do not omit entities because a simpler mock is easier.

### `users`

- `user_id` primary key (prefer identity/serial consistently).
- `full_name` required, max 100.
- `email` required, unique, stored normalized/lowercase.
- `phone_number` nullable and unique when present.
- `password_hash` nullable only for OAuth-only accounts.
- `google_id` nullable and unique if Google login is implemented.
- `role` with database check/enum for `USER` and `ADMIN`; default `USER`.
- `avatar_url` nullable.
- `status` (`ACTIVE`, `SUSPENDED`, `DELETED`).
- `created_at`, `updated_at`; UTC timestamps.

### `ingredients`

- ID, display `name`, unique `normalized_name`, optional category/unit metadata, active flag, created/updated timestamps.
- Index category/active status where useful.
- Deactivated ingredients remain attached to old recipes, but cannot be added to new recipes unless explicitly reactivated.

### `recipes`

- ID, title, description, prep time, cook time, computed/stored validated total time, difficulty enum, estimated total cost in BDT, `base_servings`, main image URL/provider asset ID where relevant.
- `is_official` or an equivalent clearly defined source field.
- `created_by` nullable FK to users using `ON DELETE SET NULL` (or implement explicit soft deletion) rather than cascading recipe deletion on account deletion.
- Moderation `status`: at minimum `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`, `ARCHIVED`.
- `moderation_note`, `approved_by`, `approved_at`, created/updated timestamps.
- CHECK constraints for times, cost, servings; indexes for status, creator, created date, total time, and common catalogue filters.
- Total time must be computed and validated in service code and protected by a database-consistent invariant where practical.

### `recipe_ingredients`

- Composite primary key `(recipe_id, ingredient_id)` unless the implemented product deliberately supports repeated rows for the same ingredient.
- FKs to recipes and ingredients with deletion behavior chosen to preserve integrity.
- Positive quantity as numeric/decimal, required unit, `is_optional` boolean.
- Do not use floating-point quantity values.

### `recipe_steps`

- ID, recipe FK with cascade on recipe deletion, `step_number`, instruction text, optional step image URL/provider asset ID.
- Unique `(recipe_id, step_number)`; order is deterministic.

### `tags` and `recipe_tags`

- Add these tables because tag categories are a product requirement.
- Tag name and unique normalized key; many-to-many recipe association with a composite primary key.
- Public responses return only approved recipe tags through approved recipe reads.

### `reviews`

- ID, recipe FK, user FK, required rating with `CHECK (rating BETWEEN 1 AND 5)`, optional comment and photo URL/provider asset ID.
- Moderation state and reason, timestamps.
- Unique `(recipe_id, user_id)` for one editable review per user per recipe.
- Index recipe/status and user/date.
- Decide and document FK/account-deletion behavior. Avoid deleting a published recipe's full history due to a user account action.

### `user_cooked_history`

- ID, user FK, recipe FK, cooked timestamp, optional notes, created/updated timestamps.
- Multiple rows for the same user/recipe are allowed.
- Index `(user_id, cooked_on DESC)` and recipe ID.

### `user_goals`

- ID, user FK, title, target count constrained `1..30`, target month stored as a real month/date value (e.g. first day of month) rather than a free-form string, optional goal criteria, current count if cached, completion status and timestamps.
- Ensure progress and completion remain correct when cook history is added/edited/deleted. A computed query is acceptable for correctness; cache only with reliable transactional maintenance.

### `saved_recipes`

- User FK, recipe FK, saved timestamp; composite primary key `(user_id, recipe_id)`.
- Do not expose another user's saved list.

### `bulk_upload_jobs` and `bulk_upload_errors`

- Job: uploader admin FK, source filename (sanitized), file type, status, started/finished timestamps, total rows, imported rows, failed rows, and useful summary.
- Per-row errors: job FK, row number, error code/message, bounded/sanitized row excerpt where appropriate. Never store uploaded passwords/tokens or unnecessarily retain full personal data.
- Provide a queryable job-history API and UI. If raw files are temporarily stored, delete them after processing unless retention is necessary and documented.

### Recommended supporting entities

- `content_reports` or an equivalent mechanism to flag recipes/reviews, if “flag” moderation is exposed to users/admins.
- `audit_logs` for sensitive admin actions such as role/status changes, moderation decisions, official recipe changes, and imports. Record actor/action/target/time and safe metadata; do not log credentials or tokens.

### Database quality requirements

- Add database constraints and indexes in migration files, not just Java validation.
- Add `updated_at` consistently and implement updates correctly.
- Avoid cascade deletion from `users` to official/published content. Choose FK delete behavior intentionally and test it.
- Use case-insensitive/normalized uniqueness for email, ingredient, and tag names.
- Create migrations in order (`V1__...`, `V2__...`) instead of editing an already-applied migration in deployed environments.

---

## 7. REST API Contract

Implement these route groups under `/api/v1`. Exact DTO field names should be consistent and documented in OpenAPI. Return proper HTTP status codes and the common error format defined below. Do not expose repositories/entities.

### Auth and profile

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/logout`
- `POST /auth/refresh` (if using refresh tokens)
- `GET /auth/me`
- `PATCH /users/me`
- `POST /users/me/avatar-upload-signature` (if using provider-signed client uploads)

### Discovery, recipe and ingredient APIs

- `GET /recipes` — pagination, query, sort and filters.
- `GET /recipes/{recipeId}` — approved recipes only for public users.
- `POST /recipes` — authenticated community submission.
- `PATCH /recipes/{recipeId}` — owner or admin, with moderation transition rules.
- `DELETE /recipes/{recipeId}` — owner policy/admin archive behavior; do not hard-delete casually.
- `POST /recipes/match` — pantry matcher.
- `POST /recipes/{recipeId}/cook` — add history event.
- `GET /ingredients?query=...&category=...`
- Admin-only ingredient create/update/activate/deactivate endpoints.
- `GET /tags` and admin tag-management endpoints if tag editing is supported.

### Reviews and saved cookbook

- `GET /recipes/{recipeId}/reviews`
- `POST /recipes/{recipeId}/reviews`
- `PATCH /reviews/{reviewId}`
- `DELETE /reviews/{reviewId}`
- `PUT /users/me/saved-recipes/{recipeId}` or `POST` to save and `DELETE` to unsave.
- `GET /users/me/saved-recipes`

### Personal cooking profile

- `GET /users/me/cooked-history`
- `PATCH /users/me/cooked-history/{historyId}`
- `DELETE /users/me/cooked-history/{historyId}`
- `GET /users/me/goals`
- `POST /users/me/goals`
- `PATCH /users/me/goals/{goalId}`
- `DELETE /users/me/goals/{goalId}`

### Admin APIs

- `GET /admin/dashboard/summary?from=...&to=...`
- `GET /admin/analytics/ingredients`
- `GET /admin/analytics/recipes`
- `GET /admin/users` and authorized user status/role management endpoints.
- `GET /admin/recipes` for all statuses; `POST /admin/recipes` for official recipes.
- `POST /admin/recipes/{recipeId}/approve`, `/reject`, `/archive`, and `/restore` as applicable.
- `GET /admin/moderation/recipes`, `GET /admin/moderation/reviews`, plus approval/rejection/flag-handling actions.
- `POST /admin/imports/preview` — validate and preview without database changes.
- `POST /admin/imports` — process an approved upload and return or create a job result.
- `GET /admin/imports` and `GET /admin/imports/{jobId}` — paginated history and row errors.
- Optional export of row errors as CSV if implemented without exposing private data.

### API response rules

- `200 OK` for successful reads/updates, `201 Created` for resource creation, `204 No Content` for successful deletes where appropriate, `400 Bad Request` for validation failures, `401 Unauthorized` when unauthenticated, `403 Forbidden` for insufficient privileges, `404 Not Found` for inaccessible/nonexistent resources when that avoids ownership leaks, `409 Conflict` for uniqueness/state conflicts, and `413 Payload Too Large` for file size limits.
- Use one standard error shape, for example:

```json
{
  "timestamp": "2026-10-13T08:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Please correct the highlighted fields.",
  "fieldErrors": {
    "rating": "Must be an integer between 1 and 5."
  },
  "traceId": "optional-correlation-id"
}
```

- Never leak SQL errors, stack traces, token values, password hashes, or storage secrets to clients.
- Document request/response DTOs, security requirements, pagination, filters, and status transitions with OpenAPI.

---

## 8. Bulk Recipe Import — CSV and JSON

Bulk import is a core ERP feature, not a future placeholder. Build validation/preview and actual import, backed by tests.

### Shared behavior

1. Check the authenticated principal and require `ADMIN` **before reading or parsing the file**.
2. Validate extension/content type and file byte limit; extension alone is not proof of format.
3. Reject null, zero-byte, malformed, unsupported, or oversized files with useful errors.
4. Accept a maximum of **500 data rows/recipe objects per job**, excluding a CSV header. Reject a batch with 501 data records before writing any recipes.
5. Parse and validate in a preview stage, reporting valid and invalid rows with row/object index, field, and human-readable reason.
6. On confirmation, persist valid records and report row-level outcomes. One invalid recipe must not prevent unrelated valid rows from importing, but never leave a single recipe half-written. Use a transaction per recipe (including its ingredient links, steps, tags, and recipe row) or another design with equivalent all-or-nothing row behavior.
7. Because partial success is allowed, the job status and count summary must match database reality. Make retries safe: do not create accidental duplicate rows from a network retry. Use an idempotency key/job confirmation token, deterministic duplicate policy, or clearly defined duplicate detection.
8. Log the admin, start/end, file type, counts, and row-level errors. Sanitize filenames and error excerpts.
9. Support cancellation only if it can be implemented reliably; do not show a fake cancel button.
10. Ensure the final response can be represented in the UI as imported count, rejected count, warnings, and downloadable/viewable error details.

### JSON input

The top-level value is an array of recipe objects. Reject non-array roots and malformed JSON. Each recipe supports:

```json
{
  "title": "Quick Egg Fried Rice",
  "description": "A quick meal using leftover rice.",
  "prep_time_mins": 5,
  "cook_time_mins": 10,
  "difficulty_level": "EASY",
  "estimated_cost_bdt": 80.00,
  "base_servings": 2,
  "image_url": "https://example.com/egg-rice.jpg",
  "tags": ["Quick", "Rice"],
  "ingredients": [
    { "name": "Cooked Rice", "quantity": 2, "unit": "cups", "is_optional": false },
    { "name": "Egg", "quantity": 2, "unit": "pcs", "is_optional": false },
    { "name": "Soy Sauce", "quantity": 1, "unit": "tbsp", "is_optional": true }
  ],
  "steps": [
    { "step_number": 1, "instruction_text": "Heat oil in a pan.", "image_url": null },
    { "step_number": 2, "instruction_text": "Add the ingredients and cook thoroughly.", "image_url": null }
  ]
}
```

Rules:

- `title`, valid prep/cook times, valid difficulty, at least one ingredient, and at least one step are required.
- `total_time_mins` is derived by the backend, even if the import file includes it. Never trust a supplied total.
- `is_official`, approval state, creator ID, and admin identity must not be accepted from the file; the authenticated importing admin is the creator and imported recipes are official and immediately approved after successful validation/import.
- Resolve ingredients/tags case-insensitively through normalized names. Create them only according to the defined catalogue policy; do not create duplicates with different casing/whitespace.
- Validate every object and nested child. Unknown or invalid fields should generate helpful validation messages; strictness should be documented.

### CSV input

Support this documented header format:

```csv
title,prep_time_mins,cook_time_mins,difficulty_level,estimated_cost_bdt,ingredients_csv,instructions_pipe
Quick Egg Noodles,3,10,EASY,60.00,Noodles:1:pack|Egg:1:pcs|Green Chili:2:pcs,Boil noodles for 3 mins|Scramble egg in a pan|Mix noodles with spices
Masala Oats,2,8,EASY,45.00,Oats:1:cup|Water:2:cups|Onion:1:pcs,Chop onion and fry|Add water and oats|Cook for 5 mins
```

- `ingredients_csv` encodes `Name:quantity:unit` items separated by `|`.
- `instructions_pipe` encodes ordered instructions separated by `|`.
- Trim whitespace and reject empty ingredient names, invalid quantities, empty instruction steps, malformed item encodings, and missing required columns.
- Use a real CSV parser, so commas inside quoted titles/descriptions and escaped quotes work. Do not split the whole line on commas.
- Use a required-header allow-list. Optionally support `description`, `base_servings`, `image_url`, and `tags_pipe`; document the exact allowed header set and implement those fields consistently.
- Because pipe is used inside the supported fields, explain how literal pipe characters are escaped or reject them with a clear message. Do not silently corrupt the recipe.
- Report physical line number and logical data row number when feasible. Header is not counted as a recipe.

### Import statuses

At minimum distinguish `PREVIEWED`, `PROCESSING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, and `FAILED`. A preview must never write recipe records. If the request fails before parsing, create an import job only when enough metadata is available and the behavior is documented.

---

## 9. Media Uploads

- Use signed/direct-to-provider uploads so large images do not need to transit through the JVM API unnecessarily. The backend generates an upload signature or short-lived upload parameters only after authentication/authorization.
- Restrict allowed image MIME types to JPEG, PNG, and WebP; validate content and configured maximum size. SVG uploads should be disallowed unless sanitized with a well-understood safe pipeline.
- Set reasonable client image dimensions/quality and display cropping without modifying the original source file unexpectedly.
- Save provider asset ID and secure URL when available. On deletion/replacement, delete provider assets only when ownership is verified and the asset is no longer referenced.
- An admin import containing image URLs must accept only permitted HTTPS URLs, and the app should have safe image-host configuration to avoid unsafe remote image processing.
- Provide useful fallback visuals for recipes without images. Image upload failure must not silently discard the text recipe or claim the upload succeeded.

---

## 10. Security, Privacy, and Reliability

- Server-side RBAC on every admin route and ownership checks for user-owned resources.
- Password hashing; never log credentials. No default production admin credentials.
- Validate DTOs with Bean Validation and validate nested import objects thoroughly.
- Parameterized queries only. Avoid concatenating user query strings into SQL.
- Configure CORS to an explicit set of frontend origins in production.
- Configure TLS at the hosting edge; secure cookies in production.
- Rate-limit login, registration, review/report endpoints, and bulk import as appropriate for the chosen infrastructure.
- Add login failure handling and generic authentication errors that avoid revealing whether an email is registered.
- Enforce request size and image/file size limits. Protect CSV/JSON imports from malformed input and resource exhaustion.
- Sanitize user-generated text in the frontend; do not render untrusted HTML. Validate external URLs and avoid unsafe link protocols.
- Do not allow unauthenticated access to private profile, history, saved recipes, goals, import logs, or analytics.
- Avoid IDOR: verify owner/role for every path-scoped mutation/read.
- Use database transactions, unique constraints, foreign keys, and optimistic locking where concurrency makes it useful.
- Return consistent errors and log exceptions server-side with trace/correlation IDs, without exposing details to users.
- Add security tests for every role-protected API group and for user A attempting to access user B's records.
- Keep `.env`, local uploads, compiled assets, logs, reports containing personal data, and secrets out of Git.

---

## 11. UI Design Direction

Create a distinctive but practical cooking product, not a generic enterprise dashboard template. It should feel friendly to students, quick to scan, and usable on mobile while still offering a serious admin workspace.

- Brand name: **Bachelor Chef**. ERP/admin product may be labelled **Bachelor Chef Admin**.
- Visual tone: clean, fresh, energetic, affordable, community-focused; warm neutral backgrounds, a restrained food-inspired accent palette, high-contrast text, and generous spacing.
- Prioritize recipe cards with readable time, cost in `৳`/`BDT`, difficulty, ratings, and match percentage.
- Use the same card, typography, button, field, badge, modal, table, pagination, and toast patterns throughout the app.
- Ensure contrast and keyboard focus are accessible. Provide visible validation messages and proper labels rather than relying on placeholder text.
- Use skeletons or clear loading states; avoid layout shifts. Use empty states that explain the next action.
- Admin tables must work on narrow screens, with horizontal overflow or mobile card conversion rather than clipped essential controls.
- Do not add animated effects or decorative gradients that hurt legibility or performance.
- Avoid excessive emoji as icons. Use a consistent icon set.
- Display local money as `৳80.00` or `80.00 BDT` consistently. Do not mix currencies.
- All key flows must be keyboard navigable and usable at common viewport sizes.

### Required frontend routes (minimum)

```text
/                       Home / discovery
/recipes                Search and filters
/recipes/[id]           Recipe details
/match                  Pantry matcher
/submit                 New recipe
/recipes/[id]/edit      Edit owned recipe
/cookbook               Saved recipes
/cooking-history        Recipes Done
/goals                  Cooking goals
/profile                Profile and submissions
/login                  Login
/register               Registration
/admin                  Admin dashboard (ADMIN only)
/admin/recipes           Official/all recipe management
/admin/ingredients       Ingredient catalogue
/admin/moderation        Pending/flagged content
/admin/imports           CSV/JSON bulk importer and job history
/admin/users             User administration
/admin/analytics         Analytics
```

A route may redirect to login if protected. Any missing detail page must show a real not-found state, not a blank screen.

---

## 12. Testing and STQA Requirements

Tests are part of the deliverable. Write them as the implementation is built, not as a final afterthought.

### Unit tests

Cover at least:

- Recipe field validation and computed total time.
- Express/Quick/Extended time classification at 15/16/20/21 and maximum at 60/61.
- Portion scaling and BDT rounding.
- Strict and non-strict pantry matching; optional ingredients; empty and duplicate pantry selections; deterministic ordering.
- Goal progress semantics, target completion, month scoping, and history edits/deletes.
- Review rating boundaries 0/1/3/5/6 and moderation aggregate behavior.
- Ingredient normalization and tag normalization.
- CSV field parsing/quoted commas, malformed row, invalid number, empty ingredient, empty step, and header errors.
- JSON object validation, missing keys, wrong data types, malformed JSON, nested validation, and invalid total time.
- Bulk batch boundaries: 0, 1, 50, 500, and 501 data rows; empty file and byte-size limit.
- Import partial success, transaction atomicity per recipe, correct result counts, and retry/idempotency behavior.

### Boundary Value Analysis (BVA)

Use these expected rules, and explicitly distinguish a field boundary from a calculated total-time boundary:

| Target | Cases | Expected behavior |
|---|---|---|
| `cook_time_mins` | 0, 1, 15, 20, 21, 60, 61 | 0 and 61 rejected; 1–60 can be accepted only if the calculated total is <=60; total-time category is derived from prep+cook. |
| Calculated total time | 15, 16, 20, 21, 60, 61 | 15 express; 16–20 Bachelor Quick; 21–60 Extended; 61 rejected. Matcher default returns only <=20. |
| CSV data rows | 0, 1, 50, 500, 501 | Reject empty/0 and >500; accept 1–500, with row-level errors reported. |
| Rating | 0, 1, 3, 5, 6 | Reject 0 and 6; accept 1–5. |
| Goal target | 0, 1, 30, 31 | Reject 0 and 31; accept 1–30. |
| Portions | 0, 1, 20, 21 | Reject 0 and 21; accept 1–20. |

### JUnit and integration/security tests

- Use JUnit 5 assertions and `@BeforeEach` setup appropriately.
- Test the actual service and controllers; avoid tests that only assert a mocked value that duplicates the implementation.
- Create temporary test files with actual content (`Files.createTempFile` / `Files.writeString`) instead of assuming a file path already exists.
- Test unauthenticated access, regular-user denial from admin endpoints, user ownership checks, admin access, and safe handling of deleted/suspended users.
- Test database constraints and transactions with an isolated test database (prefer Testcontainers PostgreSQL where available). If CI cannot run Docker, keep unit tests runnable independently and clearly identify the integration-test requirement.
- Verify row-level imports preserve successful rows and don't leave incomplete recipe/ingredient/step links.
- Recalculate cyclomatic complexity from the implementation's actual control-flow graph; do not blindly claim a fixed `V(G)` from an illustrative code snippet. Document the independent basis paths and design test cases to cover them.

### Selenium UI automation

Create a real Selenium WebDriver end-to-end test for the admin CSV flow and at least one user journey. Requirements:

- Start with the app's real login route and test fixture/admin bootstrap documented through environment variables or a test-only profile.
- Upload an actual fixture file through the file input.
- Assert import counts and errors against visible UI elements and, where practical, verify the imported recipe appears in the admin recipe list.
- Use `WebDriverWait`/explicit waits; **do not use fixed `Thread.sleep` as a synchronization strategy**.
- Ensure WebDriver is closed in `@AfterEach`/equivalent even when tests fail.
- Do not hard-code a production password in committed test code.
- Make UI tests easy to run with documented base URL/browser setup. Keep fast unit tests independent from Selenium.

### Quality gates

Run and fix:

- Backend: `./mvnw test` and `./mvnw verify` where configured.
- Frontend: lint, type-check, unit tests, and production build.
- Database: fresh-migration test from an empty database.
- E2E: admin upload and basic user lifecycle smoke flows in a configured environment.
- If a check cannot run due to unavailable external credentials/service, do not claim it passed. Document the exact blocker and provide a repeatable command for it.

---

## 13. Seed Data and Development Experience

- Include a safe development-only seed mechanism with common ingredients used in student kitchens: egg, rice, noodles, onion, garlic, green chili, potato, tomato, lentils, oats, milk, bread, soy sauce, oil, salt, pepper, and other ordinary ingredients.
- Include several realistic sample recipes with ingredients and steps so browsing, matching, portions, costs, tags, and analytics can be tested from first launch.
- Keep seed recipes explicitly marked as sample/development content. Production must not silently seed fake activity or fake analytics.
- Do not seed hard-coded end-user passwords. Admin bootstrap must be environment-driven and disabled or controlled in production.
- Supply `bulk_recipes.csv` and `bulk_recipes.json` in test resources, with valid examples and a separate invalid fixture for error cases.
- Use deterministic test fixture IDs/data where appropriate without making database IDs a public contract.

---

## 14. Environment Variables and Local Run

Provide root and app-specific `.env.example` files with documented placeholders only. A practical set includes:

```dotenv
# Database
POSTGRES_DB=bachelor_chef
POSTGRES_USER=bachelor_chef_app
POSTGRES_PASSWORD=change_me_locally
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bachelor_chef
SPRING_DATASOURCE_USERNAME=bachelor_chef_app
SPRING_DATASOURCE_PASSWORD=change_me_locally

# Backend
SERVER_PORT=8080
APP_FRONTEND_ORIGINS=http://localhost:3000
APP_JWT_ACCESS_SECRET=replace_with_a_long_random_secret
APP_ACCESS_TOKEN_TTL_MINUTES=15
APP_REFRESH_TOKEN_TTL_DAYS=14
BOOTSTRAP_ADMIN_EMAIL=admin@example.test
BOOTSTRAP_ADMIN_PASSWORD=replace_me_for_local_dev

# Media provider (optional in local dev; document the fallback)
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=

# Frontend
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
```

Adapt names to the implementation, but keep them consistent. Never prefix secrets with `NEXT_PUBLIC_`. Validate required production environment variables at startup and fail clearly if missing. Ensure `.env` files are in `.gitignore`.

Required developer commands must be documented, with equivalents for Windows where feasible:

1. Copy `.env.example` to local `.env` and set local values.
2. Start PostgreSQL using Docker Compose.
3. Run backend with Maven wrapper and wait for health readiness.
4. Install frontend dependencies and run the dev server.
5. Run tests/lint/type checks/build.
6. Load development sample content and create a bootstrap admin safely.

Provide a health endpoint and compose/container health checks. Explain Flyway startup behavior and how to reset a local development database without implying it is safe for production.

---

## 15. Deployment

### Frontend on Vercel

- Connect the frontend project/root directory and set `NEXT_PUBLIC_API_BASE_URL` to the deployed HTTPS API base.
- Add only public client configuration with `NEXT_PUBLIC_`; never add API secrets or database credentials to the frontend environment.
- Configure production and preview origins carefully. Avoid letting arbitrary Vercel preview domains access production admin APIs.
- Verify image remote patterns and any rewrites/proxy configuration needed by the chosen setup.

### Backend on a JVM host (e.g. Render Docker)

- Include a production Dockerfile that builds and runs the Spring Boot application with a non-root user where practical, a sensible JVM memory configuration, and a health endpoint.
- Configure DB URL/credentials, allowed origins, token secrets, media credentials, and bootstrap-admin behavior in the host's secret environment manager.
- Use managed PostgreSQL, TLS, restricted database credentials, backup strategy, and migration execution appropriate to the chosen host. Do not use local container disk as the permanent recipe-image store.
- Run migrations in a controlled deploy step/startup strategy. Never run destructive reset scripts against production.
- Provide separate development/staging/production environment notes and minimum-cost caveats where free-tier availability, sleep behavior, storage, or database limits may change.

### Production acceptance checklist

- Frontend and API use HTTPS.
- Allowed-origin CORS works from the production frontend only.
- Login/refresh/logout work after deployment.
- User can submit a recipe and upload images.
- Public users cannot see pending content or enter admin routes.
- Admin import works with the configured file-size/batch limit.
- Database data persists across deployment/restarts.
- Error logs contain no tokens, passwords, or media secrets.
- Health checks, backups, and secret rotation process are documented.

---

## 16. Implementation Order — Execute in This Sequence

The agent should perform the following in the current task/session. Do not return only the following plan; carry it out.

### Phase 1 — Inspect and establish the foundation

1. Inspect the repository, tool versions, existing code, and Git status.
2. Create/adjust the monorepo structure and root README.
3. Set up Docker Compose PostgreSQL and backend project with health checks.
4. Implement Flyway schema migrations, config profiles, common errors, logging, and OpenAPI.
5. Verify a fresh database can migrate and the backend boots.

### Phase 2 — Identity and authorization

1. Implement registration/login/logout/session strategy/current-user endpoint.
2. Add server-side role enforcement and owner checks.
3. Add environment-driven admin bootstrap and security tests.
4. Verify unauthorized/forbidden paths and frontend route gating.

### Phase 3 — Recipe domain and discovery

1. Implement ingredient/tag catalogue, recipe/step/ingredient entities, repositories, DTOs, validation, and transactional services.
2. Implement public catalogue/search/filters/pagination and recipe details.
3. Implement pantry matching and stable ranking.
4. Implement portion scaling and cost estimate calculations.
5. Build the responsive home, recipe catalogue, matcher, and recipe details screens.

### Phase 4 — Community and personal cooking

1. Implement recipe submissions/editing and moderation-state transitions.
2. Implement reviews/ratings and moderation-aware aggregates.
3. Implement bookmarks, cooked history, goals, profile, and progress calculations.
4. Build all user forms/pages and validate ownership/privacy.
5. Add unit/integration/security tests for every domain.

### Phase 5 — Admin ERP

1. Implement official recipe and ingredient management.
2. Implement content moderation and audit events.
3. Implement CSV/JSON preview, schema validation, row-level transactional imports, job logs, retry/duplicate policy, and error reports.
4. Implement real analytics queries and admin user management.
5. Build the admin dashboard, tables, import wizard, moderation queue, user manager, and analytics UI.

### Phase 6 — Polish, automation, and release

1. Add seed data and valid/invalid import fixtures.
2. Implement JUnit BVA/basis-path coverage, security tests, integration tests, and Selenium flows.
3. Run formatter/linter, type checks, tests, clean migrations, and production builds; fix failures.
4. Add Dockerfiles and hosting/deployment instructions.
5. Verify no core feature remains mocked or unwired. Update documentation with what is implemented and any honest limitations.

At each phase, run the narrowest relevant tests, then run the full suite near completion. If a dependency or platform assumption fails, choose a compatible stable alternative and update documentation instead of abandoning the feature.

---

## 17. Coding Standards and Agent Behavior

- Prefer small, typed, testable methods; meaningful names; clear DTO validation; and explicit transaction boundaries.
- Keep business logic out of React components and controller methods.
- Do not create enormous single-file services or components. Break features into coherent modules.
- Do not add dependencies without a clear reason; pin versions through the project build system and commit lockfiles where applicable.
- Handle `loading`, `empty`, `error`, and success states for all async UI interactions.
- Use accessible labels and controls; do not attach actions only to decorative elements.
- Do not use a fake API, in-memory core data store, or static dashboard metrics as a substitute for PostgreSQL-backed features.
- Do not swallow exceptions or return HTTP 200 for failed operations.
- Do not make the importer trust file-supplied creator IDs, roles, moderation state, or total time.
- Do not hard-delete user-generated data unless the deletion policy explicitly requires it and its implications are tested.
- Do not use fixed `Thread.sleep` in Selenium tests.
- Do not claim tests, builds, deployments, uploads, or integrations succeeded unless they were actually executed and observed to succeed.
- When finished, provide a concise handoff stating: implemented areas; exact local run commands; migrations/seed behavior; test commands and results; deployment configuration still needed (such as real Cloudinary credentials); and anything that could not be completed.

## 18. Final Acceptance Checklist

Before calling the project complete, verify each item:

- [ ] Fresh checkout → documented setup succeeds.
- [ ] Database migrations succeed from an empty database.
- [ ] Backend health endpoint and OpenAPI docs work.
- [ ] Frontend production build succeeds with strict TypeScript checks.
- [ ] Register/login/logout and role checks work.
- [ ] Public browse/search/detail only shows approved, non-archived recipes.
- [ ] Pantry matcher uses the selected ingredient IDs and time limit, and reports missing ingredients accurately.
- [ ] Recipe creation/editing saves ingredients, steps, tags, and media URLs transactionally.
- [ ] Portion adjustment recalculates quantities and estimated total/per-serving cost correctly.
- [ ] Reviews, moderation, aggregate ratings, bookmarks, history, and goals work.
- [ ] Admin official recipe/ingredient/user management and moderation work.
- [ ] CSV and JSON imports support preview, valid rows, invalid rows, 500/501 limits, and row-level reporting.
- [ ] Failed recipe import rows do not create orphaned/half-saved recipe records.
- [ ] Import history and analytics use persisted real data.
- [ ] User A cannot read or modify User B's private records.
- [ ] Non-admins cannot call admin endpoints even by direct API requests.
- [ ] JUnit, integration, BVA, and configured Selenium tests pass, or precise environment blockers are documented.
- [ ] No committed secrets or production credentials.
- [ ] README and `.env.example` accurately describe the shipped implementation.

**Build the product described by this contract, not just its scaffolding.**

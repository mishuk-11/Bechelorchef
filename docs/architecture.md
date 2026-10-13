# Architecture

## Components

```
┌──────────┐   HTTPS    ┌─────────────────────────────┐  /api/v1/* (rewrite)  ┌──────────────────────────┐  JDBC  ┌────────────┐
│ Browser  │──────────► │ Next.js 16 (Vercel)          │──────────────────────►│ Spring Boot 4 API (JVM)  │──────► │ PostgreSQL │
│          │            │ UI only, no secrets          │                       │ auth, rules, moderation, │        │ (Flyway)   │
│          │──── direct signed upload ──────────────────────────────────────►  │ matching, imports,       │        └────────────┘
└──────────┘            └─────────────────────────────┘     Cloudinary ◄──────│ analytics                │
                                                                              └──────────────────────────┘
```

- **Frontend** (`frontend/`): App Router pages are client components that call the API through one typed client (`src/lib/api/client.ts`, `src/lib/api/endpoints.ts`). TanStack Query handles caching, loading and error states. Zod schemas mirror the server rules for early feedback only.
- **Same-origin proxy**: `next.config.ts` rewrites `/api/v1/*` to `API_PROXY_TARGET`. The browser therefore talks to one origin, and the refresh cookie is first-party (works with Safari/Chrome third-party-cookie restrictions). A cross-origin setup is still possible with `NEXT_PUBLIC_API_BASE_URL` + CORS + `SameSite=None; Secure`.
- **Backend** (`backend/`): layered packages — `controller` (thin HTTP), `service` (business rules, transactions), `repository` (Spring Data + a few JDBC aggregate queries), `entity`, `dto`, `mapper`, `security`, `importer`, `analytics`, `config`, `exception`.
- **Database**: PostgreSQL is the source of truth. Flyway (`db/migration`) owns the schema; Hibernate runs with `ddl-auto=validate`.

## Security model

| Concern | Implementation |
|---|---|
| Passwords | BCrypt (cost 12). Never logged or returned. |
| Session | 15-minute HS256 access token kept **in memory** by the SPA + 14-day opaque refresh token in an `HttpOnly` cookie scoped to `/api/v1/auth`. Refresh tokens are stored as SHA-256 hashes and **rotate** on every use; re-use of a rotated token revokes all of the user's sessions. |
| Identity per request | The JWT subject is resolved to the database user on every request (`DbUserJwtConverter`), so suspension and role changes apply immediately; the role claim in the token is informational only. |
| Authorisation | `/api/v1/admin/**` requires `ROLE_ADMIN` in the filter chain (before multipart parsing) and `@PreAuthorize` on admin controllers. Ownership is checked in services; other users' private/unpublished items return **404** to avoid leaking existence (IDOR). |
| CSRF | All state-changing endpoints except refresh/logout use a Bearer header, which browsers never attach automatically. Refresh/logout (cookie-authenticated) check the `Origin` header against `APP_FRONTEND_ORIGINS`; the cookie is `SameSite=Lax` by default. |
| CORS | Exact allow-list from `APP_FRONTEND_ORIGINS`; wildcards are refused at start-up in `prod`. |
| Rate limiting | In-memory fixed windows: login (30/5 min per IP, 10 **failed** attempts/15 min per account), registration, reviews, reports, imports, media signatures. Single-instance only — see deployment notes. |
| Errors | One JSON shape (`timestamp,status,code,message,fieldErrors,traceId`); no stack traces or SQL. Every response carries `X-Trace-Id`, which is also in the logs. |
| Input | Bean Validation on DTOs + `RecipeValidator` business rules; strict JSON (no float→int or string→number coercion); sort keys from an allow-list; all SQL parameterised; LIKE wildcards escaped. |
| Media | Only https URLs on allow-listed hosts (`APP_ALLOWED_IMAGE_HOSTS`); JPEG/PNG/WebP via signed Cloudinary uploads; no SVG. |
| Headers | API: Spring Security defaults (nosniff, frame deny, no-store). Web: nosniff, `X-Frame-Options: DENY`, strict referrer policy. |

## Data model (Flyway `V1__initial_schema.sql`)

- `users` (role USER/ADMIN; status ACTIVE/SUSPENDED/DELETED; lower-case unique email) and `refresh_tokens`.
- `ingredients` and `tags` with unique **normalised** names (trimmed, whitespace-collapsed, lower-case; checked in code and by DB constraints).
- `recipes` with CHECK constraints for every rule (`total_time_mins = prep + cook ≤ 60`, cost, servings, title length, https image), moderation fields, denormalised `rating_avg/rating_count`, `is_official`, `is_sample`, `import_job_id`.
- `recipe_ingredients` (PK recipe+ingredient, NUMERIC quantity, optional flag, order), `recipe_steps` (unique recipe+step_number), `recipe_tags`.
- `reviews` (one per user per recipe, rating 1–5, moderation), `content_reports`.
- `saved_recipes`, `user_cooked_history` (many per recipe), `user_goals` (target 1–30, month stored as the first day of the month).
- `bulk_upload_jobs` + `bulk_upload_errors`, `audit_logs`.

`V2__reference_ingredients_and_tags.sql` adds catalogue reference data (47 ingredients, 14 tags) for every environment. `db/devseed/R__dev_sample_recipes.sql` (dev profile only) adds 12 sample official recipes — no fake users, reviews or activity.

### Deletion and privacy rules

- Users are never hard-deleted. Account deletion (`DELETE /users/me`) anonymises the row (name “Deleted user”, placeholder email, credentials/phone/avatar removed), deletes private cookbook and goals, archives the user's unpublished recipes, revokes sessions, and keeps published recipes, reviews and cook history (needed for aggregates; no longer linked to personal data).
- `recipes.created_by` is `ON DELETE SET NULL`; nothing cascades from users into published content.
- Recipes are never hard-deleted by the app: owners “withdraw” and admins “archive” (status `ARCHIVED`).
- A user may delete their own review (row removed, aggregates recomputed, open reports about it closed). Moderator removals keep the row with status `REMOVED` and a reason.

## Key rules and where they live

| Rule | Code |
|---|---|
| Recipe validation (API **and** importer) | `service/RecipeValidator.java` |
| Time categories | `service/TimeCategory.java` (frontend mirror: `lib/format.ts`) |
| Portions and cost | `service/PortionCalculator.java` |
| Pantry ranking | `service/PantryMatcher.java` (pure) + `repository/PantryMatchRepository.java` (one grouped SQL query) |
| Goal progress | `service/GoalProgressCalculator.java` (computed from history; `completed_at` refreshed transactionally) |
| Rating aggregates | `service/RatingAggregateService.java` (recomputed from approved reviews in the same transaction as any review change) |
| Moderation transitions | `service/RecipeService.java`, `service/ReviewService.java`, `service/ReportService.java` |
| Import | `importer/*` (parsers → validator → per-row `REQUIRES_NEW` writer) |

## Notable decisions

- **Spring Boot 4.1 / Jackson 3 / Hibernate 7** — latest stable line compatible with Java 21.
- **Pantry matching** — SQL computes required/matched counts for candidate recipes (those sharing at least one selected ingredient); ranking and paging happen in a pure Java comparator so the documented order is exactly what is unit-tested. Three queries per request regardless of result size.
- **Goal “new recipes”** — a goal counts either distinct recipes cooked in the month (`UNIQUE_RECIPES`, default) or every cook event (`COOK_EVENTS`); “new” means new within that month. Months are evaluated in `Asia/Dhaka`.
- **Ingredient creation by import** — unknown ingredient names in an admin import create new active catalogue entries (category “Uncategorized”) and are reported as warnings. Community users can only pick existing active ingredients and tags.
- **Character counting** — all length rules count Unicode code points to match PostgreSQL (`char_length`, `varchar(n)`), so emoji count as one character.

# Bachelor Chef ERP — Implementation Checklist

Source contract: `AGENTS.md`. Course context: STQA project requirements.
Legend: `[x]` done and verified by running it · `[~]` done but not executable on this machine (see note) · `[ ]` not done.

## Environment notes (2026-10-13)

- Workspace was empty → repository created from scratch at this folder root.
- Backend targets Java 21 (`maven.compiler.release=21`), built and run with JDK 25; Spring Boot 4.1.1.
- Frontend: Next.js 16.4 / React 19.3 / TypeScript 5 / Tailwind 4, Node 24 LTS.
- No Docker on the machine → integration tests ran against a dedicated local PostgreSQL 18 test database (`TEST_DB_MODE=external`); Testcontainers and embedded modes exist but were not executed here (embedded binaries need the VC++ runtime — DEF-003).
- Portable Node/PostgreSQL and local DB data live in the git-ignored `.devtools/` folder.

## Phase 1 — Foundation
- [x] Monorepo layout, root README, `.gitignore`, `.env.example`, Makefile
- [~] `docker-compose.yml` PostgreSQL with health check + named volume (written, not run: no Docker)
- [x] Spring Boot project, Maven wrapper, layered packages
- [x] Flyway `V1__initial_schema.sql` (+ `V2` reference data, dev-only sample seed)
- [x] Profiles dev/test/prod, `ddl-auto=validate`, Actuator health, springdoc OpenAPI
- [x] Common error shape, global handler, trace IDs
- [x] Fresh-DB migration verified (twice, incl. after data loss), backend boots

## Phase 2 — Identity & authorization
- [x] Register / login / logout / refresh / me
- [x] BCrypt; in-memory JWT + rotating hashed refresh token in HttpOnly cookie; reuse detection
- [x] Origin check on cookie endpoints, CORS allow-list
- [x] Generic login errors; rate limits (failed logins only per account — DEF-022)
- [x] ACTIVE/SUSPENDED/DELETED enforced on every request
- [x] Env-driven admin bootstrap (never resets an existing account)
- [x] Security tests: 401 / 403 / IDOR / suspended user / role changes

## Phase 3 — Recipe domain & discovery
- [x] Ingredient + tag catalogue with normalized unique keys
- [x] Recipe aggregate, DTOs, shared validator, transactional writes
- [x] Time rules and categories (field vs calculated boundaries)
- [x] Catalogue search/filters/sort allow-list/pagination
- [x] Recipe detail visibility rules
- [x] Pantry matcher (strict/ranked, optional excluded, deterministic, 3 queries)
- [x] Portions + BigDecimal cost

## Phase 4 — Community & personal cooking
- [x] Submissions/edits with moderation transitions; protected fields ignored
- [x] Reviews (1–5, one per user, moderation, approved-only aggregates)
- [x] Cookbook, cooked history, goals (progress derived from history, Asia/Dhaka months)
- [x] Profile, password change, account deletion (anonymisation)
- [x] Content reports

## Phase 5 — Admin ERP
- [x] Official recipes: create/edit/approve/reject/archive/restore
- [x] Ingredient catalogue admin
- [x] Moderation queues (recipes, reviews incl. flagged, reports)
- [x] Audit log
- [x] CSV/JSON import: preview, ≤500 rows, ≤5 MB, per-row transactions, idempotent confirm, job log, CSV issue export
- [x] User management
- [x] Dashboard + analytics from real queries

## Phase 6 — Frontend
- [x] Typed API client, auth context, route guards
- [x] UI primitives and consistent states
- [x] All required public, user and admin routes (21 routes build)
- [x] Not-found/error states; keyboard-accessible controls; no horizontal scroll at 390 px (automated)

## Phase 7 — Tests, seed data, release
- [x] Dev seed (12 sample recipes), reference catalogue
- [x] Fixtures: valid/invalid CSV and JSON
- [x] Backend unit tests (287)
- [x] Integration (50) + security (23) tests on real PostgreSQL
- [x] Frontend lint, type-check, Vitest (42), production build
- [x] Selenium (11): admin CSV flow, student journey, guards, responsive
- [~] Backend Dockerfile (written, not built: no Docker); deployment docs
- [x] Docs: architecture, API, testing, deployment, database, e2e

## Phase 8 — STQA deliverables
- [x] Single Excel workbook generated from executed results (`docs/stqa/BachelorChef_TestCases.xlsx`)
- [x] Defect log of 24 real defects (with regression tests where applicable)
- [x] Report draft (`docs/stqa/report-draft.md`) — team names/contributions to be filled in

## Final acceptance (AGENTS.md §18)
- [~] Fresh checkout → documented setup succeeds (each step run here except `docker compose`)
- [x] Migrations from empty DB
- [x] Health + OpenAPI work
- [x] Frontend strict production build
- [x] Auth + role checks
- [x] Public shows only approved, non-archived
- [x] Matcher correct
- [x] Transactional recipe save
- [x] Portion/cost scaling correct
- [x] Reviews, moderation, aggregates, bookmarks, history, goals
- [x] Admin management & moderation
- [x] CSV/JSON import incl. 500/501 + row errors
- [x] No half-saved recipes on failure
- [x] Import history & analytics are persisted data
- [x] IDOR protection
- [x] Non-admins blocked from admin API
- [x] Tests pass (413/413); environment limits documented
- [x] No secrets committed (placeholders only; dev-only JWT fallback is refused by the prod profile)
- [x] README + `.env.example` match the implementation
- [ ] Deployed to Vercel/Render (not done — needs accounts and credentials)
- [ ] Cloudinary verified with a real account (needs credentials)

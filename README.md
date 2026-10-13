# Bachelor Chef ERP

Mobile-first recipe discovery and community cooking platform for students and busy people in Bangladesh, with an admin ERP portal.
Pick the ingredients you have, find meals you can cook in 20 minutes or less, see the estimated cost in ৳, save recipes, log what you cooked, set monthly goals, and share recipes with the community. Administrators manage recipes and ingredients, moderate content, bulk-import recipes from CSV/JSON and see real analytics.

| Part | Stack | Folder |
|---|---|---|
| API | Java 21, Spring Boot 4.1, Spring Security (JWT + rotating HttpOnly refresh cookie), JPA/Hibernate 7, Flyway, PostgreSQL | [backend/](backend/) |
| Web app | Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4, TanStack Query, React Hook Form + Zod | [frontend/](frontend/) |
| UI automation | Selenium 4 + JUnit 5 (Java) | [e2e/](e2e/) |
| Docs | Architecture, API, testing (BVA / ECT / decision tables / basis paths), deployment, STQA workbook | [docs/](docs/) |

```
Browser ──► Next.js (UI, same-origin proxy /api/v1/*) ──► Spring Boot REST API (/api/v1) ──► PostgreSQL
```

## One-click on Windows (10/11)

| File | What it does |
|---|---|
| `install.bat` | First time (and after pulling new code). Downloads portable **JDK 21, Node.js 24 and PostgreSQL 18** into `.devtools\` only if they are missing (no admin rights, nothing installed system-wide; ~600 MB). Creates `.env` with random secrets and a random admin password, creates a private database on port **5433**, builds the API and the web app, and prints the admin login. Safe to run again. |
| `run.bat` | Starts the database, the API (own window) and the web app (own window), waits until they are healthy and opens <http://localhost:3000>. |
| `stop.bat` | Stops the web app, the API and the database started by `run.bat`. |

Just double-click `install.bat` once, then `run.bat`. The admin email and password are in `.env` (`BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`). Ports used: 3000 (web), 8080 (API), 5433 (database). To start over with an empty database, run `stop.bat`, delete `.devtools\pgdata`, `.devtools\pgpw.txt` and `.env`, then run `install.bat`.

## Quick start (manual, any OS)

Prerequisites: **JDK 21+**, **Node.js 24 LTS** (20.9+ works), and **PostgreSQL 16+** — either Docker or a local install.
Maven is not needed (the `mvnw` wrapper downloads it).

### 1. Configure

```bash
cp .env.example .env                       # PowerShell: Copy-Item .env.example .env
cp frontend/.env.example frontend/.env.local
```

Edit `.env`: set `POSTGRES_PASSWORD`/`SPRING_DATASOURCE_PASSWORD`, a random `APP_JWT_ACCESS_SECRET` (≥ 32 characters), and `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` (≥ 12 characters) for your first admin.

### 2. Start PostgreSQL

```bash
docker compose up -d db                    # waits for the health check; data persists in a named volume
```

Without Docker, create the role and database yourself:
`CREATE ROLE bachelor_chef_app LOGIN PASSWORD '…'; CREATE DATABASE bachelor_chef OWNER bachelor_chef_app;`

### 3. Run the API

The backend reads environment variables, not the `.env` file. Export them in your shell first:

```bash
# Git Bash / macOS / Linux
set -a; source .env; set +a
cd backend && ./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" ($v -replace '\s+#.*$','') }
cd backend; .\mvnw.cmd spring-boot:run
```

On start-up Flyway creates/updates the schema, the `dev` profile loads 12 **sample recipes** (flagged `is_sample`), and the bootstrap admin is created if that email does not exist yet.
Check readiness: <http://localhost:8080/actuator/health> → `{"status":"UP"}`. API docs: <http://localhost:8080/swagger-ui.html>.

### 4. Run the web app

```bash
cd frontend
npm ci
npm run dev                                # http://localhost:3000
```

Log in with the bootstrap admin to reach `/admin`, or register a normal account.

## Tests

Testing is organised for the STQA course: three members, one controller each. See **[docs/stqa/README.md](docs/stqa/README.md)** for who does what, and [docs/stqa/RESULTS.md](docs/stqa/RESULTS.md) for the latest numbers.

| Command | What runs | Needs |
|---|---|---|
| `cd backend && ./mvnw test -Dmaven.test.failure.ignore=true` | 377 JUnit tests: 90 controller tests (BVA, ECT, basis path; 30 per member) + 287 unit tests of the rest of the backend | nothing |
| `cd e2e && ./mvnw test -Dmaven.test.failure.ignore=true` | 16 Selenium tests (student journey, guards, CSV import, 390 px layout, controller defect checks) | API + web app running (`run.bat`), Chrome, `E2E_ADMIN_*` and `E2E_USER_*` |
| `python docs/stqa/generate_reports.py --collect` | Rebuilds every STQA workbook and report from the JUnit XML | `pip install openpyxl`, Graphviz |
| `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` | ESLint, strict TypeScript, 42 Vitest tests, production build | Node |

**30 defects are seeded on purpose**, 10 in each of the three controllers under test, so some tests fail by design (43 JUnit, 6 Selenium). They are listed in [docs/stqa/Inzamam/BachelorChef_DefectReport.md](docs/stqa/Inzamam/BachelorChef_DefectReport.md). The earlier integration/security suite is kept in a local backup, not in the repository.

## Useful facts

- **Roles**: `USER` and `ADMIN`. Nobody can register as admin; roles change only through `/admin/users` (audited).
- **Moderation**: community recipes and every review start as `PENDING` and are invisible to the public until approved.
- **Time rules**: prep 0–60, cook 1–60, total (computed) ≤ 60. Express ≤ 15, Bachelor Quick 16–20, Extended 21–60 minutes.
- **Money**: estimated total cost in BDT for the base servings, scaled linearly; never a live price.
- **Bulk import**: CSV or JSON, ≤ 500 recipes and ≤ 5 MB per file; preview first, then confirm (idempotent). Format in [docs/api.md](docs/api.md#bulk-import-formats).
- **Images**: signed direct uploads to Cloudinary when `CLOUDINARY_*` is set; otherwise users paste an https link from an allowed host.

## Resetting the local database (development only)

```bash
docker compose down -v && docker compose up -d db   # deletes the local volume — never do this in production
```

## Documentation

- [docs/architecture.md](docs/architecture.md) — components, security model, data model, key decisions
- [docs/api.md](docs/api.md) — endpoints, error format, status transitions, import formats
- [docs/testing.md](docs/testing.md) — test strategy, BVA, ECT, decision tables, basis paths, how to run
- [docs/deployment.md](docs/deployment.md) — Vercel + Render (Docker) + managed PostgreSQL
- [docs/deploy-free.md](docs/deploy-free.md) — step-by-step **free** hosting: Vercel + Render (`render.yaml`) + Neon
- [database/README.md](database/README.md) — migrations, seed data, backups
- [e2e/README.md](e2e/README.md) — Selenium setup
- [CHECKLIST.md](CHECKLIST.md) — implementation checklist and status

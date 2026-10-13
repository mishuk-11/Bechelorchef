# Deployment

Recommended low-cost setup: **frontend on Vercel**, **API as a Docker web service on Render** (or Railway/Fly.io), **managed PostgreSQL** (Render, Neon, Supabase…), **Cloudinary** for images.
Do not run the Spring Boot JVM as a Vercel Function.

**Free, click-by-click version:** [deploy-free.md](deploy-free.md) (uses the Render Blueprint in [`render.yaml`](../render.yaml) and Neon).

> Not verified in this repository: no deployment was performed and the Docker image was not built here (Docker was unavailable on the development machine). The steps below follow the platforms' documented behaviour; run the production checklist after your first deploy.

## 1. Database

1. Create a PostgreSQL 16+ instance. Create an application role that owns only this database (not a superuser).
2. Note the JDBC URL (`jdbc:postgresql://host:5432/db?sslmode=require`), user and password.
3. Enable automated backups (daily + point-in-time if offered). Test a restore before going live.

Flyway runs on API start-up and applies new migrations in order (`V1`, `V2`, …). Never edit an applied migration; add a new one. The `prod` profile does **not** load the sample recipes in `db/devseed`.

## 2. API (Render, Docker)

1. New → Web Service → connect the repository, **Root directory** `backend`, runtime **Docker** (uses `backend/Dockerfile`: JDK 21 build stage, JRE 21 runtime, non-root user, `MaxRAMPercentage=75`).
2. Health check path: `/actuator/health/readiness`.
3. Environment (secrets in the platform's secret manager):

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | from step 1 |
| `APP_JWT_ACCESS_SECRET` | ≥ 32 random bytes, e.g. `openssl rand -base64 48` |
| `APP_FRONTEND_ORIGINS` | `https://your-app.vercel.app` (exact; comma-separate extra domains) |
| `APP_COOKIE_SECURE` | `true` (required by the prod profile) |
| `APP_COOKIE_SAME_SITE` | `Lax` (same-origin proxy, recommended) |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | set for the first deploy only, then remove; existing accounts are never modified |
| `CLOUDINARY_CLOUD_NAME` / `_API_KEY` / `_API_SECRET` | from Cloudinary (optional) |
| `APP_ALLOWED_IMAGE_HOSTS` | `res.cloudinary.com` plus any other hosts you allow |
| `PORT` | Set by the platform; the API listens on `SERVER_PORT`, else `PORT`, else 8080 |

The `prod` profile fails fast if the JWT secret is missing/weak, origins contain wildcards, or secure cookies are off.
`server.forward-headers-strategy=framework` is enabled in `prod` so client IPs (rate limits) come from the proxy's `X-Forwarded-For`.

Free tiers may sleep after inactivity (first request is slow) and have limited RAM; the rate limiter is in-memory, so keep a single instance or put an edge rate limiter in front if you scale out.

## 3. Frontend (Vercel)

1. Import the repository, **Root Directory** `frontend`, framework Next.js.
2. Environment:
   - `API_PROXY_TARGET=https://your-api.onrender.com` (server-side only; used by the rewrite)
   - `NEXT_PUBLIC_API_BASE_URL=/api/v1` (keep the default)
3. Deploy. The browser now calls `https://your-app.vercel.app/api/v1/...`, which Vercel proxies to the API, keeping the refresh cookie first-party.

Preview deployments have different origins. Either add the specific preview domain to `APP_FRONTEND_ORIGINS` of a **staging** API, or do not point previews at the production API. Never use wildcards for production.

Images are rendered with plain `<img>` from https URLs that the API already restricted to `APP_ALLOWED_IMAGE_HOSTS`; no `next/image` remote patterns are needed.

## 4. Environments

| | Development | Staging | Production |
|---|---|---|---|
| Profile | `dev` (sample recipes) | `prod` | `prod` |
| Database | local Docker/PostgreSQL | separate managed DB | managed DB with backups |
| Origins | `http://localhost:3000` | staging/preview domain | production domain only |
| Admin bootstrap | env vars | env vars, then removed | env vars for first deploy, then removed |
| Cloudinary | optional | test folder | production folder |

## 5. Production acceptance checklist

- [ ] Frontend and API are served over HTTPS.
- [ ] `GET https://api…/actuator/health` → `UP`; `/swagger-ui.html` reachable (or restrict it at the edge if you prefer).
- [ ] A request from a non-allowed origin to `/api/v1/auth/refresh` gets 403; the real frontend works.
- [ ] Register, log in, reload the page (session restored via refresh), log out.
- [ ] Submit a recipe with an uploaded image; it is `PENDING` and invisible when logged out.
- [ ] Non-admin cannot open `/admin` or call `/api/v1/admin/*` (403).
- [ ] Admin import of a 500-row CSV works; 501 rows is rejected.
- [ ] Redeploy/restart: data persists.
- [ ] Logs contain no tokens, passwords or Cloudinary secrets (search for `Bearer`, `bc_refresh`, `password`).
- [ ] Backups are scheduled and a restore was tested.

## 6. Operations

- **Secret rotation**: changing `APP_JWT_ACCESS_SECRET` invalidates all access tokens (users refresh automatically; refresh tokens stay valid). To force everyone to log in again, also run `UPDATE refresh_tokens SET revoked_at = now() WHERE revoked_at IS NULL;`. Rotate DB and Cloudinary credentials in the provider, then update the env vars and redeploy.
- **Backups**: rely on the managed provider's snapshots; for a manual dump: `pg_dump --format=custom --no-owner "$DATABASE_URL" > backup.dump`.
- **Never** run `docker compose down -v` or drop/clean scripts against production.

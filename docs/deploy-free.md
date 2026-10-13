# Free deployment: Vercel + Render + Neon

The free route, which needs no credit card:

| Part | Host | Free plan |
|---|---|---|
| Web app (`frontend/`) | **Vercel** Hobby | always on |
| API (`backend/`, Docker) | **Render** free web service | 512 MB RAM; sleeps after 15 min idle; first request after sleep takes about 1 min |
| Database | **Neon** free PostgreSQL | about 0.5 GB; wakes in about 1 s |

Free plans change. Check each provider's pricing page before relying on these limits.

Follow the steps **in this order**: database → API → web app → connect the two.

---

## Step 1: Database on Neon (about 5 min)

1. Sign up at **https://neon.tech** (GitHub login works).
2. **Create project**: name `bechelorchef`, Postgres version 16 or newer, region **AWS Asia Pacific (Singapore)**.
3. Open **Connection details**:
   - **Turn "Connection pooling" OFF**. You need the **direct** host, without `-pooler` in the name. Flyway migrations need a direct connection.
   - Copy the host, database, user and password.
4. Build the JDBC URL from those parts. Keep `?sslmode=require` on the end:
   ```
   jdbc:postgresql://<host>/<database>?sslmode=require
   e.g. jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```

You don't need to create any tables; the API does that on its first start (Flyway).

---

## Step 2: API on Render (about 10 min, plus a 5–10 min first build)

1. Sign up at **https://render.com** with GitHub and allow it to see the `Bechelorchef` repository.
2. **New + → Blueprint →** choose the repository. Render reads [`render.yaml`](../render.yaml) and proposes the service `bachelor-chef-api` (free plan, Singapore, Docker).
3. Render asks for the values marked `sync: false`:

   | Key | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | the JDBC URL from step 1 |
   | `SPRING_DATASOURCE_USERNAME` | Neon user |
   | `SPRING_DATASOURCE_PASSWORD` | Neon password |
   | `APP_FRONTEND_ORIGINS` | for now `https://example.invalid`; replace it with the Vercel URL in step 4 |
   | `BOOTSTRAP_ADMIN_EMAIL` | e.g. `inzamam@admin.su` |
   | `BOOTSTRAP_ADMIN_PASSWORD` | **at least 12 characters**, e.g. a strong passphrase (the app refuses shorter ones) |

   `APP_JWT_ACCESS_SECRET` is generated automatically. `SPRING_PROFILES_ACTIVE=prod`, secure cookies, the small connection pool and the sample-recipe seed are already set.
4. Click **Apply**. The first Docker build takes about 5–10 minutes. Wait for **Live**.
5. Note the URL, e.g. `https://bachelor-chef-api.onrender.com`, and test it in the browser:
   - `https://bachelor-chef-api.onrender.com/actuator/health` → `{"status":"UP"}`
   - `https://bachelor-chef-api.onrender.com/api/v1/recipes` → JSON with the 12 sample recipes

**If the deploy fails:** open **Logs**.
- `Invalid configuration: ...` → a variable is missing or too short (JWT secret, origins, secure cookie).
- `Connection refused` / SSL errors → check the JDBC URL (`?sslmode=require`, direct host).
- `OutOfMemoryError` → the free 512 MB is too small at that moment; click **Manual Deploy → Deploy latest commit** to retry, or move the API to a bigger host. The image is already tuned: a local test with a 512 MB limit used about 300 MB.

---

## Step 3: Web app on Vercel (about 5 min)

1. Sign up at **https://vercel.com** with GitHub.
2. **Add New → Project →** import `Bechelorchef`.
3. **Root Directory: `frontend`** (click *Edit* next to it). The framework is detected as Next.js.
4. **Environment Variables** (before the first deploy, because the API address is baked in at build time):

   | Name | Value |
   |---|---|
   | `API_PROXY_TARGET` | your Render URL, e.g. `https://bachelor-chef-api.onrender.com` (no trailing `/`) |
   | `NEXT_PUBLIC_API_BASE_URL` | `/api/v1` |

5. **Deploy**, then note the URL, e.g. `https://bechelorchef.vercel.app`.

The browser only talks to the Vercel site. Vercel forwards `/api/v1/*` to Render, so the login cookie stays on your own domain.

---

## Step 4: Connect them (required, or login fails)

1. Render → `bachelor-chef-api` → **Environment** → set `APP_FRONTEND_ORIGINS` to the **exact** Vercel URL, e.g. `https://bechelorchef.vercel.app`.
   - Use `https`, no trailing `/` and no `*`.
   - For several domains, separate them with commas.
2. **Save changes**. Render restarts the API.
3. After the first successful start, **delete** `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` from Render's environment. The admin account stays.

If you ever change `API_PROXY_TARGET` on Vercel, **redeploy** the Vercel project (Deployments → … → Redeploy). Rewrites are fixed at build time.

---

## Step 5: Check the live site

- [ ] `https://<vercel-app>` opens and shows sample recipes. The first visit after the API slept can take about a minute.
- [ ] Register a new user, log in, reload the page (you stay logged in), log out.
- [ ] Log in as the bootstrap admin → `/admin` works; a normal user sees "Administrators only".
- [ ] Pantry matcher, save a recipe, "I cooked this", create a goal.
- [ ] Admin → Bulk import → upload `backend/src/test/resources/bulk_recipes.csv` → preview → import.

---

## Notes

- **Seeded STQA defects:** the 30 seeded defects are in the deployed code, so the live site behaves like the tested version. For example, goal target 30 is rejected and the admin role filter is ignored.
- **Accounts:** local accounts (`user1@user.com` …) are **not** online. Online you start with the bootstrap admin; register other users on the site. Online accounts can't use `123456`, because registration needs 8 or more characters.
- **Sample recipes:** they come from `SPRING_FLYWAY_LOCATIONS=...db/devseed` in `render.yaml` and are flagged `is_sample`. Remove that line for a real production site.
- **Images:** to allow uploads, add `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY` and `CLOUDINARY_API_SECRET` (free account at cloudinary.com) in Render → Environment. Without them, users paste https image links.
- **Sleeping:** to keep the API awake during a demo, open the site a minute before, or use a free uptime pinger on `/actuator/health` every 10 minutes.
- **Vercel preview deployments:** previews get other URLs. They only work if that exact URL is added to `APP_FRONTEND_ORIGINS`.
- **Updating:** pushing to `main` redeploys automatically. Render only rebuilds when `backend/**` changes; Vercel rebuilds on every push.

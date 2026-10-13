# Bachelor Chef — web app

Next.js 16 (App Router) + React 19 + TypeScript + Tailwind CSS 4. All data comes from the Spring Boot API; this app holds no secrets.

```bash
cp .env.example .env.local   # NEXT_PUBLIC_API_BASE_URL=/api/v1, API_PROXY_TARGET=http://localhost:8080
npm ci
npm run dev                  # http://localhost:3000
```

| Script | Purpose |
|---|---|
| `npm run dev` / `build` / `start` | develop, production build, serve the build |
| `npm run lint` | ESLint (Next.js core-web-vitals + TypeScript + React Compiler rules) |
| `npm run typecheck` | strict `tsc --noEmit` |
| `npm test` | Vitest unit tests (`src/**/*.test.ts`) |

## Structure

| Path | Contents |
|---|---|
| `src/app/` | routes (public, signed-in, `/admin/*`), `providers.tsx` (TanStack Query, toasts, auth) |
| `src/lib/api/` | the only HTTP client (`client.ts`: in-memory access token, single-flight refresh) and typed endpoints |
| `src/features/` | auth context, recipe card/editor/reviews, pantry ingredient picker, image field, admin widgets |
| `src/components/` | UI primitives (button, fields, modal, table, pagination, toast, states) and layout/guards |
| `src/schemas/` | Zod schemas mirroring the API rules (usability only — the API validates again) |
| `src/types/api.ts` | DTO types |

`/api/v1/*` is rewritten to `API_PROXY_TARGET` (see `next.config.ts`), so the browser uses one origin and the refresh cookie stays first-party. See the root [README](../README.md) and [docs/deployment.md](../docs/deployment.md).

# Database

PostgreSQL is the source of truth. The schema is owned by **Flyway** migrations inside the API:

| File | Purpose | Environments |
|---|---|---|
| `backend/src/main/resources/db/migration/V1__initial_schema.sql` | All tables, foreign keys, CHECK constraints, unique and partial indexes | all |
| `backend/src/main/resources/db/migration/V2__reference_ingredients_and_tags.sql` | Catalogue reference data: 47 common ingredients, 14 tags | all |
| `backend/src/main/resources/db/devseed/R__dev_sample_recipes.sql` | 12 sample official recipes (`is_sample = true`), idempotent repeatable migration | `dev` profile only |

Hibernate runs with `ddl-auto=validate`: if an entity and the schema disagree, the API refuses to start.

## Rules

- Add changes as new versioned files (`V3__describe_change.sql`); never edit a migration that has run anywhere shared.
- Keep constraints in SQL as well as in Java (the API validates first for friendly messages; the database is the last line of defence).
- Seed data never includes users, passwords, reviews or activity. The first admin comes from `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`.

## Local reset (development only)

```bash
docker compose down -v && docker compose up -d db     # removes the named volume bachelor_chef_pgdata
```

Without Docker: `DROP DATABASE bachelor_chef; CREATE DATABASE bachelor_chef OWNER bachelor_chef_app;` then restart the API.
Never run either against production.

## Backups

```bash
pg_dump --format=custom --no-owner -h HOST -U USER bachelor_chef > bachelor_chef.dump
pg_restore --clean --no-owner -h HOST -U USER -d bachelor_chef bachelor_chef.dump
```

In production use the managed provider's automated backups and test a restore regularly.

## Integration-test database

`./mvnw verify` needs a disposable PostgreSQL (tests truncate tables). Chosen automatically (`TEST_DB_MODE=auto`):
`TEST_DB_URL` set → that database; Docker available → Testcontainers `postgres:17-alpine`; otherwise embedded PostgreSQL binaries (zonky; on Windows requires the Microsoft Visual C++ runtime).

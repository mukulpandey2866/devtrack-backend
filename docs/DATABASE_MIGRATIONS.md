# Database migrations, baseline and recovery

The Neon database was created by hand, so Flyway has no history table there. This application **refuses to
start against it until it is baselined once**. That is intentional: it can never recreate or alter the existing tables
by accident.

## How it works

| Situation | What Flyway does |
|---|---|
| Empty database (local, CI, a new Neon branch) | Runs `V1__baseline_schema.sql`, creating the nine tables, indexes, the `set_updated_at()` function and its four triggers |
| Existing database, no history table, baseline **off** (default) | Fails at startup with "non-empty schema without schema history table". Nothing is changed |
| Existing database, baseline **on** (`FLYWAY_BASELINE_ON_MIGRATE=true`) | Records version 1 as `BASELINE` without running V1, then applies only newer migrations (V2, V3, ...) |
| Already baselined | Applies only pending migrations |

`spring.jpa.hibernate.ddl-auto=validate` stays on everywhere, so Hibernate only checks the schema and never changes it.
`spring.flyway.clean-disabled=true` stops anyone from wiping the database through Flyway.

The behaviour above is covered by `MigrationIntegrationTest` on a throwaway PostgreSQL container. It verifies that
baselining leaves existing data, triggers and indexes untouched and that an un-baselined existing schema is refused.

## One-time baseline of the existing Neon database

1. **Back up first.** In the Neon console create a branch of the production branch (a point-in-time copy), or run `pg_dump`.
2. **Verify the schema.** Run [`db/verify-schema.sql`](db/verify-schema.sql) in the Neon SQL editor. Check that:
   - 9 tables exist, 4 `set_updated_at` triggers exist, `flyway_schema_history` does not exist;
   - the columns match the schema in your task description (types, lengths, nullability);
   - the final three counts are `0`.
3. **Fix any mismatch with a new migration (V2)**, not by editing V1. Do not baseline until the schema matches V1.
4. **Start the app once with the baseline flag**, using a *direct* (non-pooled) connection for Flyway (see below):
   `FLYWAY_BASELINE_ON_MIGRATE=true`.
5. Confirm in the logs that Flyway reports a baseline at version 1, then **set the flag back to `false`**
   (or remove it). It is only needed once.

### Neon pooled versus direct connection

The Neon `-pooler` hostname uses PgBouncer in transaction mode. Flyway relies on session-level advisory locks, which
do not work reliably through it. Use the pooled URL for the application and a direct URL for migrations by setting
the Spring properties through environment variables:

```
SPRING_FLYWAY_URL=jdbc:postgresql://<direct-host>/<db>?sslmode=require
SPRING_FLYWAY_USER=<user>
SPRING_FLYWAY_PASSWORD=<password>
```

If these are unset, Flyway uses `DB_JDBC_URL`, which works too when the connection string is already direct.

## Adding a future migration

1. Add `src/main/resources/db/migration/V2__short_description.sql` (never edit an applied migration).
2. Make it safe to apply to existing data: additive changes first (`ADD COLUMN` nullable or with a default), backfill,
   then add `NOT NULL`/constraints in a later migration if needed.
3. Keep the matching JPA mapping change in the same commit. `ddl-auto=validate` fails the start if they disagree.
4. Run `mvn test` (the migration test and all integration tests apply every migration to a clean database).
5. Deploy. Flyway applies it on startup.

## Rollback and recovery

Flyway Community has no automatic undo. Plan recovery before deploying a migration:

- **Before each risky migration**, create a Neon branch or snapshot. Restoring that branch is the rollback.
- **Application rollback:** redeploy the previous image. Because migrations are additive, the older version keeps working.
- **Failed migration:** PostgreSQL DDL is transactional, so a failed migration rolls back and leaves no history row.
  Fix the SQL in a new version if it was never applied, and redeploy.
- **Checksum mismatch** ("Migration checksum mismatch") means an applied migration file was edited. Restore the original
  file. Only as a last resort and after a backup, run `flyway repair` against the direct URL.
- **Wrong baseline:** if baselined by mistake, `DELETE FROM flyway_schema_history;` removes only the history table's
  rows (your data is untouched), after which you can baseline again.

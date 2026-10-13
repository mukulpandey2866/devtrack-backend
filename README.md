# DevTrack Backend

Spring Boot 4.1 / Java 21 REST API for a Jira-like project and issue tracker: users, projects with role-based
membership, issues with concurrency-safe numbering and audit history, comments, multiple assignees and labels.
PostgreSQL (Neon) is the database; schema changes are managed by Flyway.

## Architecture

```
controller  ->  service  ->  repository  ->  PostgreSQL
   |              |
  dto          entity / enums           security (JWT) | exception (ProblemDetail) | mapper | config
```

| Package (`com.devtrack.devtrack_backend`) | Responsibility |
|---|---|
| `controller` | HTTP routing only; identity comes from `@AuthenticationPrincipal` |
| `service` | Business rules, **resource-level authorization** (`ProjectAccessService`), transactions |
| `repository` | Spring Data JPA, row locks, dynamic issue filters (`IssueSpecifications`) |
| `entity` / `enums` | JPA mappings (UUID, `timestamptz`, `numeric(8,2)`, composite keys, string enums); no entity relationships, so no lazy-loading or N+1 surprises |
| `dto` / `mapper` | API contracts; entities are never serialized |
| `security` / `config` | Stateless JWT, CORS, BCrypt, OpenAPI |
| `exception` | One `@RestControllerAdvice` producing RFC 9457 `application/problem+json` |

Key design decisions:

- **Information disclosure:** a caller who is not a project member gets `404` for the project and everything in it;
  a member with too little privilege gets `403`.
- **Role hierarchy:** `OWNER > MANAGER > MEMBER > VIEWER`. Viewers are read-only; MEMBER+ can create/edit issues,
  comment, assign and label; MANAGER+ manages labels, deletes issues and manages members below their rank;
  only OWNER can archive/unarchive, grant `OWNER`/`MANAGER` or manage managers. A project always keeps one owner.
- **Issue numbers:** `max(issue_number) + 1` is computed while holding a `SELECT ... FOR UPDATE` lock on the project row,
  with the `UNIQUE(project_id, issue_number)` constraint as a backstop (verified with a 20-thread test).
- **Project lifecycle:** `DELETE /api/projects/{id}` **archives** (owner only). Archived projects are read-only until an
  owner sets another status. The project key is immutable.
- **Issue deletion:** hard delete by MANAGER/OWNER; comments, assignments, history and label links are removed with it.
  Use status `CANCELLED` to retire an issue and keep its record.
- **History:** written only by the services in the same transaction as the change; there is no write endpoint.
  Tracked fields: `created`, `title`, `description`, `issueType`, `priority`, `status`, `dueDate`, `estimatedHours`,
  `assignee`, `label`.
- **Time:** all timestamps are `timestamptz`, handled in UTC (`APP_TIME_ZONE`).

## Prerequisites

- JDK 21 and Maven 3.9+ (or the included `mvnw`)
- Docker (only for the test suite and for container builds)
- A PostgreSQL database; Neon for deployment

Dependencies come from public Maven Central only.

## Local development with Neon

1. Copy `.env.example` to `.env` (git-ignored) and fill in the placeholders. Values must not be quoted.
   - `DB_JDBC_URL` must keep `sslmode=require`, e.g. `jdbc:postgresql://<neon-host>/<db>?sslmode=require`.
   - Generate `JWT_SECRET` with `openssl rand -base64 48` (minimum 32 bytes).
2. First run against the **existing** Neon database only: follow [docs/DATABASE_MIGRATIONS.md](docs/DATABASE_MIGRATIONS.md)
   (verify, back up, baseline once).
3. Run:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile reads `.env`, allows `http://localhost:5173` and `http://localhost:3000` by default and enables Swagger UI.
Instead of `.env` you can set real environment variables (PowerShell: `$env:DB_JDBC_URL="..."`).

The app has **no default database URL, username, password or JWT secret**; a missing value stops startup with a message
naming the variable (never its value).

## Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_JDBC_URL` | yes | none | PostgreSQL JDBC URL (keep `sslmode=require` for Neon) |
| `DB_USERNAME` / `DB_PASSWORD` | yes | none | Database credentials |
| `JWT_SECRET` | yes | none | HS256 signing key, at least 32 bytes |
| `CORS_ALLOWED_ORIGINS` | prod | empty (no cross-origin) | Comma-separated explicit origins; `*` is rejected |
| `JWT_ISSUER` / `JWT_AUDIENCE` | no | `devtrack-backend` / `devtrack-frontend` | Validated on every token |
| `JWT_ACCESS_TOKEN_TTL` | no | `15m` | Between `1m` and `24h` |
| `PORT` | no | `8080` | HTTP port (set by most hosts) |
| `SPRING_PROFILES_ACTIVE` | no | none (`prod` in the image) | `local` or `prod` |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` | no | `5` / `1` | Keep within your Neon connection limit |
| `DB_CONNECTION_TIMEOUT_MS` / `DB_QUERY_TIMEOUT_MS` | no | `20000` / `15000` | Allows for Neon cold starts |
| `FLYWAY_ENABLED` | no | `true` | Disable only with good reason |
| `FLYWAY_BASELINE_ON_MIGRATE` | no | `false` | `true` for the one-time baseline only |
| `SPRING_FLYWAY_URL` / `_USER` / `_PASSWORD` | no | datasource | Direct (non-pooled) Neon connection for migrations |
| `APP_TIME_ZONE` | no | `UTC` | JVM and Hibernate JDBC time zone |
| `SHUTDOWN_TIMEOUT` | no | `25s` | Graceful shutdown wait |
| `GEMINI_API_KEY` | for AI | empty (AI endpoint returns 503) | Google AI Studio key; set only in `.env` (untracked) or the host's secret store |
| `GEMINI_MODEL` | no | `gemini-3.5-flash-lite` | Any text model from the Gemini models page (e.g. `gemini-3.8-flash`) |
| `GEMINI_CONNECT_TIMEOUT` / `GEMINI_READ_TIMEOUT` | no | `5s` / `45s` | Provider timeouts (one retry on provider 503) |
| `GEMINI_MAX_OUTPUT_TOKENS` | no | `2048` | Output cap per draft |
| `AI_RATE_LIMIT_PER_MINUTE` | no | `10` | Per-user, per instance |

## Tests

```bash
mvn test
```

Integration tests start a throwaway `postgres:16-alpine` container through Testcontainers, so **Docker must be running**,
and they never contact Neon. Data is truncated before every test. If Docker is unavailable the integration tests fail
rather than silently switching to a different database. Coverage includes registration, login, JWT validation, CORS,
project/member authorization, issue lifecycle and history, concurrent numbering, assignees, labels, comments,
pagination/filtering, validation errors and the Flyway baseline procedure.

## Authentication flow

1. `POST /api/auth/register` creates a `USER` (any `platformRole` in the body is ignored). Emails are trimmed and lower-cased.
2. `POST /api/auth/login` returns a short-lived signed access token.
3. Send `Authorization: Bearer <token>` on every other request. The token is verified (signature, expiry, issuer, audience,
   subject) and the user is reloaded from the database on each request, so a deactivated user loses access immediately.
4. `GET /api/auth/me` returns the current user.

**Token lifecycle:** access tokens are stateless and expire after `JWT_ACCESS_TOKEN_TTL`. There is deliberately **no
logout or refresh endpoint**: a stateless JWT cannot be revoked without server-side state, so pretending to would be
misleading. The client logs out by discarding the token. Add refresh tokens with a revocation table if you need
longer sessions. CSRF protection is disabled because the API is stateless and uses bearer headers, not cookies.

```http
POST /api/auth/login
{"email":"ada@example.com","password":"<password>"}

200 OK
{"accessToken":"<jwt>","tokenType":"Bearer","expiresIn":900,
 "user":{"id":"...","fullName":"Ada","email":"ada@example.com","platformRole":"USER","createdAt":"..."}}
```

Errors use `application/problem+json`:

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"Request validation failed",
 "errors":[{"field":"email","message":"must be a well-formed email address"}]}
```

## API reference

Interactive docs (local profile only): `http://localhost:8080/swagger-ui.html`, spec at `/v3/api-docs`.
They are disabled in the `prod` profile. Collection endpoints take `page` (from 0), `size` (max 100, default 20) and `sort`
(allow-listed fields; others return 400).

| Method and path | Purpose | Minimum role |
|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` | Authentication | public / public / any |
| `POST /api/projects` | Create (caller becomes OWNER) | any user |
| `GET /api/projects?status=` | Projects you belong to (paged) | member |
| `GET /api/projects/{id}` | Project details | VIEWER |
| `PUT /api/projects/{id}` | Update name, description, status | MANAGER (archive/unarchive: OWNER) |
| `DELETE /api/projects/{id}` | Archive | OWNER |
| `GET /api/projects/{id}/members` | List members | VIEWER |
| `POST /api/projects/{id}/members` | Add by `email` and `role` | MANAGER (can grant below own rank) |
| `PUT /api/projects/{id}/members/{userId}` | Change role | MANAGER (OWNER for manager/owner) |
| `DELETE /api/projects/{id}/members/{userId}` | Remove (anyone may remove themselves) | MANAGER |
| `POST /api/projects/{id}/issues` | Create issue (reporter = caller) | MEMBER |
| `GET /api/projects/{id}/issues` | List; filters `status`, `priority`, `type` (repeatable), `reporterId`, `assigneeId`, `labelId`, `dueBefore`, `dueAfter`, `q`; sort `createdAt`, `updatedAt`, `issueNumber`, `dueDate`, `title` | VIEWER |
| `GET /api/issues/{id}` | Issue details | VIEWER |
| `PUT /api/issues/{id}` | Update editable fields | MEMBER |
| `PATCH /api/issues/{id}/status` | Change status | MEMBER |
| `DELETE /api/issues/{id}` | Hard delete with dependents | MANAGER |
| `GET /api/issues/{id}/history` | Read-only audit trail (paged) | VIEWER |
| `POST/GET /api/issues/{id}/comments` | Add (MEMBER) / list (VIEWER, paged) | see left |
| `PUT /api/comments/{id}` | Edit own comment | MEMBER, author only |
| `DELETE /api/comments/{id}` | Delete | author, or MANAGER |
| `POST/GET /api/issues/{id}/assignees`, `DELETE .../{userId}` | Assign (must be a project member), list, unassign | MEMBER / VIEWER |
| `POST/GET /api/projects/{id}/labels` | Create (MANAGER) / list (VIEWER); `color` is `#RRGGBB` | see left |
| `PUT/DELETE /api/labels/{id}` | Rename/recolor, delete | MANAGER |
| `PUT /api/issues/{id}/labels` | Replace the issue's label set (labels must belong to the same project) | MEMBER |
| `POST /api/ai/issues/draft` | AI issue draft (nothing is saved); body `{projectId?, title?, description?}`, at least one of title/description; if `projectId` is given the caller needs MEMBER on an active project | MEMBER |
| `GET /actuator/health` (+ `/liveness`, `/readiness`) | Health checks | public |

Status codes: `200` read/update, `201` create, `204` no body, `400` invalid input, `401` missing/invalid token,
`403` insufficient role, `404` not found or not visible to you, `409` duplicate or conflicting state (duplicate email,
project key or label name, archived project, last owner). The AI draft endpoint also returns `422` (provider refused the
content), `429` (local or provider rate limit), `502` (unusable model output), `503` (not configured or provider down)
and `504` (provider timeout).

AI draft response (`200`): `{"title":"...","description":"...","acceptanceCriteria":["..."],"suggestedLabels":["bug"]}`.
The user reviews it, then saves it through `POST /api/projects/{id}/issues`.

Example:

```http
POST /api/projects/{projectId}/issues      Authorization: Bearer <jwt>
{"title":"Login page crashes","issueType":"BUG","priority":"HIGH","dueDate":"2026-12-01","estimatedHours":4.5}

201 Created
{"id":"...","issueKey":"DEMO-1","issueNumber":1,"status":"TODO","priority":"HIGH",
 "reporter":{"id":"...","fullName":"Ada","email":"ada@example.com"},"assignees":[],"labels":[], ...}
```

## Docker

```bash
docker build -t devtrack-backend .
docker run --rm -p 8080:8080 --env-file .env devtrack-backend
```

Multi-stage build (Maven, then `eclipse-temurin:21-jre-alpine`), layered jar, runs as a non-root user, `exec`-style start so
`SIGTERM` triggers graceful shutdown, binds to `${PORT}`, and has a liveness `HEALTHCHECK`. `.env` is excluded by `.dockerignore`.
Pass configuration at run time; never bake secrets into the image. The image was built and started against a local
PostgreSQL during development (health, registration, login, project and issue flows verified over HTTP).

## Deployment

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md). Migration, baseline and recovery: [docs/DATABASE_MIGRATIONS.md](docs/DATABASE_MIGRATIONS.md).

## Production security checklist

- [ ] The Neon password that was previously hard-coded in `application.properties` (it is in git history) has been **rotated**
- [ ] `JWT_SECRET` is random, at least 32 bytes, stored only in the host's secret store
- [ ] `CORS_ALLOWED_ORIGINS` lists only your real frontend origin(s)
- [ ] `SPRING_PROFILES_ACTIVE=prod` (Swagger off, JSON logs)
- [ ] `sslmode=require` on every JDBC URL; no real `.env` committed
- [ ] `FLYWAY_BASELINE_ON_MIGRATE` is `false` after the one-time baseline
- [ ] Neon backup/branch taken before each migration
- [ ] HTTPS terminated by the host; the API is not exposed over plain HTTP
- [ ] Rate limiting on `/api/auth/*` at the proxy or platform level (not built into the app)
- [ ] Only `/actuator/health` is reachable; everything else returns 401

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `Could not resolve placeholder 'DB_JDBC_URL'` (or `JWT_SECRET`) | Variable not set in the process environment or `.env` |
| `JWT_SECRET ... at least 32 bytes` | Use `openssl rand -base64 48` |
| `Found non-empty schema(s) "public" but no schema history table` | Existing database not yet baselined; follow the baseline procedure |
| `Schema-validation: missing column/table` | Database differs from the mappings; run `docs/db/verify-schema.sql` and add a migration |
| `Connection ... timed out` / `connection refused` | VPN or firewall blocking port 5432, or Neon compute waking up; retry, raise `DB_CONNECTION_TIMEOUT_MS` |
| `SSL` / `sslmode` errors | Keep `?sslmode=require` in the URL |
| `too many connections` on Neon | Lower `DB_POOL_MAX_SIZE` or use the `-pooler` host |
| Flyway lock or hang through the pooler | Set `SPRING_FLYWAY_URL` to the direct host |
| Browser CORS error | Origin missing from `CORS_ALLOWED_ORIGINS` (exact scheme, host and port, no trailing slash) |
| `401` on every call | Token expired (default 15 min), or issuer/audience/secret differ between instances |
| Tests fail to start | Docker Desktop is not running |

# Deployment guide

Nothing here has been deployed or verified on a hosting provider. The container image itself was built and started
locally (see README). Provider pricing, free-tier limits and UI steps change, so check each provider's current
documentation before relying on the details below.

The image is portable: any host that runs a Docker image, injects environment variables and sets `PORT` works.

## 1. Prepare Neon

1. Neon console, your project, **Connection details**. Copy the host, database, user and password.
2. Build the JDBC URL: `jdbc:postgresql://<host>/<database>?sslmode=require` (no credentials in the URL).
   Use the **pooled** host (`...-pooler...`) for the app and a **direct** host for migrations
   (`SPRING_FLYWAY_URL`, see [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md)).
3. **Rotate the database password** in Neon: the old one was committed in `application.properties` and remains in git history.
4. Keep `DB_POOL_MAX_SIZE` low (default 5). Neon limits connections per compute size, and each running instance has its own pool.
5. Neon scales idle computes to zero; the first request after idle can take a few seconds. The default connection
   timeout (20 s) allows for this.

## 2. One-time baseline of the existing database

Run `docs/db/verify-schema.sql`, back up (Neon branch), then deploy once with `FLYWAY_BASELINE_ON_MIGRATE=true` and
switch it back to `false`. Full procedure: [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md).

## 3. Test the image on your other machine against Neon

```bash
docker build -t devtrack-backend .
docker run --rm -p 8080:8080 --env-file .env devtrack-backend     # .env from .env.example, unquoted values
curl http://localhost:8080/actuator/health
```

Without Docker: `mvn spring-boot:run -Dspring-boot.run.profiles=local`.

## 4. Deploy to a managed container host

Generic steps (identical for Render, Railway, Fly.io, Google Cloud Run, Azure Container Apps and similar):

1. Push this repository to GitHub (confirm `.env` is not tracked).
2. Create a **web service from a Dockerfile** and point it at the repository root.
3. Set environment variables in the provider's secret/environment settings (never in the repo):
   `DB_JDBC_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `SPRING_PROFILES_ACTIVE=prod`.
   Leave `PORT` to the host if it injects one; the app reads it.
4. Set the health check path to `/actuator/health` (or `/actuator/health/liveness` if you want database blips not to
   restart the service).
5. Deploy and watch the logs for `Started DevtrackBackendApplication`.
6. Verify `https://<service-url>/actuator/health` returns `{"status":"UP"}`, then register and log in.

### Free-tier cautions (verify with the provider)

- Free web services commonly **sleep after inactivity**; the first request after sleep can take tens of seconds
  (JVM start plus Neon compute wake-up). The 60 s Docker `start-period` and 20 s DB timeout are set with this in mind.
- Free instances have small memory (often 256-512 MB). The image uses `-XX:MaxRAMPercentage=75`; if it is killed
  for memory, choose a larger instance or lower `DB_POOL_MAX_SIZE`.
- Free tiers have monthly hour, bandwidth and build limits; some providers require a card and bill beyond the free
  allowance. Set spending alerts and review the plan before enabling autoscaling.
- Neon's free tier has storage and compute-hour limits as well.

## 5. Connect the React frontend

1. Add the deployed frontend origin to the backend: `CORS_ALLOWED_ORIGINS=https://app.example.com` (comma-separate several;
   exact scheme, host and port, no trailing slash, no wildcards). Redeploy or restart the backend.
2. In the React app set the API base URL through its build-time environment:
   - Vite: `VITE_API_BASE_URL=https://<backend-url>` and use `import.meta.env.VITE_API_BASE_URL`
   - Create React App: `REACT_APP_API_BASE_URL=https://<backend-url>`
3. Call the API with the bearer token (no cookies are used, so `credentials: 'include'` is not needed):

```js
const res = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/projects`, {
  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
});
```

4. Handle `401` by sending the user to the login screen (tokens expire after 15 minutes by default).

## 6. After deployment

- Confirm only `/actuator/health` is public and `/swagger-ui.html` returns 404 in production.
- Work through the production security checklist in the [README](../README.md).
- Keep a Neon branch or backup before every release that includes a migration.

# Runtime Validation Report

**Generated**: 2026-11-11T23:07:17+05:30  
**Target**: `C:\Users\vamsi\OneDrive\Desktop\ai-sdlc-automation-assistant`

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Spring Boot configuration inspection | PASS | 0 | Main config uses `jdbc:postgresql://localhost:5432/ai_sdlc`, `${DB_USERNAME}`, `${DB_PASSWORD}`, PostgreSQL JDBC driver, Hibernate PostgreSQL dialect, and `ddl-auto=update`. |
| PostgreSQL service | PASS | 0 | Port 5432 is listening; `pg_isready -h 127.0.0.1 -p 5432` reported accepting connections. |
| Environment variables in validation process | BLOCKED | 2 | Neither `DB_USERNAME` nor `DB_PASSWORD` was present in the process environment; neither was present in the user environment scope. After the user confirmed availability, a fresh check still found both missing. Password value was never read into output or displayed. |
| Database role/database/schema permissions | BLOCKED | 2 | Permission probe stopped before connecting because the required environment variables are unavailable to this validation process. Database/role existence and `CONNECT`, `public.USAGE`, and `public.CREATE` grants remain unverified. |
| Maven command discovery | PASS | 0 | No `mvnw.cmd` exists at the repository root or `backend`. Bundled Maven 3.9.9 exists at `tools\apache-maven-3.9.9\bin\mvn.cmd`. |
| Backend startup and PostgreSQL DDL | BLOCKED | n/a | User reported the backend was running, but no listener appeared on port 8080 after a 45-second readiness wait. Startup logs were not available to this runner; database connection/table creation is unverified. |
| Direct authentication/CSRF API | BLOCKED | n/a | `GET http://127.0.0.1:8080/api/auth/csrf` could not connect; port 8080 had no listener. |
| Frontend HTTP and API proxy | PARTIAL | 0 / 502 | Vite served `/` with HTTP 200. Its configured `/api` proxy returned HTTP 502 for `/api/auth/csrf`; Vite logged `AggregateError [ECONNREFUSED]` because the backend was not listening. |
| Live authentication and CSRF behavior | BLOCKED | n/a | Registration/login, invalid credentials, token rejection, and authenticated session checks did not reach a live backend. Existing H2-backed authentication tests passed (see below); they are not PostgreSQL/live-server validation. |
| Live project create/retrieve API | BLOCKED | n/a | No live request could reach the backend. No project was created or read during this run. |
| Persistence after backend restart | BLOCKED | n/a | No live project was created, so post-restart persistence is unverified. |
| Backend tests | PASS | 0 | Bundled Maven `test`: 10 tests, 0 failures, 0 errors, 0 skipped. |
| Frontend build | PASS | 0 | `npm run build` completed successfully. |
| Frontend lint | PASS | 0 | `npm run lint` completed successfully. |

**Overall**: FAIL / BLOCKED — PostgreSQL is accepting connections, but the environment variables configured by the user are not visible to the PowerShell process used for validation. Consequently, the role permissions, backend startup, live APIs, and persistence could not be verified.

## Configuration and command findings

- Database/JDBC URL: `jdbc:postgresql://localhost:5432/ai_sdlc`.
- Required runtime environment variables: `DB_USERNAME` and `DB_PASSWORD`. The expected username provided for this run is `ai_sdlc_app`; the password is intentionally neither recorded nor displayed.
- Driver: `org.postgresql.Driver`.
- JPA/Hibernate: `spring.jpa.hibernate.ddl-auto=update`; dialect `org.hibernate.dialect.PostgreSQLDialect`; SQL logging off; open-in-view off.
- Local session-cookie setting: `SESSION_COOKIE_SECURE` defaults to `false`.
- PostgreSQL readiness: server accepts connections on `localhost:5432`. This is a server-readiness check only, not a credential/database/role check.
- Maven wrapper: absent at both `mvnw.cmd` candidate paths. Use bundled Maven 3.9.9 from the backend directory:

  ```powershell
  Set-Location 'C:\Users\vamsi\OneDrive\Desktop\ai-sdlc-automation-assistant\backend'
  & '..\tools\apache-maven-3.9.9\bin\mvn.cmd' spring-boot:run
  ```

- Do not run that startup command from a process where either required environment variable is missing. In this execution environment both variables were absent from the process scope and user scope, despite the user's statement that they are configured locally.

## Test evidence

- Backend: `..\tools\apache-maven-3.9.9\bin\mvn.cmd test` from `backend`; exit code 0.
  - `AccountServiceTest`: 2 passed.
  - `AuthIntegrationTest`: 5 passed.
  - `ProjectServiceTest`: 3 passed.
  - Tests use the test configuration's H2 in-memory database; this does not prove PostgreSQL connection, schema permissions, or live API behavior.
- Frontend build: `npm run build`; exit code 0.
- Frontend lint: `npm run lint`; exit code 0.
- Vite proxy: `GET http://127.0.0.1:5174/` returned HTTP 200; `GET http://127.0.0.1:5174/api/auth/csrf` returned HTTP 502 with Vite `ECONNREFUSED` to the backend.
- Direct backend CSRF request: `http://127.0.0.1:8080/api/auth/csrf` could not connect; port 8080 had no listener.
- Follow-up environment/permissions probe after the user confirmed local configuration: both process variables still absent; probe exited 2 before invoking `psql`.
- After two user confirmations that the backend was running/ready, repeated checks still found no listener on port 8080. Direct CSRF requests failed with `Unable to connect to the remote server`; Vite returned HTTP 502 and logged `AggregateError [ECONNREFUSED]` on two requests. No Maven/Spring startup output was accessible, so no database failure cause is inferred.
- The temporary Vite process started for this check was stopped. PostgreSQL was left running.

## Validation verdict

```text
environment:
  docker: UNAVAILABLE — Docker is not installed
  node: AVAILABLE — v24.12.0
  playwright: NOT RUN — no browser test was attempted; live backend was unavailable
  infra-tier: PRIMARY(local PostgreSQL) — server ready, but app-role authentication and grants were not verifiable from this process
  browser-tier: UNVERIFIED — Vite HTTP and proxy were probed; no browser journey was run
startup: BLOCKED — user reports a manual launch, but port 8080 remained closed for 45 seconds; logs are not available to this runner
integration: PASS — exit_code: 0; passed: 10, failed: 0, skipped: 0; existing H2-backed service/auth tests only; PostgreSQL not covered
e2e: UNVERIFIED — exit_code: n/a; live auth, CSRF, project create/read, and restart persistence were blocked by backend unavailability
overall: FAIL — environment variables were unavailable to this runner; database permissions and live application behavior remain unverified
```

## Pending checks and smallest safe next step

1. The validation runner does not inherit the variables visible in the user's terminal. Despite the user's running/ready confirmations, no HTTP listener became available. In the terminal where the variables are configured, inspect the Maven process and provide the startup result or redacted error lines. Do not send or print the password. The command is:

   ```powershell
   Set-Location 'C:\Users\vamsi\OneDrive\Desktop\ai-sdlc-automation-assistant\backend'
   & '..\tools\apache-maven-3.9.9\bin\mvn.cmd' spring-boot:run
   ```

   The next step is blocked until port 8080 actually listens and a direct CSRF GET returns HTTP 200. No authentication, project CRUD, or restart-persistence request has been performed against the live API.
2. With both variables present, verify the `ai_sdlc_app` connection to database `ai_sdlc` and its `CONNECT`, `public.USAGE`, and `public.CREATE` privileges. Grant only the required database/schema privileges if the SQL privilege probe shows a missing grant.
3. Start the backend with the command above and capture startup logs confirming PostgreSQL connection and Hibernate schema update. If startup fails, record its exact safe-to-share error (never include a password).
4. Repeat CSRF and authentication requests through the Vite `/api` proxy, then create and read a uniquely named project under the authenticated account.
5. Restart only the backend process and read the same project again to verify persistence; delete test data only if the API cleanup succeeds.

No application source code was changed. Only this runtime validation report was updated.

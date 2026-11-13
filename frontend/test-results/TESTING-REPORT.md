# Playwright E2E Testing Report

**Application:** AI-Powered SDLC Automation Assistant  
**Latest verification:** Phase 2 Architecture Generation implementation and validation (2026-11-13)  
**Browser:** Chromium  
**Frontend:** `http://localhost:5173`; production preview audit at `http://127.0.0.1:4173`  
**Backend:** `http://localhost:8080`  
**Historical auth/project Playwright result:** 7 passed, 0 failed, 1 skipped. **Historical targeted restart-persistence test:** 1 passed; the same test account logged in and its project was retrieved after Spring Boot restarted. Phase 2 test results are recorded below.

## Execution summary

| Check | Result | Details |
|---|---|---|
| Frontend build/type check | PASS | Latest `npm run build` completed successfully |
| Playwright TypeScript check | PASS | Latest `npm run typecheck:e2e` completed successfully |
| Backend tests | PASS (H2) | Maven: 30 tests, 0 failures/errors/skips; architecture API/persistence tests use H2, not PostgreSQL |
| Requirements Playwright tests | PASS | 3 passed in the targeted Chromium run |
| Architecture Playwright tests | PASS | 4 passed, including approved selection, generation, editing/review, history, invalid-diagram handling, and narrow viewport |
| Architecture + requirements browser regressions | PASS | 7 passed, 0 failed |
| Safe mocked/UI Playwright selection | PASS | 7 passed, 1 skipped; live backend tests excluded |
| Complete default Playwright suite | BLOCKED | 7 passed, 3 failed, 1 skipped; three live-auth checks received 502/timeout because backend port 8080 was unavailable |
| Targeted live PostgreSQL restart workflow | PASS | Existing named Playwright test passed after a verified Spring Boot stop/start |
| Standalone runtime validation | PASS | All 3 plan assertions passed: build, Playwright type-check, E2E process |
| Lighthouse production-build audit | PASS with findings | Performance 81, Accessibility 100, Best Practices 96, SEO 91 |
| HTML report | Generated | `playwright-report/index.html`; latest full run includes the three backend-availability failures |
| Failure evidence | Available | Live integration failure context is in `./e2e/live-postgres-live-registr-3defc-ization-and-backend-restart/error-context.md`; that test disables screenshots/traces |
| PostgreSQL persistence before restart | PASS | SQL confirmed the generated account and project before restart |
| Persistence across backend restart | PASS | New backend PID/JDBC sessions verified; Playwright post-restart login and project GET passed; read-only SQL reconfirmed the same project ID |

The earlier PostgreSQL restart-persistence results below concern existing authentication/project features, not the Phase 2 architecture workflow. Architecture integration tests use H2 and Playwright route fixtures; no live PostgreSQL architecture writes or OpenAI calls were made for this feature.

## Phase 1 and Phase 2 feature validation

| Test/check | Expected result | Actual result | Status | Evidence |
|---|---|---|---|
| Backend Maven suite | Compile and pass all unit/integration tests | 30 tests passed; 0 failures, errors, or skips | PASS | `backend/target/surefire-reports/` |
| Frontend production build | TypeScript and Vite production build succeed | `npm run build` succeeded | PASS | Build output generated `frontend/dist/` |
| Playwright TypeScript | Test sources type-check | `npm run typecheck:e2e` succeeded | PASS | Command exit code 0 |
| Frontend lint | Changed frontend sources/tests lint | Exit code 0; one existing `App.tsx` exhaustive-deps warning | PASS with warning | `src/App.tsx:113` warning predates this workflow |
| Requirements UI workflow | Generate, edit, approve, reject, delete, reload saved records | 3 Chromium tests passed, including reload and narrow viewport | PASS (mocked API) | `tests/requirements.spec.ts`; Playwright report |
| Architecture UI workflow | Select approved inputs, generate, preview/edit, approve/reject, inspect versions | 4 Chromium tests passed, including invalid Mermaid fallback and narrow viewport | PASS (mocked API) | `tests/architecture.spec.ts`; Playwright report |
| Architecture API/service workflow | Generate, persist, list, retrieve, edit, approve/reject, version, ownership, approval and source-reference constraints | Included in 30 passing H2-backed Maven tests | PASS (H2) | `backend/src/test/java/com/ai/sdlc/ArchitectureIntegrationTest.java` |
| Provider failure handling | Show provider error without fake success | Provider failure browser test passed | PASS (mocked API) | `tests/requirements.spec.ts` |
| Safe existing UI workflows | Auth form, project UI, and requirements behavior work | 7 passed, 1 opt-in live test skipped | PASS (mocked UI) | `npm run test:e2e -- --workers=1 --grep-invert 'backend rejects an invalid registration payload|invalid credentials are rejected by the backend|homepage shows the product title'` |
| Default complete browser suite | Existing backend-dependent and mocked flows pass | 7 passed, 3 failed, 1 skipped; CSRF calls returned HTTP 502 and login wait timed out | BLOCKED | Backend direct probe could not connect; `Test-NetConnection localhost -Port 8080` reported false |
| PostgreSQL persistence for new requirements | Requirements remain after real DB restart | Not run during Phase 2; historical PostgreSQL evidence is for auth/projects only | NOT RUN | Prior PostgreSQL evidence in this report is for auth/projects only |
| PostgreSQL persistence for architecture documents | Architecture remains after real DB restart | Not run; the local PostgreSQL listener and backend DB environment credentials were unavailable during Phase 2 validation | NOT RUN | No architecture documents were written to PostgreSQL |
| OpenAI generation | Real provider returns valid structured output | Not run; no live provider call or key used | NOT RUN | Standard tests use mock provider/parser tests |

The initial targeted browser failures were caused by the fixture's broad `requirements/*` route intercepting `/requirements/generate` and returning a not-found response. The fixture now falls through for `/generate`; the three targeted requirements tests pass. No application behavior was changed to accommodate the test.

## Live backend/PostgreSQL integration phase (2026-11-12)

**Status: PASS for the requested restart-persistence verification.** Before the targeted live test, read-only SQL verified `current_database()=ai_sdlc_test` and `current_user=ai_sdlc_app`; ten fresh `PostgreSQL JDBC Driver` sessions and backend CSRF HTTP 200 corroborated the active datasource. After restart, a new Spring Boot process and ten new JDBC sessions were observed on the same isolated database. The original database `ai_sdlc` was not queried or modified.

### Configuration and API contract inspected

- The source default remains `jdbc:postgresql://localhost:5432/ai_sdlc`; the username and password are environment-supplied (`DB_USERNAME` and `DB_PASSWORD`). Hibernate uses `ddl-auto=update`.
- The intended safe runtime override is `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ai_sdlc_test`; active JDBC sessions were independently observed in `ai_sdlc_test` under `ai_sdlc_app`. Credentials were not read or recorded.
- Discovered auth routes: `GET /api/auth/csrf`, `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, and `POST /api/auth/logout`. Auth is session-cookie based; mutating requests require the CSRF cookie/token sent as `X-XSRF-TOKEN`. Project routes are authenticated: `GET/POST /api/projects` and `GET/PUT/DELETE /api/projects/{id}`.
- `User` owns projects with JPA cascade/orphan-removal; `Project` has dependent requirements, architecture documents, development tasks, test cases, user stories, and agent logs. Project mutations and removal of a dedicated test user can affect dependent rows, so cleanup must be confined to the isolated test database and dedicated account.

### Live integration test outcomes

| Test | Result |
|---|---|
| Live backend readiness / CSRF | PASS | Backend responded; `GET /api/auth/csrf` returned 200 |
| Database/backend datasource identity | PASS | Read-only SQL verified `ai_sdlc_test` / `ai_sdlc_app`; ten active-pool JDBC sessions observed from localhost |
| Live invalid registration and invalid login | PASS | Actual backend negative-path checks in the non-mutating Playwright suite |
| Live valid registration/session and login | PASS | Registration returned 201, authenticated user/projects reads succeeded, sign-out invalidated the session, and re-login succeeded |
| Live project create/detail/update/delete | PASS | Create 201, detail 200, update 200; temporary delete returned 204 and subsequent detail returned 404 |
| PostgreSQL record verification | PASS | SQL verified the generated account/project before restart and the same project after restart; aggregate counts: seven `e2e-` accounts, seven persistence projects, zero temporary delete-test projects |
| Backend restart and post-restart login/project retrieval | PASS | PID changed from 6784 to 7204; JDBC sessions were re-created; Playwright login returned 200 and retrieval of the same project returned 200 with expected details |
| Test-data cleanup | NOT RUN | Dedicated test accounts/projects remain in `ai_sdlc_test`; no destructive cleanup attempted |

Successful auth/project UI flows in the ordinary E2E suite use route interception or an in-memory API stub; those passes establish frontend behavior only. The latest opt-in run independently verified real backend responses and PostgreSQL rows as detailed below.

## Test cases

| ID | Test | Result | Coverage |
|---|---|---|---|
| E2E-001 | Homepage title, sign-in screen, CSRF endpoint and protected projects API | PASS | Live frontend/backend; verifies CSRF 200 and unauthenticated projects 401 |
| E2E-002 | Registration required fields and minimum password length | PASS | Browser form validation |
| E2E-003 | Invalid registration payload | PASS | Live backend returns 400; payload rejected before account creation |
| E2E-004 | Invalid login credentials | PASS | Live backend returns 401; UI displays “Invalid email or password.” |
| E2E-005 | Valid registration flow and logout | PASS (mocked) | UI workflow with intercepted API responses; does not establish live auth success |
| E2E-006 | Project create/list/edit/search/delete | PASS (stubbed) | UI workflow with an in-memory API stub; does not establish database persistence |
| E2E-007 | Project API failure and retry recovery | PASS | Simulated 503 followed by a successful stubbed response |
| E2E-008 | Live registration/auth/project create-detail-update-delete | PASS | Real backend and PostgreSQL; unique account; all API/UI assertions before restart passed |
| E2E-009 | Direct PostgreSQL verification of live records | PASS | Read-only SQL confirmed the generated account/project before and after restart; final aggregate counts: 7 test accounts, 7 persistence projects, 0 temporary delete-test projects |
| E2E-010 | Persistence after backend restart | PASS | Exact existing test passed (1 passed); new backend process was verified and Playwright logged in and retrieved the same project after restart |

Browser workflows executed with Chromium. The dedicated accounts/projects created during restart-persistence attempts remain in `ai_sdlc_test`. Final read-only SQL found seven `e2e-` test accounts, seven persistence-named projects, and zero temporary `Delete verification` projects. No broad or destructive cleanup was attempted.

The runtime-validation tool reported all three plan assertions passed. Its test-results parser could not determine granular counts (`framework=unknown`, `parse-failed`); direct Playwright invocations provide the actual counts. The targeted restart-persistence invocation completed with **1 passed**.

## API integration findings

The Vite `/api` proxy successfully reached the configured backend for the read/negative cases:

- `GET /api/auth/csrf` returned HTTP 200 with a token.
- `GET /api/projects` without an authenticated session returned HTTP 401.
- Invalid credentials at `POST /api/auth/login` returned HTTP 401 and the expected UI error.
- An invalid registration payload at `POST /api/auth/register` returned HTTP 400.
- A prior pre-fix project API attempt returned null `createdAt` and `updatedAt` values. Following the `saveAndFlush` fix, the current live test's project-create response included string timestamps and create/detail/update/delete assertions passed.
- In the standard suite, successful registration/project paths and the simulated 503 recovery use Playwright route stubs. The separate opt-in test exercised successful auth and project operations against the real backend.

During the earlier auth/project phase, a full suite completed without a failed browser network request or unhandled JavaScript error. In the prior requirements phase, three backend-dependent tests could not run successfully because the backend on port 8080 was unavailable; expected unauthorized responses remain excluded from the homepage test's console-error assertion.

## Database persistence findings

The protected PostgreSQL passfile was used without displaying its contents. Read-only SQL verified the active database/user before the run and confirmed the newly generated account and project row—including updated description and timestamps—before restart. After the user stopped and restarted Spring Boot, the listener changed from PID 6784 to PID 7204. PostgreSQL then showed ten fresh JDBC-driver connections to `ai_sdlc_test` under `ai_sdlc_app`, with connection start times after the new process started.

The existing Playwright test retained the generated password only in memory while waiting. It passed its post-restart login assertion (HTTP 200) and retrieved the same project ID successfully, verifying expected project details. A final narrowly scoped SQL query confirmed that project ID `74f6cfdc-0530-4964-8719-c4d39a146d73` still exists with the updated description and timestamps. Overall `e2e-` fixture counts were seven accounts, seven persistence projects, and zero temporary delete-test projects. Credentials were not written to this report. The original `ai_sdlc` was not targeted.

Earlier restart attempts timed out because the Spring Boot process was not restarted within the test's wait window; they are superseded by this successful run. Their retained test rows were not deleted.

The backend's automated Maven tests use in-memory H2 with `create-drop`; those tests are separate from the genuine PostgreSQL observations above.

## Confirmed defect and fixes

- **Project response timestamps were null immediately after create/update.** Hibernate populated `@CreationTimestamp` / `@UpdateTimestamp` after the service constructed the response DTO. `ProjectService` now uses `saveAndFlush` before mapping responses. Regression coverage passes (11 H2-backed Maven tests), and the live PostgreSQL workflow confirmed string timestamps on create plus successful update.
- **Lighthouse low-contrast text and missing favicon findings.** Text color variables were adjusted for light/dark themes and the existing SVG favicon was linked from the HTML. Production-build Lighthouse Accessibility reached 100.

## Lighthouse (production build)

Audited the Vite production build using `vite preview` (not the development server). Scores: Performance **81**, Accessibility **100**, Best Practices **96**, SEO **91**. Lighthouse warned that the test device CPU was slower than its expected calibration; treat performance as environment-sensitive. Existing report files: `lighthouse-production-report.report.html` and `lighthouse-production-report.report.json`. The earlier development-server audit scored 38/96/96/91 and should not be used as the production performance result.

## Feature availability

| Feature | Status | Evidence / limitation |
|---|---|---|
| Sign-in/registration screen and session-gated workspace | PARTIALLY IMPLEMENTED | Standard suite's success path is mocked; live registration, login, logout, and unauthorized access passed |
| Project list/create/edit/search/delete UI | PARTIALLY IMPLEMENTED | Stubbed UI suite and live create/detail/update/delete passed; no distinct project detail screen |
| Backend authentication and project endpoints | VERIFIED for tested flows | Real CRUD/auth requests passed against the independently verified test database |
| PostgreSQL record persistence before restart | VERIFIED | SQL confirmed generated test user and updated project rows |
| Persistence after backend restart | VERIFIED | Exact live Playwright test logged in after restart and retrieved the same project; direct SQL confirmed the row remains |
| Requirements creation/management | IMPLEMENTED; MOCK/H2-TESTED | Owner-scoped APIs, provider boundary, persistence model, and review workspace are implemented. The new feature has 21 passing H2-backed backend tests and 3 passing mocked Playwright tests; real PostgreSQL and OpenAI execution remain unverified. |
| Architecture generation | IMPLEMENTED; MOCK/H2-TESTED | Owner-scoped generation/list/get/edit/approve/reject/version APIs, approved-requirement provenance, draft-only editing/review, versioned persistence, and four Mermaid diagrams. Backend full suite: 30 H2 tests passed; architecture + requirements browser suite: 7 passed. Live PostgreSQL architecture persistence/restart and OpenAI generation remain unverified. |
| Development planning | NOT IMPLEMENTED | No implemented UI/API workflow found |
| Test-case generation | NOT IMPLEMENTED | No implemented UI/API workflow found |
| Traceability matrix | NOT IMPLEMENTED | No implemented UI/API workflow found |
| SDLC report generation | NOT IMPLEMENTED | No implemented UI/API workflow found |
| Project details page | NOT IMPLEMENTED | Current UI provides list/edit, not a distinct detail page |
| Delayed-response loading states | NOT TESTED | No controlled delay scenario was run |

The SDLC workflow navigation items and dashboard metrics for requirements, planning, testing, and traceability are marked “Soon” or “Available in a later phase.” Domain entities exist for some future artifacts, but no corresponding API controllers were found.

## Recommended follow-up

1. Add/run a non-destructive architecture create/retrieve/restart check against an explicitly verified `ai_sdlc_test` datasource; retain created test records unless narrowly scoped cleanup is authorized.
2. Retain the historical auth/project test rows or perform only explicitly authorized, narrowly scoped cleanup; do not delete or truncate unrelated rows.

## Re-run commands

From the `frontend` directory:

```powershell
npm run test:e2e -- --workers=1
npm run test:e2e -- tests/requirements.spec.ts --workers=1
npm run test:e2e -- tests/architecture.spec.ts tests/requirements.spec.ts --workers=1
npm run test:e2e -- --workers=1 --grep-invert 'backend rejects an invalid registration payload|invalid credentials are rejected by the backend|homepage shows the product title'
npm run test:e2e:headed
npm run typecheck:e2e
npx playwright show-report playwright-report
npm run build
npm run lint -- src/App.tsx src/components/RequirementsWorkspace.tsx src/services/requirementsApi.ts tests/requirements.spec.ts
```

The complete browser suite includes live-auth checks and requires the Spring Boot backend at `http://localhost:8080`. The targeted requirements tests use route fixtures and do not require a live backend.

From the `backend` directory, with Maven installed:

```powershell
mvn test
```

The regular suite's successful mutation flows are stubbed. The existing opt-in workflow exercises authentication/projects only. Any architecture persistence/restart extension must first verify the backend is connected to `ai_sdlc_test`; do not run it against the default `ai_sdlc` datasource. Architecture has not yet been tested against live PostgreSQL.

```powershell
$env:RUN_POSTGRES_INTEGRATION = 'true'
npm run test:e2e:live
```

Do not run the live workflow against an unverified datasource; it creates persistent test accounts/projects.

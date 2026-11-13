# Implementation Plan: AI-Powered Requirements Engineering

**Date:** 2026-11-12 | **Spec:** [feature-spec.md](./feature-spec.md)

## Summary

Implement the first user-facing SDLC workflow in the existing React/Spring Boot application. Reuse the current session security, project ownership boundary, PostgreSQL/JPA model, API client, and project UI. Add a Spring AI provider boundary with an explicit local mock default and an opt-in OpenAI provider. Model output is JSON parsed and validated server-side; generated rows are saved as pending review. Human approval is always explicit.

## Technical Context

**Language/Version:** Java 21, Spring Boot 3.4.0; TypeScript/React/Vite  
**Primary Dependencies:** Existing Spring Web, Validation, Security, JPA, PostgreSQL, Jackson, Playwright; Spring AI 1.0.x BOM with `spring-ai-openai` and `spring-ai-client-chat`. Spring AI 1.0 supports Spring Boot 3.4.x; do not upgrade the application stack for this feature.
**Storage:** Existing PostgreSQL `requirements` table and `Requirement` entity. Add acceptance criteria as a backward-compatible nullable text column so existing rows are not rewritten or blocked; new API writes require non-empty criteria. Preserve the existing `project_id` relationship and add no destructive migration.  
**Testing:** JUnit 5, Mockito, Spring MVC/JPA test support with the existing H2 test profile, and existing Playwright Chromium suite. AI network calls are never required in standard tests.  
**Target Platform:** Existing single-origin Vite + Spring Boot application.  
**Constraints:** No secrets in code; owner scope on all operations; no automatic AI approval; mock output labelled; no fake success after provider/parsing errors; send only required project context to the provider.

### Provider and failure decisions

- Add a `RequirementGenerationProvider` interface and implementations for a mock provider and Spring AI OpenAI.
- Default to `APP_AI_PROVIDER=mock`; require explicit `openai` selection and `OPENAI_API_KEY` for real inference. Never silently fall back from a failed real provider to mock output.
- Represent provider identity in the generation result and persisted source marker (`MOCK_DEMO` or `OPENAI`).
- Request JSON-object mode with a requirements array. Parse directly with Jackson, validate every item, and reject malformed/partial model output before inserting any rows.
- Bound provider retries/timeouts and translate provider failures into non-success API errors.
- Generate requirements as `PENDING`. Editing any item resets it to `PENDING`; approve/reject endpoints perform explicit status transitions.
- Use owner-scoped project and requirement repository lookups for every read and write.
- Preserve `application.properties` datasource defaults and schema policy; do not change database credentials or switch the live database.

## Constitution Check

| Principle | Result | Alignment |
|---|---|---|
| Modular, layered Java and React architecture | PASS | Add focused DTO, repository, service, provider, controller, and UI service/component responsibilities |
| Align to Phase 1 design | PASS | Implements the documented requirement-agent, human-review, and structured DTO direction without implementing downstream phases |
| Never auto-approve generated AI output | PASS | All generated records begin pending |
| Real model calls only with explicit configuration; identify demo data | PASS | Mock is separately selected/defaulted and visibly disclosed; OpenAI is explicit |
| Keep secrets out of code/client/report | PASS | Credentials are read from backend process environment only |
| Preserve existing data and authentication/ownership | PASS | Nullable additive column; reuse existing authenticated owner identity and CSRF |

## Applied Guidelines

No framework-to-framework migration guideline applies. Spring AI compatibility was checked against the official Spring AI 1.0 documentation, which supports Spring Boot 3.4.x. Use the existing Spring conventions, validation, DTO, security, and frontend styles.

## Implementation Steps

### Step 1: 1.1 — Add provider configuration and compatible Spring AI dependencies
- **Requirements:** REQ-002, REQ-007, REQ-008
- **Design inputs:** `docs/ARCHITECTURE.md` AI integration layer; `.github/copilot-instructions.md` no-fake-output and secret rules
- **Description:** Import the Spring AI 1.0 BOM and OpenAI model/chat-client modules compatible with Spring Boot 3.4. Use direct modules and construct the OpenAI client only when explicitly selected; the Boot starter's unrelated model auto-config requires credentials even in mock mode. Keep mock as the local/test default and require explicit environment-based OpenAI configuration. Bound retry and request timeouts; do not add frontend secrets.

### Step 2: 2.1 — Extend the requirement persistence contract safely
- **Requirements:** REQ-003
- **Design inputs:** Existing `Requirement.java`, `Project.java`, and `docs/DATABASE_SCHEMA.md`
- **Description:** Add acceptance criteria while retaining current columns, relationships, and existing rows. Keep legacy acceptance criteria nullable at the database layer; require non-empty criteria for new API writes. Define supported type, priority, status, and provider values in application validation.

### Step 3: 2.2 — Define requirement API and provider contracts
- **Requirements:** REQ-001, REQ-002, REQ-003, REQ-004, REQ-005
- **Design inputs:** Existing auth/project DTO and controller patterns
- **Description:** Define generation, response, edit, and action DTOs; add the provider interface and owner-scoped repository method contracts. Generate the next requirement code after the highest existing numeric code while preserving UUID primary keys.

### Step 4: 2.3 — Implement provider implementations and strict parsing
- **Requirements:** REQ-001, REQ-002, REQ-007
- **Design inputs:** Spring AI OpenAI documentation; provider contract from Step 3
- **Description:** Implement explicitly labelled deterministic mock generation and OpenAI JSON-mode generation. Parse and validate the entire response before persistence. Translate malformed output, timeout, rate limit, and provider errors to explicit error results; never fall back silently.

### Step 5: 2.4 — Implement owner-scoped persistence and review lifecycle
- **Requirements:** REQ-003, REQ-004, REQ-005
- **Design inputs:** Existing `ProjectService` owner-scoping and transaction patterns
- **Description:** Load the project from the authenticated owner, build minimized context from stored project fields, persist generated records as pending, list/get/edit/approve/reject/delete owner-scoped requirements, and reset edited reviewed items to pending.

### Step 6: 2.5 — Expose authenticated requirement endpoints
- **Requirements:** REQ-001, REQ-004, REQ-005
- **Design inputs:** Existing `ProjectController`, `SecurityConfig`, and exception conventions
- **Description:** Expose generate/list/get/update/approve/reject/delete under `/api/projects/{projectId}/requirements`. Use existing session/CSRF protection, request validation, and 404 for foreign-owned resources.

### Step 7: 3.1 — Add project workspace and requirement UI
- **Requirements:** REQ-005, REQ-006
- **Design inputs:** Existing `App.tsx`, project API client, theme, responsive CSS, accessibility patterns
- **Description:** Make projects openable into a project workspace that shows the saved context and Requirements section. Add provider disclosure, generate/loading/error/empty states, functional/non-functional groups, status/priority/acceptance-criteria cards, edit/approve/reject/delete actions, and destructive confirmation.

### Step 8: 4.1 — Verify behavior and update product documentation
- **Requirements:** REQ-007, REQ-008
- **Design inputs:** Existing H2/JUnit and Playwright patterns; user feature specification
- **Description:** Add focused backend and Playwright tests, run existing suites/build/type checks, and update API, schema, architecture, and local AI-provider configuration documentation. Report all test outcomes truthfully.

## Project Structure

```text
backend/src/main/java/com/ai/sdlc/
  controller/          # authenticated project requirement routes
  domain/              # existing Requirement entity extended compatibly
  dto/                 # request/response and validated generated-output contracts
  exception/           # provider/validation error translation
  repository/          # owner-scoped requirement queries
  service/             # requirement lifecycle and generation orchestration
  ai/                   # provider interface, mock, OpenAI adapter, parser
backend/src/test/java/ # provider, service, repository, controller/auth tests
frontend/src/
  services/             # requirement API calls through existing CSRF client
  types/                # requirement request/response types
  App.tsx               # project workspace and requirements experience
  index.css             # responsive accessible presentation
frontend/tests/         # Playwright requirements workflow
docs/                   # workflow, API, model config, and schema updates
```

## Testing Strategy

- **appType:** SPA + REST API
- **Critical user journeys:** (1) open an owned project and generate labelled mock requirements; (2) list persisted requirements after navigation/reload; (3) edit then approve/reject individual entries; (4) delete only after confirmation; (5) provider failure displays an error without creating rows; (6) foreign owner cannot read or mutate project requirements.
- **primaryValidationStack:** JUnit 5/Mockito, Spring MVC/JPA integration tests on isolated H2 for routine suite, Playwright Chromium with mock provider.
- **fallbackMatrix:** `infra-tier`: PostgreSQL for explicit live verification when available; H2 isolated test profile for routine validation (does not claim PostgreSQL dialect/DDL coverage). `browser-tier`: Playwright Chromium as already configured.
- **Environment requirements:** Java 21/Maven; Node/npm and installed Chromium; no paid AI credentials for standard test suite. Real OpenAI smoke tests are optional and only run when explicitly configured.
- **knownGaps:** H2 tests do not prove PostgreSQL migration behavior; provider mock proves contract only, not OpenAI output quality/availability.
- **Test data strategy:** use isolated H2 for backend tests; Playwright creates uniquely named project requirements through mock provider, deletes only its dedicated requirement fixtures where cleanup is safe; never reset databases or touch the original `ai_sdlc`.
- **Acceptance criteria:** all generated records are PENDING, valid, persist and reload; provider marker is visible; all owner checks pass; malformed/provider errors create no records; old project/auth tests continue to pass.
- **Validation review expectations:** confirm no credential exposure, no mock presented as real AI, no cross-owner access, no implicit approval, and no changes to existing user/project data.

## Phase 1: Setup

- [x] T001 [Plan:1.1] Add the compatible Spring AI BOM and OpenAI model/chat-client modules to `backend/pom.xml`; configure `APP_AI_PROVIDER` defaulting to mock and environment-only OpenAI key/model selection in `backend/src/main/resources/application.properties`.

## Phase 2: Foundational contracts and data

- [x] T002 [Plan:2.1] Extend `backend/src/main/java/com/ai/sdlc/domain/Requirement.java` with backward-compatible acceptance-criteria storage without altering current project/user relationships.
- [x] T003 [Plan:2.2] Add validated requirement edit/response DTOs under `backend/src/main/java/com/ai/sdlc/dto/` and provider contracts under `backend/src/main/java/com/ai/sdlc/ai/`.
- [x] T004 [Plan:2.2,2.4] Add owner-scoped requirement repository queries in `backend/src/main/java/com/ai/sdlc/repository/RequirementRepository.java`.

## Phase 3: User story — Generate and review requirements (P1)

- [x] T005 [US1] [Plan:2.3] Add provider and parser tests for valid structured JSON, invalid JSON, missing fields, invalid enum values, timeout/provider errors, and explicit mock labeling in `backend/src/test/java/com/ai/sdlc/`.
- [x] T006 [US1] [Plan:2.3] Implement mock and Spring AI OpenAI providers, minimized project context, strict Jackson parsing, and explicit provider error handling under `backend/src/main/java/com/ai/sdlc/ai/`.
- [x] T007 [US1] [Plan:2.4] Implement transactional generation/list/get/edit/approve/reject/delete lifecycle in `backend/src/main/java/com/ai/sdlc/service/RequirementService.java`, ensuring generation is pending and edits reset status to pending.
- [x] T008 [US1] [Plan:2.5] Add owner-authorized requirement routes in `backend/src/main/java/com/ai/sdlc/controller/RequirementController.java` and map generation/provider failures to explicit non-success HTTP responses.
- [x] T009 [US1] [Plan:2.4,2.5] Add repository/service/controller tests for persistence, state transitions, validation, CSRF/session behavior, and cross-owner denial in `backend/src/test/java/com/ai/sdlc/`.

## Phase 4: User story — Use the project requirements workspace (P1)

- [x] T010 [US1] [Plan:3.1] Add requirement API functions using the existing CSRF-aware `frontend/src/services/apiClient.ts` and typed contracts under `frontend/src/types/`.
- [x] T011 [US1] [Plan:3.1] Add project opening and the accessible requirements workspace in `frontend/src/App.tsx`, reusing the existing layout/theme and providing loading, error, empty, generation-provider disclosure, grouped cards, edit, approve, reject, delete confirmation, and responsive states.
- [x] T012 [US1] [Plan:3.1,4.1] Add Playwright tests in `frontend/tests/` for the mocked generation/review workflow, loading/error/empty states, provider failure handling, and workspace reload; verify backend ownership denial in the API integration test.

## Phase 5: Documentation and validation

- [x] T013 [Plan:4.1] Update `docs/DATABASE_SCHEMA.md`, `docs/ARCHITECTURE.md`, `docs/AUTHENTICATION.md` or a requirements workflow document, and `docs/FRONTEND.md` with model, data flow, API examples, mock-vs-real provider behavior, and environment configuration.
- [ ] T014 [Plan:4.1] Run targeted backend tests and the complete existing Maven suite, frontend production build/type-check/lint, and Playwright suite; record exact pass/fail/blocked outcomes. Backend and frontend build/type-check/lint passed; the default Playwright run remains blocked for three live-auth checks because backend port 8080 is not running. See `frontend/test-results/TESTING-REPORT.md`.

## Requirement Mapping

| REQ ID | Description | Plan Items | Implementation Evidence |
|---|---|---|---|
| REQ-001 | Owner-scoped generation from persisted, minimized project context | 2.2, 2.3, 2.4, 2.5 | Requirement DTO/provider/service/controller |
| REQ-002 | Provider abstraction, explicit mock/OpenAI, JSON validation and errors | 1.1, 2.2, 2.3, 4.1 | `pom.xml`, provider implementations, parser, configuration/tests |
| REQ-003 | Backward-compatible persisted requirement model and fields | 2.1, 2.2, 2.4 | `Requirement.java`, repository/service and schema docs |
| REQ-004 | Authenticated owner-only requirement API | 2.2, 2.4, 2.5 | Repository, service, controller, authorization tests |
| REQ-005 | Pending/edit/approve/reject/delete review lifecycle | 2.4, 2.5, 3.1, 4.1 | Lifecycle service, routes, workspace UI/tests |
| REQ-006 | Responsive accessible project requirements workspace | 3.1 | `App.tsx`, CSS, Playwright |
| REQ-007 | Backend and browser verification without paid AI | 2.3, 2.4, 2.5, 3.1, 4.1 | JUnit/Mockito and Playwright tests |
| REQ-008 | Workflow/API/schema/provider documentation | 1.1, 4.1 | Docs and environment configuration |

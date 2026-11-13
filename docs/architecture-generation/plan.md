# Implementation Plan: AI-Powered Software Architecture Generation

**Scope:** Phase 2, integrated with the existing requirements workspace.
**Feature contract:** [feature-spec.md](./feature-spec.md)
**Stack:** Java 21, Spring Boot 3.4, Spring Data JPA/PostgreSQL, React 19, TypeScript, Vite.

## Decisions

1. Keep the existing `ArchitectureDocument` table and legacy columns. Add nullable content/style/provider/version columns and a source-requirement collection table so old rows remain readable and Hibernate's additive update does not require backfilling existing records.
2. Share the existing `APP_AI_PROVIDER` choice and one backend-configured OpenAI `ChatClient` across requirements and architecture adapters. Keep deterministic mock output explicitly labeled.
3. Generate one immutable document row per version. Lock the owned project during version allocation and enforce a project/version unique constraint.
4. Require the selected requirement IDs to belong to the requested project and have `APPROVED` status; do not infer requirements from client-supplied content.
5. Restrict generation to a Mermaid syntax subset used by the four required diagrams, validate before persistence, and validate/render in the frontend with Mermaid's parser and strict SVG rendering.
6. Preserve same-origin session and CSRF conventions. Expose all document operations under an owner-scoped project path.
7. Use H2 for routine integration tests. PostgreSQL verification must target only the dedicated `ai_sdlc_test` database; no destructive test cleanup or access to `ai_sdlc`.

## Tasks

### T001 — Extend architecture persistence and shared provider configuration

Add the document status, source requirements, content, style, provider, and version model; preserve all legacy fields. Refactor the OpenAI setup so both workflow adapters use the existing selection, key, model, timeouts, and ChatClient.

### T002 — Implement architecture DTOs, provider, parser, and Mermaid validation

Add validated selection/update/response contracts, project-and-approved-requirement context, contextual mock output, OpenAI prompts/adapter, JSON parsing, and validation of required content sections and four diagrams.

### T003 — Implement owner-scoped architecture repository, lifecycle, versioning, and APIs

Add repository queries and authenticated routes for generation, listing, retrieval, draft editing, approval/rejection, and version history. Ensure every lookup checks project owner and document/project membership.

### T004 — Add the architecture workspace and typed API

Integrate project navigation, default-select approved requirements, provide a structured document viewer/editor, safe Mermaid previews/source, status actions, and version navigation with loading/error/empty states.

### T005 — Test, document, and verify

Add focused backend and browser coverage. Run the existing Maven suite, frontend production build/type-check/lint, and targeted E2E tests. Verify PostgreSQL persistence/retrieval/restart only if the isolated test database and safe test access are available; record any limitation truthfully. Update architecture, schema, frontend, and workflow documentation.

## Required API contract

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/projects/{projectId}/architecture/generate` | Generate a new version from selected approved requirement IDs |
| GET | `/api/projects/{projectId}/architecture` | List a project's architecture versions |
| GET | `/api/projects/{projectId}/architecture/{documentId}` | Retrieve a document |
| PUT | `/api/projects/{projectId}/architecture/{documentId}` | Edit a draft |
| POST | `/api/projects/{projectId}/architecture/{documentId}/approve` | Approve a draft |
| POST | `/api/projects/{projectId}/architecture/{documentId}/reject` | Reject a draft |
| GET | `/api/projects/{projectId}/architecture/{documentId}/versions` | Inspect project version history from a document |

## Verification gates

- Backend: compile/tests, strict AI JSON and Mermaid validation, H2 repository/controller/authorization/version tests.
- Frontend: production build, Playwright type-check, architecture generation/edit/review/history/error/responsive tests.
- Persistence: no database reset; PostgreSQL target must be `ai_sdlc_test`, with fixture identity and cleanup behavior explicitly recorded.
- Documentation: examples match the implemented routes, schema, status transitions, provider disclosure, and environment variables.

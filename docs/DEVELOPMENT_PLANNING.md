# Development Planning

## Overview

Development Planning turns an owned project's approved requirements and approved architecture documents into actionable task drafts. It uses the existing AI provider configuration and persists tasks through the existing `DevelopmentTask` entity. Generated content is not treated as real model output when the mock provider is active, and no generated task is automatically approved.

The workspace is project-scoped and is available from the project navigation. It supports generation, milestone grouping, search, category/priority/status/milestone filters, task details and source provenance, draft editing, review, status updates, and safe deletion.

## Generation inputs and safeguards

`POST /api/projects/{projectId}/development-tasks/generate` requires an authenticated project owner and at least one approved requirement. The service passes the project name, description, objectives, domain, preferred stack, approved functional and non-functional requirements, and approved architecture documents to the configured provider. If no architecture document is approved, generation can proceed without architecture references.

The provider returns a structured `tasks` JSON array. Before persistence, the parser validates task field lengths, category and priority values, effort bounds, source-reference shapes, and dependency indexes; it rejects malformed or cyclic dependency graphs. The service then resolves every requirement code and architecture UUID against the approved project inputs. Generated tasks begin in `TODO` / `DRAFT` and include the provider name, requirement and architecture links, acceptance criteria, effort estimate, milestone, and task dependencies.

The default mock provider is identified as `MOCK_DEMO` and the API response includes `demo: true`. Optional OpenAI generation reuses the backend-only provider configuration used by other AI workflows. Credentials are not sent to the frontend.

## API

All endpoints are rooted at `/api/projects/{projectId}/development-tasks`; they require the existing authenticated session. Mutating requests use the application's CSRF token/header conventions.

| Method and path | Behavior |
|---|---|
| `POST /generate` | Generate and persist task drafts from approved project inputs |
| `GET /` | List tasks; optional `category`, `priority`, `status`, and `milestone` filters |
| `GET /{taskId}` | Retrieve task details, source references, dependencies, and provenance |
| `PUT /{taskId}` | Edit a draft and its same-project requirement, architecture, and task links |
| `PUT /{taskId}/status` | Change the delivery status of an approved task |
| `POST /{taskId}/approve` | Approve a draft |
| `POST /{taskId}/reject` | Reject a draft |
| `DELETE /{taskId}` | Delete an unapproved task when no task or traceability row depends on it |

Task category values are `FRONTEND`, `BACKEND`, `DATABASE`, `API`, `SECURITY`, `INTEGRATION`, and `OTHER`. Priority values are `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL`. Delivery statuses are `TODO`, `IN_PROGRESS`, `BLOCKED`, and `COMPLETED`; review statuses are `DRAFT`, `APPROVED`, and `REJECTED`.

The service returns not-found for projects/tasks outside the authenticated user's scope, validation errors for invalid references or dependency graphs, and conflicts for unavailable generation inputs or invalid review/deletion transitions. Standard validation and global error response handling are reused.

## Lifecycle and relationship rules

- Every project and task lookup is scoped to the authenticated owner's email and project ID.
- Generation uses approved requirements only, and any generated requirement/architecture reference must match the approved input set.
- Task edits require `DRAFT`; review actions only transition a draft to approved or rejected.
- Status changes require `APPROVED`.
- Dependencies must be distinct tasks in the same project, cannot include the task itself, and cannot create a cycle.
- A task cannot be deleted if it is approved, referenced as another task's dependency, or linked to requirement traceability.
- Requirement, architecture, and dependency references are validated before an edit is saved; source artifacts are not modified by task updates.
- The legacy `requirement_id` remains populated as the primary requirement for compatibility; all related requirements and architecture documents are also stored in join tables.

## Verification

The current implementation was verified with:

- Backend Maven suite: 34 tests passed, 0 failures/errors/skips using the in-memory H2 database in `backend/src/test/resources/application.properties`.
- Frontend production build: passed.
- Frontend lint: exit code 0; the existing `App.tsx` exhaustive-deps warning remains.
- Playwright TypeScript check: passed.
- Planning Playwright spec: 5 tests passed against mocked API routes, including rejection, project switching, and delayed loading state.

These results do not establish live PostgreSQL persistence for development tasks. No planning record was written to PostgreSQL and no planning restart-persistence test was run. Existing PostgreSQL restart-persistence evidence in the testing report covers authentication and projects only. OpenAI calls and production deployment have not been verified.

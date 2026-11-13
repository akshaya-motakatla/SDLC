# Requirements Engineering

## Current workflow

1. A signed-in owner opens a project from the dashboard or project list.
2. The requirements workspace loads saved requirements and shows the project's saved context.
3. The owner asks the selected provider to generate functional and non-functional requirements.
4. The backend parses and validates the provider's JSON before saving any records.
5. Generated requirements are persisted as `PENDING`; generation never approves content.
6. The owner can edit, approve, reject, or delete each requirement. Editing resets its status to `PENDING`.
7. Reopening the project loads the same saved requirements from the backend.
8. The owner can open Architecture, where only approved requirements are selected by default and are independently checked by the backend before use.

The local default is the `MOCK_DEMO` provider. Its output is test/demo data and the UI explicitly identifies it as **Demo output, not AI-generated**. The application does not silently substitute mock output when the real provider fails.

## Architecture and data flow

```text
React RequirementsWorkspace
  -> CSRF/session-aware requirementsApi
  -> authenticated RequirementController
  -> RequirementService (loads project by project ID + authenticated owner)
  -> RequirementGenerationProvider
       -> MockRequirementGenerationProvider (default)
       -> OpenAiRequirementGenerationProvider (Spring AI, explicit opt-in)
  -> RequirementJsonParser (validates structure, fields, enum values, and bounds)
  -> RequirementRepository
  -> PostgreSQL requirements row
```

Only the project's name, description, objectives, domain, and preferred technology stack are passed to the provider. Account information, credentials, session data, and unrelated database records are not included. The OpenAI adapter requests JSON-object mode and also instructs the model to return the required shape. Provider output is validated before persistence. Provider/network errors and invalid JSON return an error response; they do not produce successful-looking records.

The existing `Requirement` entity is extended additively. Acceptance criteria are stored as newline-separated text in a nullable column so existing rows remain readable; API-created and edited requirements require non-empty criteria. Existing UUID primary keys and non-null project foreign keys continue to enforce identity and project relationships. The existing Hibernate `update` behavior adds the column without clearing existing data.

## Architecture generation handoff
Approved requirements are the only requirements accepted by architecture generation. The owner may choose a subset, but the backend verifies each selected ID belongs to the owned project and is still approved. Each generation creates a versioned architecture draft; edits are limited to drafts, and approval/rejection is an explicit human action. The generation provider is shared with requirements engineering: mock output is labeled as demo content, and the OpenAI adapter is opt-in. The architecture API, Mermaid diagrams, database representation, configuration, and current validation limits are documented in [Architecture generation](./ARCHITECTURE_GENERATION.md).

## API

All routes require the existing authenticated session. Mutating requests require the existing CSRF token (`X-XSRF-TOKEN`).

| Method | Route | Result |
|---|---|---|
| `POST` | `/api/projects/{projectId}/requirements/generate` | `201`; provider/demo metadata and persisted requirements |
| `GET` | `/api/projects/{projectId}/requirements` | `200`; saved requirements for the owned project |
| `GET` | `/api/projects/{projectId}/requirements/{requirementId}` | `200`; one owned requirement |
| `PUT` | `/api/projects/{projectId}/requirements/{requirementId}` | `200`; edited item, reset to `PENDING` |
| `POST` | `/api/projects/{projectId}/requirements/{requirementId}/approve` | `200`; item marked `APPROVED` |
| `POST` | `/api/projects/{projectId}/requirements/{requirementId}/reject` | `200`; item marked `REJECTED` |
| `DELETE` | `/api/projects/{projectId}/requirements/{requirementId}` | `204`; item deleted when no dependent artifacts exist |

Project and requirement lookup is scoped to the authenticated owner's email. A missing or non-owned project/requirement returns `404`, avoiding disclosure of another user's records. Deletion returns `409` when user stories, tasks, test cases, traceability links, or architecture documents still reference the requirement, preserving downstream provenance rather than cascading into those records. DTO validation errors return `400`; invalid provider output and provider failures return `502`.

Generation response example:

```json
{
  "provider": "MOCK_DEMO",
  "demo": true,
  "requirements": [
    {
      "id": "generated-uuid",
      "projectId": "project-uuid",
      "code": "REQ-0001",
      "title": "Provide the core project workflow",
      "description": "Users can complete the primary workflow for their project.",
      "type": "FUNCTIONAL",
      "priority": "HIGH",
      "acceptanceCriteria": [
        "A user can complete the primary workflow using the project interface.",
        "The result is saved and available when the project is reopened."
      ],
      "status": "PENDING",
      "source": "MOCK_DEMO",
      "createdAt": "2026-01-01T12:00:00",
      "updatedAt": "2026-01-01T12:00:00"
    }
  ]
}
```

Edit request example:

```json
{
  "title": "Provide the core project workflow",
  "description": "Users can complete the primary workflow for their project.",
  "type": "FUNCTIONAL",
  "priority": "HIGH",
  "acceptanceCriteria": [
    "The primary workflow completes successfully.",
    "The result remains available after reopening the project."
  ]
}
```

## AI provider configuration

Spring AI OpenAI integration is compatible with the project's Spring Boot 3.4.0 and Java 21 versions. The OpenAI provider is opt-in:

- `APP_AI_PROVIDER`: `mock` (default) or `openai`
- `OPENAI_API_KEY`: required only when `APP_AI_PROVIDER=openai`
- `OPENAI_MODEL`: optional; defaults to `gpt-4o-mini`

Configure these only in the backend process environment or a secret manager. Never put API credentials in source control, frontend variables, logs, or test fixtures. An explicit OpenAI selection without a key fails startup with a clear configuration error; it does not switch to demo output. OpenAI network connections use a 5-second connect timeout and a 45-second read timeout. Provider failures, including rate limits and timeouts, are surfaced as retryable API errors.

## Verification

The standard backend suite uses the existing in-memory H2 test configuration. AI parsing, service behavior, controller routes, CSRF/session handling, owner isolation, status transitions, and deletion are tested without a paid model call. Playwright exercises the browser workflow with clearly marked API fixtures. These tests are not claims of live OpenAI or live PostgreSQL verification.

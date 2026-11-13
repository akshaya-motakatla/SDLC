# Architecture Overview

## 1. Objective
This document defines the architectural direction for the AI-Powered SDLC Automation Assistant. It focuses on the planned design, modular boundaries, data flow, runtime agent responsibilities, and the deployment model needed to satisfy the project requirements.

## 2. Architectural goals
- Keep the application modular and maintainable
- Separate orchestration from specialized AI agent logic
- Maintain clear human approval gates between analysis stages
- Support structured data exchange between services
- Provide extensibility for multiple LLM providers
- Support PostgreSQL persistence with JPA/Hibernate
- Ensure a clean separation between frontend and backend concerns

## 3. High-level system architecture
The system will follow a layered architecture with the following major parts:

1. Presentation layer
   - React + TypeScript frontend
   - Vite build tool
   - Tailwind-based UI layout
   - Dashboard and project management views

2. API layer
   - Spring Boot REST controllers
   - DTO-based request and response handling
   - Validation and global exception handling
   - Session-backed account authentication and per-account project authorization

3. Application services layer
   - Project management services
   - Requirement analysis orchestration
   - Architecture generation services
   - Planning and QA orchestration
   - Traceability services
   - Reporting services

4. Domain layer
   - Project, requirement, task, traceability, report, and log entities
   - Business rules for approvals and workflow validation

5. Data access layer
   - Spring Data JPA repositories
   - PostgreSQL database
   - Hibernate mappings and constraints

6. AI integration layer
   - Spring AI adapters
   - Provider abstraction for configurable LLM models
   - Structured prompt templates and response models

7. Workflow/orchestration layer
   - SdlcOrchestrator
   - Agent execution coordination
   - Human approval checkpoint handling
   - Retrieval and logging of agent outputs

## 4. Runtime workflow status
The longer-term product roadmap includes four runtime Java agents:

- RequirementAnalystAgent
- SystemArchitectAgent
- DevelopmentPlannerAgent
- QATestingAgent

Each agent is intended to be a dedicated Java component with a narrow responsibility and a clear contract for its inputs and outputs.

Requirements engineering and architecture generation are implemented workflow slices. Development planning, QA, traceability, reporting, and a general-purpose multi-agent orchestrator remain future work; the named roadmap agents should not be interpreted as implemented runtime components.

## 5. Workflow sequence
1. User creates a project and submits project description
2. The configured requirements provider generates functional and non-functional requirements
3. Generated requirements are presented for human approval
4. The owner selects approved requirements and requests architecture generation
5. The architecture provider returns structured content and four validated Mermaid diagrams
6. The owner edits the draft and approves or rejects it; each subsequent generation creates a new version
7. Development planning, QA, traceability validation, and final report generation are planned follow-on workflows

## 6. Human approval gates
A strict approval model is required:
- Requirement generation does not auto-approve itself
- Architecture, tasks, and tests must follow approved upstream artifacts
- Users must review and approve generated content before it is committed to downstream stages
- Approved/rejected architecture versions are immutable; a new generation creates a separate draft version

## 7. Data flow model
The system should use structured DTOs and domain objects, not ad hoc strings, to pass data between services. This ensures:
- Data integrity
- Easier validation
- Better logging and traceability
- More reliable downstream transformations

### 7.1 Implemented requirements flow
The React requirements workspace calls the session- and CSRF-aware API service. The authenticated controller delegates to `RequirementService`, which first resolves the project using both project ID and authenticated owner email. The service passes only saved project context to the configured provider, validates the returned JSON with `RequirementJsonParser`, and persists valid requirements as `PENDING`. Requirements are returned with provider/source metadata; invalid model output or provider errors produce an explicit error and are not persisted.

`APP_AI_PROVIDER` selects the local `mock` provider by default or the opt-in `openai` Spring AI adapter. The mock provider is represented in the API and UI as demo output. The OpenAI API key is read from the backend process environment and is never sent to the browser.

### 7.2 Implemented architecture flow
The React architecture workspace loads saved requirements and architecture documents through the session- and CSRF-aware API client. Approved requirements are selected by default, and the backend independently verifies project ownership and `APPROVED` status before it sends project context and the selected requirement text to the configured architecture provider. The response parser validates required sections and all four diagrams before a draft is persisted.

The existing Spring AI provider selection is shared with requirements generation: `mock` is the clearly labeled default, and `openai` is optional. A generation creates a new project-scoped version; only a `DRAFT` can be edited, approved, or rejected. All document operations verify ownership using the authenticated account. See [Architecture generation](./ARCHITECTURE_GENERATION.md) for the API, diagram syntax, persistence, setup, and validation details.

## 8. Proposed module breakdown
### Backend
- `src/main/java/com/.../project`
- `src/main/java/com/.../requirements`
- `src/main/java/com/.../architecture`
- `src/main/java/com/.../planning`
- `src/main/java/com/.../testing`
- `src/main/java/com/.../traceability`
- `src/main/java/com/.../reporting`
- `src/main/java/com/.../agent`
- `src/main/java/com/.../config`
- `src/main/java/com/.../security`

### Frontend
- `frontend/src/pages`
- `frontend/src/components`
- `frontend/src/features`
- `frontend/src/services`
- `frontend/src/hooks`
- `frontend/src/types`

### Documentation and automation
- `docs/`
- `.github/`

## 9. Security considerations
- Store API keys in environment variables
- Avoid exposing secrets in frontend code or logs
- Validate all incoming DTOs
- Hash account passwords with BCrypt; never return stored password hashes
- Use HttpOnly, SameSite session cookies and CSRF tokens for state-changing requests
- Scope project reads, updates, and deletes to the authenticated account
- Serve production sessions only over HTTPS
- Log agent execution without storing plain secrets

## 10. Quality attributes
### Maintainability
- Clear package boundaries
- Small, focused service classes
- Well-named DTOs and domain objects

### Scalability
- Support additional agents later without major restructuring
- Use JPA repositories and modular service design
- Keep orchestration centralized

### Observability
- Agent execution logs
- Error tracking
- Explicit workflow state transitions

### Reliability
- Retry policies for AI calls
- Validate structured outputs before persistence
- Clearly label demo output; do not silently substitute mock output after provider failure

## 11. Implemented phase status
- Phase 1 requirements engineering: implemented with persistence, owner-scoped APIs, provider abstraction, and human review.
- Phase 2 architecture generation: implemented with approved-requirement input, structured documents, Mermaid diagrams, editing, review, persistence, and project-level version history.
- Planning, QA/testing generation, traceability, reports, and cross-workflow orchestration: not implemented.

## 12. Decision summary
The architecture intentionally favors a clean, layered Java/Spring system with a React frontend and a clear orchestration layer. This keeps the product professional, extensible, and aligned with the presented SDLC automation objectives.

## 13. Account authentication
- Registration and sign-in use email/password credentials, with a unique username for display.
- Spring Security stores authentication in a server-side HTTP session. The session cookie is HttpOnly and SameSite=Lax.
- CSRF protection remains enabled for all unsafe HTTP methods. The frontend first obtains a CSRF token and sends it in the `X-XSRF-TOKEN` header.
- Project ownership is derived from the authenticated account; clients cannot choose or override the owner.
- The frontend and API should be served from the same origin (the Vite development server proxies `/api`). A production reverse proxy should preserve that same-origin boundary.
- The initial servlet-container session store is in-memory; use a shared session store such as Spring Session with Redis or JDBC before running multiple backend instances.

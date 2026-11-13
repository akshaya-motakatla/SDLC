# Phase 2 Feature Specification: AI-Powered Software Architecture Generation

**Status:** Approved implementation scope from the Phase 2 user request.
**Design inputs:** `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/DATABASE_SCHEMA.md`, the existing requirements workflow, and `.github/copilot-instructions.md`.

## Goal

Allow an authenticated project owner to generate a versioned architecture document from selected approved requirements, edit and review it, and reopen earlier versions. Keep AI provider selection explicit, label demo output, and persist documents under the existing PostgreSQL/JPA model.

## Requirements

### REQ-ARCH-001 — Approved requirement selection

The project workspace lists approved requirements and selects them by default. A caller may choose a subset, but the backend rejects empty selections, foreign-project requirements, and requirements that are not approved. The generated document identifies the source requirement IDs.

### REQ-ARCH-002 — Grounded architecture content

The generated architecture addresses project overview, goals, requirement mapping, architecture style, system/frontend/backend/database/API design, components and interactions, data flow, identity and authorization, security, scale and performance, justified technologies, integrations, risks, assumptions, and trade-offs. Content is grounded in persisted project context and selected requirement descriptions and acceptance criteria.

### REQ-ARCH-003 — Shared AI provider configuration

Use the existing provider selection, credentials, OpenAI ChatClient configuration, and provider metadata conventions. Mock generation is visibly identified as demo output. OpenAI is opt-in and must never silently fall back to demo output.

### REQ-ARCH-004 — Safe, versioned persistence

Persist each generated revision with a unique ID, project ID, source requirement IDs, title, content, architecture style, Mermaid diagrams, provider, status, version, and timestamps. Regeneration creates a new draft version and never overwrites an earlier approved or rejected document. Preserve legacy architecture columns and rows through additive schema evolution.

### REQ-ARCH-005 — Validated diagrams

Require system-context, component, entity-relationship, and workflow diagrams. Validate the generated Mermaid subset on the server before persistence. The UI previews each diagram, exposes its source, and reports render errors without showing a broken or misleading preview.

### REQ-ARCH-006 — Owner-scoped API and review lifecycle

Authenticated owners can generate, list, retrieve, edit drafts, approve, reject, and inspect earlier versions. Every route scopes both project and document to the authenticated owner. Reviewed documents are immutable; edits are draft-only. Existing session and CSRF controls remain enabled.

### REQ-ARCH-007 — Project architecture workspace

Add a responsive, keyboard-accessible architecture workspace with approved-requirement selection, generation loading/error states, document viewing/editing, Mermaid previews and source, status, approval/rejection actions, and version history. Controls must perform real API actions.

### REQ-ARCH-008 — Automated and operational verification

Add focused provider/parser/diagram, repository/service/controller/authorization/version, and browser workflow tests using mock AI output. Validate database persistence and retrieval after restart only against `ai_sdlc_test`; never reset or modify `ai_sdlc`.

### REQ-ARCH-009 — Documentation

Document workflow, component interactions, endpoints, diagrams, additive schema evolution, provider configuration, local execution, testing, and known limitations.

## Acceptance criteria

- Only selected approved requirements from the owned project reach the provider or are attached to a document.
- Generated and edited content remains a draft until a human approves or rejects it.
- Each regeneration creates a distinct increasing project version and earlier documents remain retrievable.
- Foreign owners receive not-found behavior for project/document access and mutations.
- Invalid provider output or Mermaid syntax is rejected before persistence.
- Mock responses identify themselves as demo output; OpenAI configuration remains backend-only.
- Existing project, auth, and requirement behavior is preserved.
- Standard tests use mock providers and isolated test persistence; no paid AI is required.

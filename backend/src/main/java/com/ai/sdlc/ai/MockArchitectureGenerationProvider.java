package com.ai.sdlc.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class MockArchitectureGenerationProvider implements ArchitectureGenerationProvider {
    private final ObjectMapper objectMapper;

    public MockArchitectureGenerationProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(ArchitectureContext context) {
        String requirementMapping = context.requirements().stream()
                .map(requirement -> "- **%s — %s (%s/%s):** %s Acceptance: %s"
                        .formatted(
                                requirement.code(),
                                requirement.title(),
                                requirement.type(),
                                requirement.priority(),
                                requirement.description(),
                                String.join("; ", requirement.acceptanceCriteria())
                        ))
                .collect(Collectors.joining("\n"));
        String objectives = context.objectives() == null || context.objectives().isBlank()
                ? "No project objectives were supplied; confirm objectives with the project owner."
                : context.objectives();
        String stack = context.preferredStack() == null || context.preferredStack().isBlank()
                ? "No preferred stack was supplied; validate the recommendations with the project owner."
                : context.preferredStack();
        String content = """
                ## Project overview
                **%s** is a %s project. %s

                ## Architecture goals
                - Support the stated project objective: %s
                - Keep the UI, authenticated API, domain workflows, and persistence responsibilities distinct.
                - Preserve reviewable links from this design to the selected approved requirements.

                ## Functional and non-functional requirement mapping
                %s

                ## Recommended architecture style
                A layered application with a React client and a Spring Boot modular monolith is the recommended starting point for this project. It matches the declared stack (%s) and keeps deployment and transactional boundaries straightforward. Split services only when measured scale or independent ownership justifies the operational cost.

                ## High-level system architecture
                The project owner uses the browser workspace; the React client calls the authenticated Spring Boot API; the API applies project ownership and workflow rules, then reads or writes PostgreSQL. Architecture generation uses the configured provider only when explicitly selected; the current mock provider is demo output.

                ## Frontend architecture
                Organize the React/TypeScript client around project workflows, with typed API services, accessible forms, explicit loading/error states, and a document viewer/editor. Keep session cookies and CSRF tokens in the existing same-origin API flow; never store provider credentials in browser code.

                ## Backend architecture
                Use Spring MVC controllers for validated HTTP contracts, focused services for ownership and workflow rules, JPA repositories for persistence, and provider adapters behind the shared AI configuration. Persist generated documents as drafts before human approval.

                ## Database architecture
                PostgreSQL stores the project, approved requirements, architecture documents, and document-to-source-requirement links. Each regeneration creates an immutable new version; edits are limited to drafts. Existing architecture columns remain available for legacy rows.

                ## API design and responsibilities
                Project-scoped architecture routes generate, list, retrieve, edit, approve, reject, and inspect versions. The server derives the owner from the authenticated session and verifies every selected requirement against the project before calling the provider.

                ## Major components and their interactions
                - React project workspace: requirement selection, document review, editor, diagram preview, and version history.
                - Spring Boot architecture controller/service: request validation, owner checks, approved-requirement selection, version lifecycle, and response mapping.
                - Shared AI provider adapter: contextual mock/demo or explicitly configured OpenAI generation.
                - JPA repositories and PostgreSQL: durable project, requirements, source links, documents, and revisions.

                ## Data flow
                The owner selects approved requirement IDs; the API loads project and requirement data from PostgreSQL, sends only that context to the configured provider, validates structured content and Mermaid diagrams, then saves a new DRAFT version with source IDs. The owner edits and explicitly approves or rejects the saved draft.

                ## Authentication and authorization design
                Reuse the existing server-side Spring Security session and CSRF protections. Every architecture operation is scoped through the project owner; the browser cannot nominate an owner or attach requirements from another project.

                ## Security considerations
                Keep OpenAI keys in backend environment configuration, treat project text as untrusted data in prompts, validate provider output before persistence, and render Mermaid with strict client-side security settings. Production requires HTTPS and secure session cookies.

                ## Scalability and performance considerations
                Start with the existing single-instance application and PostgreSQL transaction model. Bound generation payload sizes, avoid sending unrelated project data, and monitor provider latency. Use shared session storage and measured capacity planning before horizontally scaling.

                ## Technology recommendations and justifications
                - React and TypeScript fit the existing accessible workflow UI and typed API boundary.
                - Spring Boot and JPA reuse the project's authentication, validation, transaction, and ownership patterns.
                - PostgreSQL provides durable relational constraints for requirements and revision history.
                - Mermaid provides portable, text-based diagrams; use strict rendering and validate source before saving.
                - The declared project stack is **%s**; verify any additional technology against deployment and team constraints before adoption.

                ## External integrations and dependencies
                The configured AI provider is the only external generation dependency. Mock mode has no external service dependency. OpenAI mode requires backend network access, a valid environment-supplied key, and operational limits appropriate to the deployment.

                ## Risks, assumptions, and trade-offs
                - Assumption: the selected approved requirements are the authoritative architecture scope.
                - Risk: generated content can be incomplete or inaccurate; mitigate with explicit human review and traceable source IDs.
                - Risk: provider latency or rate limiting can delay generation; surface failures and never substitute demo output silently.
                - Trade-off: a modular monolith favors simple transactions and deployment over independent service scaling.
                - Confirm retention, backup, access policy, target deployment environment, and production session storage before launch.
                """.formatted(
                context.projectName(),
                context.domain(),
                context.description(),
                objectives,
                requirementMapping,
                stack,
                stack
        );

        Map<String, String> diagrams = new LinkedHashMap<>();
        diagrams.put("systemContext", """
                flowchart LR
                  owner["Project owner"] -->|Uses| ui["React project workspace"]
                  ui -->|Session and CSRF API calls| api["Spring Boot REST API"]
                  api -->|Reads and writes| db["PostgreSQL project data"]
                  api -->|Optional generation| provider["Configured AI provider"]
                """.strip());
        diagrams.put("components", """
                flowchart LR
                  workspace["Project architecture workspace"] -->|Calls| controller["Architecture controller"]
                  controller -->|Coordinates| service["Architecture service"]
                  service -->|Loads approved inputs| requirementRepo["Requirement repository"]
                  service -->|Creates versions| architectureRepo["Architecture repository"]
                  service -->|Uses shared configuration| aiProvider["Architecture provider"]
                  requirementRepo -->|Reads| database["PostgreSQL"]
                  architectureRepo -->|Persists| database
                """.strip());
        diagrams.put("entityRelationship", """
                erDiagram
                  USER ||--o{ PROJECT : owns
                  PROJECT ||--o{ REQUIREMENT : contains
                  PROJECT ||--o{ ARCHITECTURE_DOCUMENT : has_versions
                  ARCHITECTURE_DOCUMENT ||--o{ ARCHITECTURE_DOCUMENT_REQUIREMENT : cites
                  REQUIREMENT ||--o{ ARCHITECTURE_DOCUMENT_REQUIREMENT : sourced_by
                """.strip());
        diagrams.put("workflow", """
                sequenceDiagram
                  participant owner as Project owner
                  participant ui as Architecture workspace
                  participant api as Spring Boot API
                  participant ai as Configured AI provider
                  participant db as PostgreSQL
                  owner->>ui: Select approved requirements
                  ui->>api: Generate with selected requirement IDs
                  api->>db: Verify project ownership and approvals
                  api->>ai: Request grounded architecture
                  ai-->>api: Return structured content and diagrams
                  api->>db: Save new draft version
                  api-->>ui: Return source-linked document
                  owner->>ui: Review and approve
                  ui->>api: Approve draft document
                  api->>db: Persist approved status
                """.strip());

        try {
            return objectMapper.writeValueAsString(new GeneratedArchitecture(
                    context.projectName() + " — Software Architecture",
                    "Layered modular monolith",
                    content,
                    diagrams
            ));
        } catch (JsonProcessingException exception) {
            throw new ArchitectureGenerationException("The demo architecture response could not be serialized.", exception);
        }
    }

    @Override
    public String providerName() {
        return "MOCK_DEMO";
    }

    @Override
    public boolean isDemo() {
        return true;
    }
}

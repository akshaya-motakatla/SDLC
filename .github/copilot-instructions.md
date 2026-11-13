# GitHub Copilot Instructions for AI-Powered SDLC Automation Assistant

## Project mission
Build a professional, full-stack SDLC automation application that turns natural-language project ideas into structured engineering artifacts. The system should support requirement analysis, architecture, planning, testing, traceability, and reporting.

## Core principles
- Keep the architecture modular and layered.
- Prefer clear design separation between frontend, backend, orchestration, and AI components.
- Do not implement production functionality without first aligning to the approved design documents.
- Do not auto-approve generated AI output; require human review.
- Use actual LLM calls only when credentials and runtime configuration are available. In demo mode, clearly label sample outputs.
- Keep security and validation in mind: no secrets in code, no hardcoded credentials, no insecure defaults.
- Use Java Spring Boot for backend logic and React + TypeScript for the frontend.

## Required workflow for this repository
1. Review and align with the Phase 1 planning documents before any implementation.
2. Keep code changes focused on the active approved phase.
3. Validate the changed component with the smallest relevant test or build step.
4. Prevent fake or fabricated functionality from being presented as real execution results.
5. Favor maintainability, readability, and SOLID principles over quick hacks.

## Coding guidelines
- Use conventional Java naming patterns and package organization.
- Prefer DTOs for API contracts and validation.
- Keep service responsibilities narrow and explicitly separated.
- Use PostgreSQL and JPA as the default persistence model.
- Use TypeScript on the frontend with clear component boundaries and reusable hooks.
- Use Tailwind CSS for styling and accessible UI patterns.

## Required documentation discipline
- Update the relevant docs when product or architectural decisions change.
- Keep the generated PRD, architecture, schema, and planning docs consistent with implementation decisions.
- Document assumptions, mismatches, and approval gates as part of the workflow.

## Repository-specific guardrails
- Do not create the Spring Boot backend before the Phase 1 documents are reviewed and approved.
- Do not create the React frontend before backend and architecture expectations are aligned.
- Do not pretend AI execution occurred when there was no actual model call.
- Keep GitHub Copilot customization files distinct from the runtime Java agents inside the application.

## Expected deliverables
The project should eventually include:
- Project management and workspace creation
- Requirement analysis workflows
- System architecture generation
- Development planning and roadmap generation
- QA and testing case generation
- Requirement traceability matrix
- Final SDLC report generation

## Output quality bar
The final implementation should read like a professional SaaS application, not a prototype or static mockup. Aim for maintainable code, thoughtful UX, and evidence-based engineering artifacts.

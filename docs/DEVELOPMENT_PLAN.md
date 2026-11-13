# Development Plan

## 1. Project objective
Build a professional full-stack application that transforms natural-language project ideas into structured SDLC artifacts using AI-assisted workflows, human review, and traceability.

## 2. Phase summary

### Phase 1 - Documentation and architecture definition (Complete)
Deliverables:
- Product requirements document
- Architecture overview
- Database schema specification
- Development plan
- Proposed folder structure
- Initial GitHub Copilot customization documents

Outcome:
A clear product and technical foundation before implementation begins.

### Phase 2 - Backend foundation (Complete)
- Initialize Spring Boot project
- Configure PostgreSQL and JPA
- Create domain entities and repositories
- Add validation, exception handling, and security setup
- Implement project management APIs

### Phase 3 - React frontend foundation (Complete)
- Initialize Vite + React + TypeScript app
- Configure Tailwind and design system
- Build responsive dashboard and project screens
- Connect project create, list, update, and delete flows to the project API
- Show explicit loading, empty, and API error states without sample project data
- Add account registration, sign-in, session restoration, and sign-out screens

### Phase 4 - Project management expansion
- Add detailed project overview and workspace management
- Extend lifecycle and dashboard metrics as supporting backend APIs become available
- Add richer project filtering and status workflows
- Add account-management features such as password reset and email verification before production launch

### Phase 5 - Runtime AI agents
- Implement RequirementAnalystAgent
- Implement SystemArchitectAgent
- Implement DevelopmentPlannerAgent
- Implement QATestingAgent
- Add structured AI prompt handling and output validation

### Phase 6 - Orchestration and human approval workflow
- Build SdlcOrchestrator
- Validate golden-path agent execution
- Add review and approval steps for generated artifacts
- Add retry and execution logging

### Phase 7 - Traceability and reporting
- Create requirement traceability matrix
- Add coverage calculations with evidence-based logic
- Implement PDF and Markdown report export

### Phase 8 - GitHub Copilot customization
- Add `.github/copilot-instructions.md`
- Add custom agent definitions
- Add reusable skills for requirement, architecture, planning, and testing domains

### Phase 9 - Quality assurance and documentation
- Unit tests, integration tests, and UI tests
- Bug fixing and security review
- Complete README and deployment guidance

## 3. Implementation strategy
- Work in small, reviewable slices
- Validate after each major phase
- Prevent premature backend/frontend coding beyond the approved phase
- Keep all generated requirements human-reviewed before downstream processing

## 4. Risks and mitigations
### Risk: AI output quality variability
Mitigation: Strong prompt structure, validation, and user approval gates.

### Risk: Overengineering
Mitigation: Keep the architecture layered and intentionally simple for a first version.

### Risk: Missing traceability
Mitigation: Define the requirement-to-task-to-test mapping early and validate it before report generation.

### Risk: Incomplete documentation
Mitigation: Maintain the docs set as part of the workflow and keep them updated with each phase.

## 5. Definition of done for Phase 1
- Product vision and requirements are clear
- Architecture direction is recorded
- Data model is mapped and consistent
- Development milestones are defined
- Phase 1 documents are reviewed and approved before implementation starts

## 6. Phase 3 implementation status
- Vite + React + TypeScript frontend scaffolded under `frontend/`
- Tailwind CSS and Lucide React configured
- Responsive dashboard and project list screens implemented
- Project CRUD uses the Spring Boot `/api/projects` endpoints
- Metrics that do not yet have backend data are explicitly marked unavailable
- Frontend production build and lint checks pass
- See `FRONTEND.md` for local run instructions and current API boundaries

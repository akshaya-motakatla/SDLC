# Proposed Project Folder Structure

```text
ai-sdlc-automation-assistant/
├── .github/
│   ├── copilot-instructions.md
│   ├── agents/
│   │   ├── requirement-analyst.agent.md
│   │   ├── system-architect.agent.md
│   │   ├── development-planner.agent.md
│   │   └── qa-engineer.agent.md
│   └── skills/
│       ├── requirement-generation/
│       │   └── SKILL.md
│       ├── architecture-design/
│       │   └── SKILL.md
│       ├── development-planning/
│       │   └── SKILL.md
│       └── test-case-generation/
│           └── SKILL.md
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── DATABASE_SCHEMA.md
│   ├── DEVELOPMENT_PLAN.md
│   ├── PROJECT_FOLDER_STRUCTURE.md
│   ├── FRONTEND.md
│   ├── AUTHENTICATION.md
│   └── README.md (later phase)
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   ├── pom.xml
│   └── README.md
├── frontend/
│   ├── src/
│   │   ├── services/
│   │   │   ├── apiClient.ts
│   │   │   ├── authApi.ts
│   │   │   └── projectsApi.ts
│   │   ├── types/
│   │   │   ├── auth.ts
│   │   │   └── project.ts
│   │   ├── App.tsx
│   │   └── index.css
│   ├── public/
│   ├── .env.example
│   ├── package.json
│   ├── package-lock.json
│   ├── vite.config.ts
│   └── tsconfig*.json
├── docker/
│   └── docker-compose.yml (later phase)
├── README.md
├── .gitignore
└── .env.example
```

## Purpose of the structure
- `.github/` holds repository-level Copilot guidance and custom agent definitions.
- `docs/` stores project planning, requirements, architecture, and database guidance.
- `backend/` will hold the Spring Boot application and Java code.
- `frontend/` will hold the React + Vite application.
- `docker/` is reserved for environment/container setup when needed later.
- Root-level files support project onboarding and environment setup.

## Phase status
The backend foundation and frontend Phase 3 scaffold now exist. This structure remains the target layout as later workflow, agent, and deployment modules are added.

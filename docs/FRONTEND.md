# Frontend Foundation

## Stack
- React and TypeScript
- Vite
- Tailwind CSS 4
- Lucide React icons

## Local development
1. Start PostgreSQL and create the `ai_sdlc` database expected by the backend.
2. Start the Spring Boot application from `backend/`.
3. In a separate terminal, run:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

Vite serves the frontend on its local development port and proxies `/api` requests to `http://localhost:8080`. Set `VITE_API_BASE_URL` in a local `frontend/.env` file when the API is hosted at a different URL. Never put secrets in frontend environment variables; Vite exposes `VITE_*` values to browser code.

## Implemented screens
- Account sign-in and registration, with session restoration and sign-out.
- Dashboard: workspace project totals, project statuses, workflow roadmap, and an explicitly empty agent activity area.
- Projects: project listing, search, create, edit, and delete using the backend project API.
- Requirements workspace: open an owned project, review saved project context, generate functional/non-functional requirements, and edit, approve, reject, or delete them.
- Architecture workspace: select approved requirements, generate a draft architecture, edit its content, review four Mermaid diagrams and their source, approve/reject drafts, and reopen earlier versions.
- Development Planning workspace: generate project-scoped tasks from approved requirements and approved architecture documents, inspect provenance and dependencies, search/filter/group tasks, edit drafts, approve/reject them, and update delivery status for approved tasks.
- Light/dark theme toggle, responsive layouts, and accessible form labels.

## Data and availability behavior
- Authentication uses a Spring Security server-side session and CSRF tokens. The browser sends the session cookie with API requests; no access token is stored in browser storage.
- The current servlet session store is in-memory. It is suitable for the single-instance development setup but is not durable across restarts or shared across multiple backend instances.
- Project records are fetched from `GET /api/projects` and mutated through the matching POST, PUT, and DELETE endpoints.
- No sample or mock project records are rendered.
- Project counts are derived from successfully loaded API data. Metrics without backend endpoints are shown as unavailable rather than inferred.
- If the backend cannot be reached, the dashboard shows a connection error, retry action, and a distinct unavailable-data state.
- Requirement generation is available under each project's Requirements workspace. The default mock provider is clearly disclosed as demo output; the UI never labels it as real AI generation.
- Architecture generation uses the same provider setting and clearly identifies mock output. The browser validates Mermaid source before rendering in strict mode; invalid source is reported and the broken preview is withheld while its source remains viewable.
- Development task generation uses the existing provider setting; mock output is labeled as demo output. Generated tasks remain drafts until explicitly approved.
- Task data is fetched and mutated through the selected project's authenticated API routes. Switching projects remounts the workspace so task state and filters cannot leak between projects.
- QA generation, a traceability-matrix workspace, notifications, and account settings remain future-phase features.
- Session cookies require HTTPS in production; configure `SESSION_COOKIE_SECURE=true` and serve frontend and API from the same origin or explicitly configure a trusted CORS/CSRF deployment boundary.

## Quality checks
Run `npm run build` for TypeScript checking and a production Vite build. Run `npm run lint` for the configured Oxlint check.

Run `npm run typecheck:e2e` to type-check Playwright tests. With the Vite development server running, run `npm run test:e2e -- tests/planning.spec.ts --workers=1` for the planning workflow, or include `tests/architecture.spec.ts` and `tests/requirements.spec.ts` for earlier feature workflows. Mermaid is dynamically loaded; its production bundle includes large parser/layout chunks, which currently trigger Vite's chunk-size warning.

See [Development Planning](./DEVELOPMENT_PLANNING.md) for supported operations and lifecycle rules.

import { expect, test } from '@playwright/test'
import type { Page, Route } from '@playwright/test'
import type { ArchitectureDocument } from '../src/types/architecture.js'

const project = {
  id: 'test-project',
  name: 'Architecture workspace',
  description: 'A project used to verify architecture generation and review.',
  objectives: 'Tie architecture decisions to approved requirements.',
  domain: 'Software',
  preferredStack: 'React, Spring Boot, PostgreSQL',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}

const requirements = [
  {
    id: 'approved-1',
    projectId: project.id,
    code: 'REQ-0001',
    title: 'Create a project workspace',
    description: 'The owner can create and reopen project workspaces.',
    type: 'FUNCTIONAL',
    priority: 'HIGH',
    acceptanceCriteria: ['Saved project details appear when reopened.'],
    status: 'APPROVED',
    source: 'MOCK_DEMO',
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  },
  {
    id: 'approved-2',
    projectId: project.id,
    code: 'REQ-0002',
    title: 'Protect project data',
    description: 'Only the authenticated project owner can access project data.',
    type: 'NON_FUNCTIONAL',
    priority: 'HIGH',
    acceptanceCriteria: ['A different account cannot read this project.'],
    status: 'APPROVED',
    source: 'MOCK_DEMO',
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  },
  {
    id: 'pending-1',
    projectId: project.id,
    code: 'REQ-0003',
    title: 'Pending requirement',
    description: 'This requirement has not been approved.',
    type: 'FUNCTIONAL',
    priority: 'MEDIUM',
    acceptanceCriteria: ['It must not be used by default.'],
    status: 'PENDING',
    source: 'MOCK_DEMO',
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  },
]

const diagrams = {
  systemContext: 'flowchart LR\n  owner["Project owner"] -->|Uses| ui["React workspace"]\n  ui --> api["Spring Boot API"]',
  components: 'flowchart LR\n  workspace["Architecture workspace"] --> controller["Architecture controller"]\n  controller --> service["Architecture service"]',
  entityRelationship: 'erDiagram\n  PROJECT ||--o{ REQUIREMENT : contains\n  PROJECT ||--o{ ARCHITECTURE_DOCUMENT : has',
  workflow: 'sequenceDiagram\n  participant owner as Project owner\n  participant api as Spring Boot API\n  owner->>api: Generate architecture\n  api-->>owner: Return draft',
}

const content = `## Project overview
${project.name} supports project workspace creation and private project data.

## Architecture goals
- Persist owner workspaces and keep review evidence traceable.

## Functional and non-functional requirement mapping
- REQ-0001: Project workspace creation.
- REQ-0002: Owner-only project access.

## Recommended architecture style
A layered modular monolith.

## High-level system architecture
React client to Spring Boot API to PostgreSQL.

## Frontend architecture
Typed, accessible React project workspace.

## Backend architecture
Spring MVC controllers, services, and repositories.

## Database architecture
PostgreSQL stores projects, requirements, and architecture versions.

## API design and responsibilities
Project-scoped authenticated REST operations.

## Major components and their interactions
The workspace calls the architecture API.

## Data flow
Approved requirements are validated and saved as a draft.

## Authentication and authorization design
Use the existing session cookie and CSRF boundary.

## Security considerations
Keep model keys on the backend.

## Scalability and performance considerations
Start with a modular monolith and measure before splitting services.

## Technology recommendations and justifications
Use the declared React, Spring Boot, and PostgreSQL stack.

## External integrations and dependencies
OpenAI is optional; mock mode uses no external service.

## Risks, assumptions, and trade-offs
Human review is required because generated content can be inaccurate.`

function makeDocument(version: number, status: ArchitectureDocument['status'] = 'DRAFT'): ArchitectureDocument {
  return {
    id: `architecture-${version}`,
    projectId: project.id,
    sourceRequirementIds: ['approved-1'],
    title: `Architecture version ${version}`,
    content,
    architectureStyle: 'Layered modular monolith',
    diagrams: { ...diagrams },
    status,
    version,
    provider: 'MOCK_DEMO',
    demo: true,
    createdAt: `2026-01-0${version}T00:00:00`,
    updatedAt: `2026-01-0${version}T00:00:00`,
  }
}

async function setupArchitecture(page: Page, initialDocuments: ArchitectureDocument[] = []) {
  const documents = [...initialDocuments]
  let latestGenerationSourceIds: string[] = []

  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200,
    json: { id: 'test-user', username: 'test-user', email: 'test@example.test', firstName: 'Test', lastName: 'User' },
  }))
  await page.route('**/api/auth/csrf', (route) => route.fulfill({ status: 200, json: { token: 'test-csrf-token' } }))
  await page.route('**/api/projects', (route) => route.fulfill({ status: 200, json: [project] }))
  await page.route('**/api/projects/test-project/requirements', (route) => route.fulfill({ status: 200, json: requirements }))
  await page.route('**/api/projects/test-project/architecture**', async (route: Route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    const method = route.request().method()
    if (path.endsWith('/generate') && method === 'POST') {
      latestGenerationSourceIds = route.request().postDataJSON().sourceRequirementIds as string[]
      const generated = makeDocument(documents.length + 1)
      generated.sourceRequirementIds = [...latestGenerationSourceIds]
      documents.unshift(generated)
      await route.fulfill({ status: 201, json: generated })
    } else if (path === '/api/projects/test-project/architecture' && method === 'GET') {
      await route.fulfill({ status: 200, json: documents })
    } else if (path.endsWith('/versions') && method === 'GET') {
      await route.fulfill({ status: 200, json: documents })
    } else if (method === 'PUT') {
      const id = path.split('/').at(-1)
      const index = documents.findIndex((document) => document.id === id)
      const payload = route.request().postDataJSON()
      documents[index] = { ...documents[index], ...payload, updatedAt: '2026-01-10T00:00:00' }
      await route.fulfill({ status: 200, json: documents[index] })
    } else if (method === 'POST' && /\/(approve|reject)$/.test(path)) {
      const parts = path.split('/')
      const id = parts.at(-2)
      const action = parts.at(-1)
      const index = documents.findIndex((document) => document.id === id)
      documents[index] = { ...documents[index], status: action === 'approve' ? 'APPROVED' : 'REJECTED' }
      await route.fulfill({ status: 200, json: documents[index] })
    } else {
      const id = path.split('/').at(-1)
      const document = documents.find((item) => item.id === id)
      await route.fulfill(document ? { status: 200, json: document } : { status: 404, json: { message: 'Architecture document not found.' } })
    }
  })

  return { documents, get latestGenerationSourceIds() { return latestGenerationSourceIds } }
}

async function openArchitecture(page: Page) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible()
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: /Projects/ }).click()
  await page.getByRole('button', { name: `Open architecture for ${project.name}` }).click()
  await expect(page.getByRole('heading', { name: project.name })).toBeVisible()
}

test('owner generates from approved selections, previews diagrams, edits, approves, and opens history', async ({ page }) => {
  const data = await setupArchitecture(page)
  await openArchitecture(page)
  await expect(page.getByText('No architecture documents yet')).toBeVisible()
  await expect(page.getByLabel(/REQ-0001/)).toBeChecked()
  await expect(page.getByLabel(/REQ-0002/)).toBeChecked()
  await expect(page.getByLabel(/REQ-0003/)).toHaveCount(0)
  await page.getByLabel(/REQ-0002/).uncheck()
  await page.getByRole('button', { name: 'Generate Architecture' }).click()
  await expect(page.getByText('Demo output — generated by the local mock provider')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Architecture version 1' })).toBeVisible()
  expect(data.latestGenerationSourceIds).toEqual(['approved-1'])
  await expect(page.getByRole('img', { name: 'System context diagram preview' })).toBeVisible()
  await expect(page.locator('.mermaid-preview svg').first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'View Mermaid source' }).first()).toBeVisible()
  await page.getByRole('button', { name: 'View Mermaid source' }).first().click()
  await expect(page.getByText(/flowchart LR/).first()).toBeVisible()

  await page.getByRole('button', { name: 'Edit draft' }).click()
  await page.getByLabel('Document title').fill('Edited architecture version 1')
  await page.getByRole('button', { name: 'Save draft' }).click()
  await expect(page.getByRole('heading', { name: 'Edited architecture version 1' })).toBeVisible()
  await page.getByRole('button', { name: 'Approve architecture' }).click()
  await expect(page.locator('.architecture-document .requirement-status')).toHaveText('APPROVED')
  await expect(page.getByText('This reviewed version is immutable.')).toBeVisible()

  await page.getByRole('button', { name: 'Generate Architecture' }).click()
  await expect(page.getByRole('heading', { name: 'Architecture version 2' })).toBeVisible()
  await page.getByRole('button', { name: /Version 1/ }).click()
  await expect(page.getByRole('heading', { name: 'Edited architecture version 1' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Approve architecture' })).toHaveCount(0)
})

test('owner can reject a draft and provider failures are shown without a document', async ({ page }) => {
  const data = await setupArchitecture(page)
  await openArchitecture(page)
  await page.getByRole('button', { name: 'Generate Architecture' }).click()
  await expect(page.getByRole('heading', { name: 'Architecture version 1' })).toBeVisible()
  await page.getByRole('button', { name: 'Reject' }).click()
  await expect(page.locator('.architecture-document .requirement-status')).toHaveText('REJECTED')
  expect(data.documents[0].status).toBe('REJECTED')

  await page.reload()
  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible()
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: /Projects/ }).click()
  await page.getByRole('button', { name: `Open architecture for ${project.name}` }).click()
  await expect(page.getByRole('heading', { name: 'Architecture version 1' })).toBeVisible()
})

test('architecture workspace handles generation errors and narrow viewports', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await setupArchitecture(page)
  await openArchitecture(page)
  await page.route('**/api/projects/test-project/architecture/generate', (route) => route.fulfill({
    status: 502,
    json: { message: 'The AI provider could not generate architecture. Please retry later.' },
  }))
  await page.getByRole('button', { name: 'Generate Architecture' }).click()
  await expect(page.getByRole('alert')).toContainText('The AI provider could not generate architecture.')
  await expect(page.getByText('No architecture documents yet')).toBeVisible()
})

test('invalid saved Mermaid source is reported without showing a broken preview', async ({ page }) => {
  const document = makeDocument(1)
  document.diagrams.systemContext = 'flowchart LR\n  invalid diagram line'
  await setupArchitecture(page, [document])
  await openArchitecture(page)

  await expect(page.getByRole('alert')).toContainText('This Mermaid source could not be rendered.')
  await expect(page.locator('.architecture-diagram').first().locator('svg')).toHaveCount(0)
  await page.getByRole('button', { name: 'View Mermaid source' }).first().click()
  await expect(page.locator('.mermaid-source').first()).toContainText('invalid diagram line')
})

import { expect, test } from '@playwright/test'
import type { Page, Route } from '@playwright/test'

interface MockRequirement {
  id: string
  projectId: string
  code: string
  title: string
  description: string
  type: 'FUNCTIONAL' | 'NON_FUNCTIONAL'
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
  acceptanceCriteria: string[]
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  source: string
  createdAt: string
  updatedAt: string
}

const project = {
  id: 'test-project',
  name: 'Requirements workspace',
  description: 'A project used to verify requirements review.',
  objectives: 'Keep requirements structured and reviewable.',
  domain: 'Software',
  preferredStack: 'React, Spring Boot',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}

function makeRequirement(id: string, type: MockRequirement['type']): MockRequirement {
  return {
    id,
    projectId: project.id,
    code: id === 'requirement-1' ? 'REQ-0001' : 'REQ-0002',
    title: id === 'requirement-1' ? 'Create a project workspace' : 'Protect project data',
    description: 'The project owner can use the project workspace.',
    type,
    priority: 'HIGH',
    acceptanceCriteria: ['The saved result can be reopened.', 'Only the project owner can access it.'],
    status: 'PENDING',
    source: 'MOCK_DEMO',
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  }
}

async function setupWorkspace(page: Page, initialRequirements: MockRequirement[] = [], holdRequirementsLoad = false) {
  const requirements = [...initialRequirements]
  let releaseRequirementsLoad = () => {}
  const requirementsLoadGate = new Promise<void>((resolve) => {
    releaseRequirementsLoad = resolve
  })
  const now = '2026-01-01T00:00:00'

  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200,
    json: { id: 'test-user', username: 'test-user', email: 'test@example.test', firstName: 'Test', lastName: 'User' },
  }))
  await page.route('**/api/auth/csrf', (route) => route.fulfill({ status: 200, json: { token: 'test-csrf-token' } }))
  await page.route('**/api/projects', (route) => route.fulfill({ status: 200, json: [project] }))
  await page.route('**/api/projects/test-project/requirements', async (route) => {
    if (route.request().method() === 'GET') {
      if (holdRequirementsLoad) await requirementsLoadGate
      await route.fulfill({ status: 200, json: requirements })
      return
    }
    await route.fulfill({ status: 405 })
  })
  await page.route('**/api/projects/test-project/requirements/generate', async (route) => {
    const generated = [makeRequirement('requirement-1', 'FUNCTIONAL'), makeRequirement('requirement-2', 'NON_FUNCTIONAL')]
    requirements.unshift(...generated)
    await route.fulfill({ status: 201, json: { provider: 'MOCK_DEMO', demo: true, requirements: generated } })
  })
  const handleRequirementMutation = async (route: Route) => {
    const url = new URL(route.request().url())
    const parts = url.pathname.split('/')
    if (parts.at(-1) === 'generate') {
      await route.fallback()
      return
    }
    const requirementId = parts.at(-1) === 'approve' || parts.at(-1) === 'reject'
      ? parts.at(-2)
      : parts.at(-1)
    const action = parts.at(-1)
    const index = requirements.findIndex((requirement) => requirement.id === requirementId)
    if (index < 0) {
      await route.fulfill({ status: 404, json: { message: 'Requirement not found.' } })
      return
    }

    if (route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON()
      requirements[index] = { ...requirements[index], ...payload, status: 'PENDING', updatedAt: now }
      await route.fulfill({ status: 200, json: requirements[index] })
    } else if (route.request().method() === 'POST' && (action === 'approve' || action === 'reject')) {
      requirements[index] = { ...requirements[index], status: action === 'approve' ? 'APPROVED' : 'REJECTED', updatedAt: now }
      await route.fulfill({ status: 200, json: requirements[index] })
    } else if (route.request().method() === 'DELETE') {
      requirements.splice(index, 1)
      await route.fulfill({ status: 204 })
    } else {
      await route.fulfill({ status: 405 })
    }
  }
  await page.route('**/api/projects/test-project/requirements/*', handleRequirementMutation)
  await page.route('**/api/projects/test-project/requirements/*/*', handleRequirementMutation)
  return { requirements, releaseRequirementsLoad }
}

async function openRequirements(page: Page) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible()
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: /Projects/ }).click()
  await page.getByRole('button', { name: `Open requirements for ${project.name}` }).click()
  await expect(page.getByRole('heading', { name: project.name })).toBeVisible()
}

test('project owner can generate, edit, review, and delete requirements', async ({ page }) => {
  const data = await setupWorkspace(page, [], true)
  await openRequirements(page)
  await expect(page.getByText('Loading saved requirements...')).toBeVisible()
  data.releaseRequirementsLoad()
  await expect(page.getByText('No requirements yet')).toBeVisible()
  await expect(page.getByText('Loading saved requirements...')).not.toBeVisible()

  await page.getByRole('button', { name: 'Generate Requirements' }).first().click()
  await expect(page.getByText('Demo output, not AI-generated')).toBeVisible()
  const cards = page.locator('.requirement-card')
  await expect(cards).toHaveCount(2)
  await expect(cards.first()).toContainText('PENDING')
  await expect(cards.first()).toContainText('Acceptance criteria')
  expect(data.requirements).toHaveLength(2)

  await page.reload()
  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible()
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: /Projects/ }).click()
  await page.getByRole('button', { name: `Open requirements for ${project.name}` }).click()
  await expect(page.locator('.requirement-card')).toHaveCount(2)

  const firstCard = cards.nth(0)
  await firstCard.getByRole('button', { name: 'Edit' }).click()
  await firstCard.getByLabel('Title').fill('Edited project workspace')
  await firstCard.getByLabel(/Acceptance criteria/).fill('Edited acceptance criterion')
  await firstCard.getByRole('button', { name: 'Save changes' }).click()
  await expect(firstCard).toContainText('Edited project workspace')
  await expect(firstCard).toContainText('PENDING')

  await firstCard.getByRole('button', { name: 'Approve' }).click()
  await expect(firstCard.locator('.requirement-status')).toHaveText('APPROVED')
  const secondCard = cards.nth(1)
  await secondCard.getByRole('button', { name: 'Reject' }).click()
  await expect(secondCard.locator('.requirement-status')).toHaveText('REJECTED')

  page.once('dialog', (dialog) => dialog.accept())
  await firstCard.getByRole('button', { name: 'Delete Edited project workspace' }).click()
  await expect(cards).toHaveCount(1)
  expect(data.requirements).toHaveLength(1)
})

test('provider failures are shown and do not produce success-shaped output', async ({ page }) => {
  await setupWorkspace(page)
  await openRequirements(page)
  await page.route('**/api/projects/test-project/requirements/generate', async (route) => {
    await route.fulfill({ status: 502, json: { message: 'The AI provider could not generate requirements. Please retry later.' } })
  })

  await page.getByRole('button', { name: 'Generate Requirements' }).first().click()
  await expect(page.getByRole('alert')).toContainText('The AI provider could not generate requirements.')
  await expect(page.getByText('No requirements yet')).toBeVisible()
  await expect(page.locator('.requirement-card')).toHaveCount(0)
  await expect(page.getByText('Demo output, not AI-generated')).toHaveCount(0)
})

test('requirements workspace is usable on a narrow viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await setupWorkspace(page)
  await openRequirements(page)
  await page.getByRole('button', { name: 'Generate Requirements' }).first().click()
  await expect(page.locator('.requirement-card')).toHaveCount(2)
  await expect(page.getByRole('button', { name: 'Approve' }).first()).toBeVisible()
})

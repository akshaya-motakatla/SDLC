import { expect, test } from '@playwright/test'
import type { Page, Route } from '@playwright/test'
import type { DevelopmentTask } from '../src/types/developmentTask.js'

const project = {
  id: 'planning-project',
  name: 'Planning workspace',
  description: 'Deliver a private, owner-managed software project workspace.',
  objectives: 'Implement secure project management.',
  domain: 'Software',
  preferredStack: 'React, Spring Boot, PostgreSQL',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}
const secondProject = {
  ...project,
  id: 'planning-project-2',
  name: 'Second planning workspace',
}

const requirements = [{
  id: 'requirement-1',
  projectId: project.id,
  code: 'REQ-0001',
  title: 'Protect project data',
  description: 'Only the project owner may access project data.',
  type: 'NON_FUNCTIONAL',
  priority: 'HIGH',
  acceptanceCriteria: ['Another account cannot read project data.'],
  status: 'APPROVED',
  source: 'MOCK_DEMO',
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}]

function task(reviewStatus: DevelopmentTask['reviewStatus'] = 'DRAFT'): DevelopmentTask {
  return {
    id: 'task-1',
    projectId: project.id,
    taskId: 'TASK-0001',
    title: 'Enforce owner-only project access',
    description: 'Enforce the owner check on project API reads and writes.',
    category: 'SECURITY',
    priority: 'HIGH',
    estimatedEffortHours: 8,
    dependencyIds: [],
    dependencyTaskIds: [],
    relatedRequirements: [{ id: 'requirement-1', code: 'REQ-0001', title: 'Protect project data' }],
    architectureReferences: [],
    milestone: 'Milestone 1',
    acceptanceCriteria: ['Another account cannot read project data.'],
    status: 'TODO',
    reviewStatus,
    generationProvider: 'MOCK_DEMO',
    demo: true,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  }
}

async function setupPlanning(
  page: Page,
  initialTasks: DevelopmentTask[] = [],
  failGeneration = false,
  projects = [project],
  delayTaskLoad = false,
) {
  const tasks = [...initialTasks]
  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200,
    json: { id: 'test-user', username: 'test-user', email: 'test@example.test', firstName: 'Test', lastName: 'User' },
  }))
  await page.route('**/api/auth/csrf', (route) => route.fulfill({ status: 200, json: { token: 'test-csrf-token' } }))
  await page.route('**/api/projects', (route) => route.fulfill({ status: 200, json: projects }))
  await page.route('**/api/projects/*/requirements', (route) => {
    const projectId = new URL(route.request().url()).pathname.split('/')[3]
    return route.fulfill({ status: 200, json: projectId === project.id ? requirements : [] })
  })
  await page.route('**/api/projects/*/architecture', (route) => route.fulfill({ status: 200, json: [] }))
  await page.route('**/api/projects/*/development-tasks**', async (route: Route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    const method = route.request().method()
    const projectId = path.split('/')[3]
    if (path.endsWith('/generate') && method === 'POST') {
      if (failGeneration) {
        await route.fulfill({ status: 502, json: { message: 'The task provider is temporarily unavailable.' } })
        return
      }
      const generated = task()
      tasks.unshift(generated)
      await route.fulfill({ status: 201, json: { provider: 'MOCK_DEMO', demo: true, tasks: [generated] } })
    } else if (path.endsWith('/development-tasks') && method === 'GET') {
      if (delayTaskLoad) await new Promise((resolve) => setTimeout(resolve, 300))
      const category = url.searchParams.get('category')
      const projectTasks = projectId === project.id ? tasks : []
      await route.fulfill({ status: 200, json: category ? projectTasks.filter((item) => item.category === category) : projectTasks })
    } else if (path.endsWith('/approve') || path.endsWith('/reject')) {
      const updated = { ...tasks[0], reviewStatus: path.endsWith('/approve') ? 'APPROVED' : 'REJECTED' } as DevelopmentTask
      tasks[0] = updated
      await route.fulfill({ status: 200, json: updated })
    } else if (path.endsWith('/status') && method === 'PUT') {
      const status = route.request().postDataJSON().status as DevelopmentTask['status']
      const updated = { ...tasks[0], status }
      tasks[0] = updated
      await route.fulfill({ status: 200, json: updated })
    } else if (method === 'PUT') {
      const payload = route.request().postDataJSON()
      const updated = {
        ...tasks[0],
        ...payload,
        relatedRequirements: [{ id: 'requirement-1', code: 'REQ-0001', title: 'Protect project data' }],
        architectureReferences: [],
      } as DevelopmentTask
      tasks[0] = updated
      await route.fulfill({ status: 200, json: updated })
    } else if (method === 'DELETE') {
      tasks.shift()
      await route.fulfill({ status: 204, body: '' })
    } else {
      await route.fulfill({ status: 404, json: { message: 'Task not found.' } })
    }
  })
  return tasks
}

async function openPlanning(page: Page) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible()
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: /Projects/ }).click()
  await page.getByRole('button', { name: `Open planning for ${project.name}` }).click()
  await expect(page.getByRole('heading', { name: project.name })).toBeVisible()
}

test('planning workspace generates, edits, reviews, and updates generated tasks', async ({ page }) => {
  const tasks = await setupPlanning(page)
  await openPlanning(page)
  await expect(page.getByText('No development tasks yet')).toBeVisible()
  await page.getByRole('button', { name: 'Generate Tasks' }).first().click()
  await expect(page.getByRole('heading', { name: 'Enforce owner-only project access' })).toBeVisible()
  await expect(page.getByText('Demo output, not AI-generated').first()).toBeVisible()

  await page.getByRole('button', { name: 'Details' }).click()
  await expect(page.getByText('REQ-0001 · Protect project data')).toBeVisible()
  await page.getByRole('button', { name: 'Hide details' }).click()

  await page.getByRole('button', { name: 'Edit' }).click()
  await page.getByLabel('Title').fill('Secure the project endpoints')
  await page.getByRole('button', { name: 'Save draft' }).click()
  await expect(page.getByRole('heading', { name: 'Secure the project endpoints' })).toBeVisible()
  await page.getByRole('button', { name: 'Approve' }).click()
  await expect(page.locator('.planning-task-card .requirement-status')).toHaveText('APPROVED')
  await page.getByLabel('Update status for TASK-0001').selectOption('IN_PROGRESS')
  await expect(page.locator('.planning-task-status')).toContainText('In Progress')
  expect(tasks[0].reviewStatus).toBe('APPROVED')
  expect(tasks[0].status).toBe('IN_PROGRESS')
})

test('planning filters and generation failures work at a narrow viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await setupPlanning(page, [task('APPROVED')], true)
  await openPlanning(page)
  await expect(page.getByRole('heading', { name: 'Enforce owner-only project access' })).toBeVisible()
  await page.getByLabel('Filter by category').selectOption('FRONTEND')
  await expect(page.getByText('No tasks match the current search and filters.')).toBeVisible()
  await page.getByLabel('Filter by category').selectOption('')
  await page.getByLabel('Search tasks').fill('not found')
  await expect(page.getByText('No tasks match the current search and filters.')).toBeVisible()
  await page.getByLabel('Search tasks').fill('')
  await page.getByRole('button', { name: 'Generate Tasks' }).click()
  await expect(page.getByRole('alert')).toContainText('The task provider is temporarily unavailable.')
})

test('owner can reject a generated draft', async ({ page }) => {
  await setupPlanning(page, [task()])
  await openPlanning(page)
  await page.getByRole('button', { name: 'Reject' }).click()
  await expect(page.locator('.planning-task-card .requirement-status')).toHaveText('REJECTED')
})

test('switching projects does not carry tasks into another workspace', async ({ page }) => {
  await setupPlanning(page, [task()], false, [project, secondProject])
  await openPlanning(page)
  await expect(page.getByRole('heading', { name: task().title })).toBeVisible()
  await page.getByRole('button', { name: 'Back to projects' }).click()
  await page.getByRole('button', { name: `Open planning for ${secondProject.name}` }).click()
  await expect(page.getByRole('heading', { name: secondProject.name })).toBeVisible()
  await expect(page.getByText('No development tasks yet')).toBeVisible()
  await expect(page.getByRole('heading', { name: task().title })).toHaveCount(0)
})

test('planning workspace displays its loading state while tasks are fetched', async ({ page }) => {
  await setupPlanning(page, [], false, [project], true)
  await openPlanning(page)
  await expect(page.getByText('Loading development tasks...')).toBeVisible()
  await expect(page.getByText('No development tasks yet')).toBeVisible()
})

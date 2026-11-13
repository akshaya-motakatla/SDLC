import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

interface MockProject {
  id: string;
  name: string;
  description: string;
  objectives: string;
  domain: string;
  preferredStack: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

async function mockAuthenticatedWorkspace(page: Page) {
  const projects: MockProject[] = [];
  let nextId = 1;
  const user = {
    id: 'test-user',
    username: 'test-user',
    email: 'test-user@example.test',
    firstName: 'Test',
    lastName: 'User',
  };
  const csrfToken = crypto.randomUUID();
  const now = '2026-01-01T00:00:00';

  await page.route('**/api/auth/me', (route) =>
    route.fulfill({ status: 200, json: user }),
  );
  await page.route('**/api/auth/csrf', (route) =>
    route.fulfill({ status: 200, json: { token: csrfToken } }),
  );
  await page.route('**/api/projects', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ status: 200, json: projects });
      return;
    }

    if (route.request().method() === 'POST') {
      const payload = route.request().postDataJSON();
      const project: MockProject = {
        ...payload,
        id: `test-project-${nextId++}`,
        createdAt: now,
        updatedAt: now,
      };
      projects.unshift(project);
      await route.fulfill({ status: 201, json: project });
      return;
    }

    await route.fulfill({ status: 405 });
  });
  await page.route('**/api/projects/*', async (route) => {
    const projectId = route.request().url().split('/').pop();
    const index = projects.findIndex((project) => project.id === projectId);

    if (route.request().method() === 'PUT' && index !== -1) {
      const payload = route.request().postDataJSON();
      projects[index] = { ...projects[index], ...payload, updatedAt: now };
      await route.fulfill({ status: 200, json: projects[index] });
      return;
    }

    if (route.request().method() === 'DELETE' && index !== -1) {
      projects.splice(index, 1);
      await route.fulfill({ status: 204 });
      return;
    }

    await route.fulfill({ status: 404, json: { message: 'Project not found.' } });
  });

  return projects;
}

test('project creation, listing, editing, search, and deletion work in the UI', async ({ page }) => {
  const projects = await mockAuthenticatedWorkspace(page);
  const projectName = `E2E project ${crypto.randomUUID()}`;

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible();
  await page.getByRole('button', { name: 'New project', exact: true }).click();

  const dialog = page.getByRole('dialog');
  await dialog.getByLabel(/Project name/).fill(projectName);
  await dialog.getByLabel(/Description/).fill('A test-only project used for browser workflow checks.');
  await dialog.getByLabel(/Objectives/).fill('Verify project workspace interactions.');
  await dialog.getByLabel(/Domain/).fill('Quality Engineering');
  await dialog.getByLabel(/Technology stack/).fill('React, TypeScript');
  await dialog.getByLabel('Status').selectOption('ACTIVE');
  await dialog.getByRole('button', { name: 'Create project', exact: true }).click();

  await expect(page.getByRole('heading', { name: 'Your projects' })).toBeVisible();
  await expect(page.getByRole('row', { name: new RegExp(projectName) })).toBeVisible();
  expect(projects).toHaveLength(1);

  await page.getByRole('button', { name: `Edit ${projectName}` }).click();
  const editDialog = page.getByRole('dialog');
  await editDialog.getByLabel(/Description/).fill('Updated test-only project description.');
  await editDialog.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Updated test-only project description.')).toBeVisible();

  await page.getByRole('searchbox', { name: 'Search projects' }).fill('no matching project');
  await expect(page.getByText('No matching projects')).toBeVisible();
  await page.getByRole('searchbox', { name: 'Search projects' }).clear();
  await expect(page.getByRole('row', { name: new RegExp(projectName) })).toBeVisible();

  page.once('dialog', (dialogEvent) => dialogEvent.accept());
  await page.getByRole('button', { name: `Delete ${projectName}` }).click();
  await expect(page.getByText('No projects yet')).toBeVisible();
  expect(projects).toHaveLength(0);
});

test('project API failures are surfaced and retry can recover', async ({ page }) => {
  let listRequests = 0;
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({
      status: 200,
      json: {
        id: 'test-user',
        username: 'test-user',
        email: 'test-user@example.test',
        firstName: 'Test',
        lastName: 'User',
      },
    }),
  );
  await page.route('**/api/projects', (route) => {
    listRequests += 1;
    return listRequests === 1
      ? route.fulfill({ status: 503, json: { message: 'Unavailable' } })
      : route.fulfill({ status: 200, json: [] });
  });

  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Project data is unavailable');
  await expect(page.getByRole('alert')).toContainText('The backend is not responding.');
  await page.getByRole('button', { name: 'Retry' }).click();
  await expect(page.getByText('No projects yet')).toBeVisible();
  expect(listRequests).toBeGreaterThanOrEqual(2);
});

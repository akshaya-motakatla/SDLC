
import { test, expect } from '@playwright/test';

test('homepage shows the product title and sign-in screen', async ({ page }) => {
  const consoleErrors: string[] = [];
  const failedRequests: string[] = [];
  const expectedUnauthorizedResponses = new Set<string>();
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  page.on('pageerror', (error) => consoleErrors.push(error.message));
  page.on('response', (response) => {
    if (response.url().endsWith('/api/auth/me') && response.status() === 401) {
      expectedUnauthorizedResponses.add(response.url());
    }
  });
  page.on('requestfailed', (request) => failedRequests.push(request.url()));

  await page.goto('/');

  await expect(page).toHaveTitle('SDLC Studio | Engineering workspace');
  await expect(page.getByRole('heading', { name: 'Sign in to your workspace' })).toBeVisible();

  const csrfResponse = await page.request.get('/api/auth/csrf');
  expect(csrfResponse.status()).toBe(200);
  expect((await csrfResponse.json()).token).toEqual(expect.any(String));

  const protectedResponse = await page.request.get('/api/projects');
  expect(protectedResponse.status()).toBe(401);
  expect(consoleErrors.filter((message) => !message.includes('status of 401 (Unauthorized)'))).toEqual([]);
  expect(failedRequests.filter((url) => !expectedUnauthorizedResponses.has(url))).toEqual([]);
});

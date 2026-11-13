import { expect, test } from '@playwright/test';

test('registration form enforces required fields and password length', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('tab', { name: 'Create account' }).click();

  const username = page.locator('input[autocomplete="username"]');
  const password = page.locator('input[autocomplete="new-password"]');
  await expect(username).toHaveAttribute('required', '');
  await expect(password).toHaveAttribute('minlength', '12');

  await username.fill('valid.user');
  await page.locator('input[autocomplete="given-name"]').fill('Valid');
  await page.locator('input[autocomplete="family-name"]').fill('User');
  await page.getByLabel('Email address').fill('valid@example.test');
  await password.fill('short');
  await page.getByRole('button', { name: 'Create account', exact: true }).last().click();

  expect(await password.evaluate((input: HTMLInputElement) => input.validity.valid)).toBe(false);
  await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible();
});

test('backend rejects an invalid registration payload without creating an account', async ({ request }) => {
  const csrfResponse = await request.get('/api/auth/csrf');
  expect(csrfResponse.status()).toBe(200);
  const csrfToken = (await csrfResponse.json()).token as string;

  const registrationResponse = await request.post('/api/auth/register', {
    data: {
      username: '',
      email: `invalid-${crypto.randomUUID()}@example.test`,
      password: '',
      firstName: '',
      lastName: '',
    },
    headers: { 'X-XSRF-TOKEN': csrfToken },
  });

  expect(registrationResponse.status()).toBe(400);
});

test('invalid credentials are rejected by the backend and shown to the user', async ({ page }) => {
  const email = `missing-${crypto.randomUUID()}@example.test`;
  await page.goto('/');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(`invalid-${crypto.randomUUID()}`);

  const loginResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/auth/login'),
  );
  await page.getByRole('button', { name: 'Sign in securely' }).click();
  const loginResponse = await loginResponsePromise;

  expect(loginResponse.status()).toBe(401);
  await expect(page.getByRole('alert')).toHaveText('Invalid email or password.');
});

test('a valid registration flow can enter the workspace and sign out', async ({ page }) => {
  const username = `e2e-${crypto.randomUUID()}`;
  const email = `${username}@example.test`;
  const user = {
    id: crypto.randomUUID(),
    username,
    email,
    firstName: 'Test',
    lastName: 'User',
  };
  const csrfToken = crypto.randomUUID();

  await page.route('**/api/auth/csrf', (route) =>
    route.fulfill({ status: 200, json: { token: csrfToken } }),
  );
  await page.route('**/api/auth/register', (route) =>
    route.fulfill({ status: 201, json: user }),
  );
  await page.route('**/api/projects', (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  await page.route('**/api/auth/logout', (route) =>
    route.fulfill({ status: 204 }),
  );

  await page.goto('/');
  await page.getByRole('tab', { name: 'Create account' }).click();
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="given-name"]').fill(user.firstName);
  await page.locator('input[autocomplete="family-name"]').fill(user.lastName);
  await page.getByLabel('Email address').fill(email);
  await page.locator('input[autocomplete="new-password"]').fill(`Test-${crypto.randomUUID()}-Pass!`);
  await page.getByRole('button', { name: 'Create account', exact: true }).last().click();

  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).first().click();
  await expect(page.getByRole('heading', { name: 'Sign in to your workspace' })).toBeVisible();
});

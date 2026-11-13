import { expect, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { rmSync, writeFileSync } from 'node:fs';

test.use({ screenshot: 'off', trace: 'off' });

function queryTestDatabase(query: string) {
  const psql = process.env.PSQL_BIN;
  if (!psql) {
    throw new Error('Set PSQL_BIN to the PostgreSQL client before enabling the live integration test.');
  }
  try {
    return execFileSync(psql, [
      '-X', '-w', '-h', 'localhost', '-p', '5432', '-U', 'ai_sdlc_app', '-d', 'ai_sdlc_test',
      '-A', '-t', '-c', query,
    ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], timeout: 10_000 }).trim();
  } catch {
    throw new Error('Could not verify ai_sdlc_test using the protected PostgreSQL passfile; live writes were not verified.');
  }
}

function assertTestDatabaseConnection(since?: Date) {
  const sessionCutoff = since
    ? `AND backend_start >= '${since.toISOString()}'::timestamptz`
    : '';
  const identity = queryTestDatabase(`
    SELECT current_database() || '|' || current_user || '|' ||
      (SELECT count(*)::text FROM pg_stat_activity
       WHERE datname = current_database() AND usename = current_user
         AND application_name ILIKE '%JDBC%' AND pid <> pg_backend_pid()
         ${sessionCutoff});
  `);
  const [database, username, jdbcSessions] = identity.split('|');
  expect(database).toBe('ai_sdlc_test');
  expect(username).toBe('ai_sdlc_app');
  expect(Number(jdbcSessions)).toBeGreaterThan(0);
  return Number(jdbcSessions);
}

function readPostgresRequirements(projectId: string) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(projectId)) {
    throw new Error('Project ID is not a UUID; refusing to query PostgreSQL.');
  }
  const rows = queryTestDatabase(`
    SELECT id::text || '|' || code || '|' || status || '|' || COALESCE(source, '')
    FROM requirements
    WHERE project_id = '${projectId}'::uuid
    ORDER BY code;
  `);
  if (!rows) return [];
  return rows.split(/\r?\n/).map((row) => {
    const [id, code, status, source] = row.split('|');
    return { id, code, status, source };
  });
}

test('live registration, requirements workflow, PostgreSQL persistence, authorization, and backend restart', async ({ page, browser }) => {
  test.skip(
    process.env.RUN_POSTGRES_INTEGRATION !== 'true',
    'Set RUN_POSTGRES_INTEGRATION=true only when Spring Boot targets ai_sdlc_test.',
  );
  test.setTimeout(360_000);
  const initialJdbcSessions = assertTestDatabaseConnection();
  console.log(`Verified ai_sdlc_test as ai_sdlc_app with ${initialJdbcSessions} active JDBC session(s) before live writes.`);

  const identifier = crypto.randomUUID().replaceAll('-', '');
  const email = `e2e-${identifier}@example.test`;
  const password = `Live-${crypto.randomUUID()}-Db!`;
  const projectName = `PostgreSQL persistence ${identifier}`;
  const projectDescription = 'Created through the real frontend and backend for isolated PostgreSQL verification.';

  await page.goto('/');
  await page.getByRole('tab', { name: 'Create account' }).click();
  await page.locator('input[autocomplete="username"]').fill(`e2e-${identifier}`);
  await page.locator('input[autocomplete="given-name"]').fill('Integration');
  await page.locator('input[autocomplete="family-name"]').fill('Test');
  await page.getByLabel('Email address').fill(email);
  await page.locator('input[autocomplete="new-password"]').fill(password);

  const registrationResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/auth/register')
      && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Create account', exact: true }).last().click();
  const registrationResponse = await registrationResponsePromise;
  expect(registrationResponse.status()).toBe(201);
  expect(await registrationResponse.json()).toMatchObject({ email, username: `e2e-${identifier}` });
  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible();

  const currentUserResponse = await page.request.get('/api/auth/me');
  expect(currentUserResponse.status()).toBe(200);
  expect(await currentUserResponse.json()).toMatchObject({ email });

  const emptyProjectsResponse = await page.request.get('/api/projects');
  expect(emptyProjectsResponse.status()).toBe(200);
  expect(await emptyProjectsResponse.json()).toEqual([]);

  await page.getByRole('button', { name: 'New project', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel(/Project name/).fill(projectName);
  await dialog.getByLabel(/Description/).fill(projectDescription);
  await dialog.getByLabel(/Objectives/).fill('Confirm PostgreSQL persistence across backend restart.');
  await dialog.getByLabel(/Domain/).fill('Quality Engineering');
  await dialog.getByLabel(/Technology stack/).fill('React, TypeScript, Spring Boot, PostgreSQL');
  await dialog.getByLabel('Status').selectOption('ACTIVE');

  const createProjectResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/projects')
      && response.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Create project', exact: true }).click();
  const createProjectResponse = await createProjectResponsePromise;
  expect(createProjectResponse.status()).toBe(201);
  const project = await createProjectResponse.json() as Record<string, unknown>;
  const projectShape = Object.fromEntries(Object.entries(project).map(([key, value]) => [
    key,
    value === null ? 'null' : Array.isArray(value) ? 'array' : typeof value,
  ]));
  console.log(`Live project response field types: ${JSON.stringify(projectShape)}`);
  expect(project).toMatchObject({
    id: expect.any(String),
    name: projectName,
    description: projectDescription,
    domain: 'Quality Engineering',
    preferredStack: 'React, TypeScript, Spring Boot, PostgreSQL',
    status: 'ACTIVE',
    createdAt: expect.any(String),
    updatedAt: expect.any(String),
  });
  const projectId = project.id as string;
  await expect(page.getByRole('alert')).toHaveCount(0, { timeout: 5_000 });
  await expect(page.getByRole('button', { name: `Edit ${projectName}` })).toBeVisible({ timeout: 5_000 });

  const projectUrl = `/api/projects/${encodeURIComponent(projectId)}`;
  const projectDetailResponse = await page.request.get(projectUrl);
  expect(projectDetailResponse.status()).toBe(200);
  expect(await projectDetailResponse.json()).toMatchObject({
    id: projectId,
    name: projectName,
    description: projectDescription,
    domain: 'Quality Engineering',
    preferredStack: 'React, TypeScript, Spring Boot, PostgreSQL',
    status: 'ACTIVE',
  });

  const updatedDescription = `${projectDescription} Updated.`;
  await page.getByRole('button', { name: `Edit ${projectName}` }).click();
  const editDialog = page.getByRole('dialog');
  await editDialog.getByLabel(/Description/).fill(updatedDescription);
  const updateProjectResponsePromise = page.waitForResponse((response) =>
    response.url() === new URL(projectUrl, page.url()).href
      && response.request().method() === 'PUT',
  );
  await editDialog.getByRole('button', { name: 'Save changes' }).click();
  const updateProjectResponse = await updateProjectResponsePromise;
  expect(updateProjectResponse.status()).toBe(200);
  expect(await updateProjectResponse.json()).toMatchObject({ id: projectId, description: updatedDescription });

  await page.getByRole('button', { name: `Open requirements for ${projectName}` }).click();
  await expect(page.getByText('No requirements yet')).toBeVisible();

  const generate = async () => {
    const responsePromise = page.waitForResponse((response) =>
      response.url().endsWith(`/api/projects/${encodeURIComponent(projectId)}/requirements/generate`)
        && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Generate Requirements' }).first().click();
    const response = await responsePromise;
    expect(response.status()).toBe(201);
    const result = await response.json() as {
      provider: string;
      demo: boolean;
      requirements: Array<{
        id: string;
        code: string;
        title: string;
        status: string;
        source: string;
      }>;
    };
    expect(['MOCK_DEMO', 'OPENAI']).toContain(result.provider);
    expect(result.demo).toBe(result.provider === 'MOCK_DEMO');
    expect(result.requirements.length).toBeGreaterThan(0);
    expect(result.requirements.every((requirement) =>
      requirement.status === 'PENDING' && requirement.source === result.provider,
    )).toBe(true);
    if (result.demo) {
      await expect(page.getByText('Demo output, not AI-generated')).toBeVisible();
    } else {
      await expect(page.getByText('Demo output, not AI-generated')).toHaveCount(0);
      await expect(page.getByText('Generation source: OPENAI')).toBeVisible();
    }
    return result;
  };

  const generated = await generate();
  let liveRequirements = [...generated.requirements];
  if (liveRequirements.length < 2) {
    const additional = await generate();
    expect(additional.provider).toBe(generated.provider);
    liveRequirements = [...liveRequirements, ...additional.requirements];
  }
  expect(liveRequirements.length).toBeGreaterThanOrEqual(2);

  const requirementsUrl = `/api/projects/${encodeURIComponent(projectId)}/requirements`;
  const savedRequirementsResponse = await page.request.get(requirementsUrl);
  expect(savedRequirementsResponse.status()).toBe(200);
  const savedRequirements = await savedRequirementsResponse.json() as typeof liveRequirements;
  expect(savedRequirements.length).toBe(liveRequirements.length);
  expect(savedRequirements.every((requirement) => requirement.status === 'PENDING')).toBe(true);
  const postgresRequirementsAfterGeneration = readPostgresRequirements(projectId);
  expect(postgresRequirementsAfterGeneration).toHaveLength(liveRequirements.length);
  expect(postgresRequirementsAfterGeneration.every((requirement) =>
    requirement.status === 'PENDING' && requirement.source === generated.provider,
  )).toBe(true);
  console.log(`Read ${postgresRequirementsAfterGeneration.length} generated requirement row(s) directly from ai_sdlc_test.`);

  const editedRequirement = liveRequirements[0];
  const editedTitle = `Edited ${editedRequirement.code} ${identifier}`;
  const editedCard = page.locator('.requirement-card').filter({ hasText: editedRequirement.code });
  await editedCard.getByRole('button', { name: 'Edit' }).click();
  const titleInput = page.getByLabel('Title');
  await expect(titleInput).toBeVisible();
  await titleInput.fill(editedTitle);
  const criteriaInput = page.getByLabel(/Acceptance criteria/);
  await expect(criteriaInput).toBeVisible();
  await criteriaInput.fill('The edited requirement remains available after restart.');
  const updateRequirementPromise = page.waitForResponse((response) =>
    response.url().endsWith(`/requirements/${encodeURIComponent(editedRequirement.id)}`)
      && response.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: 'Save changes' }).click();
  const updateRequirementResponse = await updateRequirementPromise;
  expect(updateRequirementResponse.status()).toBe(200);
  expect(await updateRequirementResponse.json()).toMatchObject({
    id: editedRequirement.id,
    title: editedTitle,
    status: 'PENDING',
  });
  expect(readPostgresRequirements(projectId).find((requirement) => requirement.id === editedRequirement.id))
    .toMatchObject({ status: 'PENDING', source: generated.provider });

  const approvePromise = page.waitForResponse((response) =>
    response.url().endsWith(`/requirements/${encodeURIComponent(editedRequirement.id)}/approve`)
      && response.request().method() === 'POST',
  );
  await editedCard.getByRole('button', { name: 'Approve' }).click();
  expect((await approvePromise).status()).toBe(200);
  await expect(editedCard.locator('.requirement-status')).toHaveText('APPROVED');
  expect(readPostgresRequirements(projectId).find((requirement) => requirement.id === editedRequirement.id)?.status)
    .toBe('APPROVED');

  const rejectedRequirement = liveRequirements.find((requirement) => requirement.id !== editedRequirement.id)!;
  const rejectedCard = page.locator('.requirement-card').filter({ hasText: rejectedRequirement.code });
  const rejectPromise = page.waitForResponse((response) =>
    response.url().endsWith(`/requirements/${encodeURIComponent(rejectedRequirement.id)}/reject`)
      && response.request().method() === 'POST',
  );
  await rejectedCard.getByRole('button', { name: 'Reject' }).click();
  expect((await rejectPromise).status()).toBe(200);
  await expect(rejectedCard.locator('.requirement-status')).toHaveText('REJECTED');
  expect(readPostgresRequirements(projectId).find((requirement) => requirement.id === rejectedRequirement.id)?.status)
    .toBe('REJECTED');

  const otherPage = await browser.newPage();
  const otherIdentifier = crypto.randomUUID().replaceAll('-', '');
  const otherEmail = `e2e-${otherIdentifier}@example.test`;
  await otherPage.goto('/');
  await otherPage.getByRole('tab', { name: 'Create account' }).click();
  await otherPage.locator('input[autocomplete="username"]').fill(`e2e-${otherIdentifier}`);
  await otherPage.locator('input[autocomplete="given-name"]').fill('Other');
  await otherPage.locator('input[autocomplete="family-name"]').fill('Owner');
  await otherPage.getByLabel('Email address').fill(otherEmail);
  await otherPage.locator('input[autocomplete="new-password"]').fill(`Other-${crypto.randomUUID()}-Db!`);
  const otherRegistrationPromise = otherPage.waitForResponse((response) =>
    response.url().endsWith('/api/auth/register')
      && response.request().method() === 'POST',
  );
  await otherPage.getByRole('button', { name: 'Create account', exact: true }).last().click();
  expect((await otherRegistrationPromise).status()).toBe(201);
  expect((await otherPage.request.get(requirementsUrl)).status()).toBe(404);
  expect((await otherPage.request.get(
    `${requirementsUrl}/${encodeURIComponent(editedRequirement.id)}`,
  )).status()).toBe(404);
  const otherCsrfResponse = await otherPage.request.get('/api/auth/csrf');
  expect(otherCsrfResponse.status()).toBe(200);
  const otherCsrfToken = (await otherCsrfResponse.json()).token as string;
  const foreignApprove = await otherPage.request.post(
    `${requirementsUrl}/${encodeURIComponent(editedRequirement.id)}/approve`,
    { headers: { 'X-XSRF-TOKEN': otherCsrfToken } },
  );
  expect(foreignApprove.status()).toBe(404);
  await otherPage.close();

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible();
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: /Projects/ }).click();
  await page.getByRole('button', { name: `Open requirements for ${projectName}` }).click();
  await expect(page.locator('.requirement-card')).toHaveCount(liveRequirements.length);
  await expect(page.locator('.requirement-card').filter({ hasText: editedRequirement.code }).locator('.requirement-status'))
    .toHaveText('APPROVED');
  await expect(page.locator('.requirement-card').filter({ hasText: rejectedRequirement.code }).locator('.requirement-status'))
    .toHaveText('REJECTED');

  const csrfResponse = await page.request.get('/api/auth/csrf');
  expect(csrfResponse.status()).toBe(200);
  const csrfToken = (await csrfResponse.json()).token as string;
  const temporaryProjectPayload = {
    name: `Delete verification ${identifier}`,
    description: 'Temporary project deleted through the real backend API.',
    objectives: '',
    domain: 'Quality Engineering',
    preferredStack: 'Spring Boot',
    status: 'DRAFT',
  };
  const temporaryProjectResponse = await page.request.post('/api/projects', {
    data: temporaryProjectPayload,
    headers: { 'X-XSRF-TOKEN': csrfToken },
  });
  expect(temporaryProjectResponse.status()).toBe(201);
  const temporaryProject = await temporaryProjectResponse.json() as { id: string };
  const deleteResponse = await page.request.delete(`/api/projects/${encodeURIComponent(temporaryProject.id)}`, {
    headers: { 'X-XSRF-TOKEN': csrfToken },
  });
  expect(deleteResponse.status()).toBe(204);
  expect((await page.request.get(`/api/projects/${encodeURIComponent(temporaryProject.id)}`)).status()).toBe(404);

  const logoutResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/auth/logout')
      && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Sign out' }).first().click();
  expect((await logoutResponsePromise).status()).toBe(204);
  await expect(page.getByRole('heading', { name: 'Sign in to your workspace' })).toBeVisible();
  expect((await page.request.get('/api/auth/me')).status()).toBe(401);
  expect((await page.request.get('/api/projects')).status()).toBe(401);

  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  const loginResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/auth/login')
      && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Sign in securely' }).click();
  const loginResponse = await loginResponsePromise;
  expect(loginResponse.status()).toBe(200);
  expect(await loginResponse.json()).toMatchObject({ email });
  await expect(page.getByRole('heading', { name: 'Good to see you.' })).toBeVisible();
  expect((await page.request.get(projectUrl)).status()).toBe(200);

  const restartReadyFile = process.env.LIVE_RESTART_READY_FILE;
  const restartEvidence = {
    database: 'ai_sdlc_test',
    projectId,
    provider: generated.provider,
    demo: generated.demo,
    requirementIds: liveRequirements.map((requirement) => requirement.id),
    editedApprovedRequirementId: editedRequirement.id,
    rejectedRequirementId: rejectedRequirement.id,
  };
  if (restartReadyFile) writeFileSync(restartReadyFile, JSON.stringify(restartEvidence), { encoding: 'utf8' });
  console.log(`Live test data is ready in ai_sdlc_test: project=${projectId}; provider=${generated.provider}; requirementIds=${liveRequirements.map((requirement) => requirement.id).join(',')}; editedApproved=${editedRequirement.id}; rejected=${rejectedRequirement.id}.`);
  console.log('Restart the Spring Boot backend against ai_sdlc_test now; the test will wait up to five minutes for it to return.');

  let backendUnavailableAt: Date | undefined;
  await expect.poll(async () => {
    try {
      const response = await page.request.get('/api/auth/csrf', { timeout: 1_000 });
      if (response.status() === 200) return backendUnavailableAt ? 'restarted' : 'waiting';
    } catch {
      backendUnavailableAt ??= new Date();
      return 'backend unavailable';
    }

    backendUnavailableAt ??= new Date();
    return 'backend unavailable';
  }, { timeout: 300_000, intervals: [1_000] }).toBe('restarted');

  const postRestartJdbcSessions = assertTestDatabaseConnection(backendUnavailableAt);
  console.log(`Verified restarted backend has ${postRestartJdbcSessions} active ai_sdlc_test JDBC session(s) before making authenticated requests.`);

  expect((await page.request.get('/api/auth/me')).status()).toBe(401);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Sign in to your workspace' })).toBeVisible();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  const postRestartLoginPromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/auth/login')
      && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Sign in securely' }).click();
  expect((await postRestartLoginPromise).status()).toBe(200);

  const persistedProjectResponse = await page.request.get(projectUrl);
  expect(persistedProjectResponse.status()).toBe(200);
  expect(await persistedProjectResponse.json()).toMatchObject({
    id: project.id,
    name: projectName,
    description: updatedDescription,
  });

  const persistedRequirementsResponse = await page.request.get(requirementsUrl);
  expect(persistedRequirementsResponse.status()).toBe(200);
  const persistedRequirements = await persistedRequirementsResponse.json() as typeof liveRequirements;
  expect(persistedRequirements).toHaveLength(liveRequirements.length);
  expect(persistedRequirements.find((requirement) => requirement.id === editedRequirement.id)).toMatchObject({
    title: editedTitle,
    status: 'APPROVED',
    acceptanceCriteria: ['The edited requirement remains available after restart.'],
  });
  expect(persistedRequirements.find((requirement) => requirement.id === rejectedRequirement.id)).toMatchObject({
    status: 'REJECTED',
  });
  const postgresRequirementsAfterRestart = readPostgresRequirements(projectId);
  expect(postgresRequirementsAfterRestart).toHaveLength(liveRequirements.length);
  expect(postgresRequirementsAfterRestart.find((requirement) => requirement.id === editedRequirement.id))
    .toMatchObject({ status: 'APPROVED', source: generated.provider });
  expect(postgresRequirementsAfterRestart.find((requirement) => requirement.id === rejectedRequirement.id)?.status)
    .toBe('REJECTED');

  const postRestartCsrfResponse = await page.request.get('/api/auth/csrf');
  expect(postRestartCsrfResponse.status()).toBe(200);
  const postRestartCsrfToken = (await postRestartCsrfResponse.json()).token as string;
  const deleteRequirementResponse = await page.request.delete(
    `${requirementsUrl}/${encodeURIComponent(editedRequirement.id)}`,
    { headers: { 'X-XSRF-TOKEN': postRestartCsrfToken } },
  );
  expect(deleteRequirementResponse.status()).toBe(204);
  expect((await page.request.get(
    `${requirementsUrl}/${encodeURIComponent(editedRequirement.id)}`,
  )).status()).toBe(404);
  expect((await page.request.get(requirementsUrl).then((response) => response.json()) as typeof liveRequirements)
    .some((requirement) => requirement.id === editedRequirement.id)).toBe(false);
  expect(readPostgresRequirements(projectId).some((requirement) => requirement.id === editedRequirement.id)).toBe(false);
  console.log(`Verified post-restart requirement state in ai_sdlc_test; deleted requirement=${editedRequirement.id}; rejected requirement=${rejectedRequirement.id}.`);
  if (restartReadyFile) rmSync(restartReadyFile, { force: true });
});

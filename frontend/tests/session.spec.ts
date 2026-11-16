import { expect, test, type Page } from '@playwright/test'

const username = process.env.QVA_TEST_USERNAME ?? 'jyoshna'
const password = process.env.QVA_TEST_PASSWORD
const screenshots = process.env.QVA_SCREENSHOT_DIR

test.beforeAll(() => {
  if (!password) throw new Error('Set QVA_TEST_PASSWORD for the disposable QVA backend.')
})

async function signIn(page: Page) {
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password!)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

test('restores the session across direct routes and supports keyboard navigation', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 900 })
  await page.goto('/app/')
  await expect(page.getByRole('heading', { name: 'Sign in to QVA' })).toBeVisible()
  if (screenshots) await page.screenshot({ path: screenshots + '/QVA-2.1-login.png' })

  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill('Invalid-test-credential!')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toHaveText('Invalid username or password.')
  await expect(page.getByLabel('Password')).toBeEmpty()

  await signIn(page)
  await expect(page.getByRole('heading', { name: 'Operations overview' })).toBeVisible()
  await expect(page.getByText('Operational', { exact: true })).toBeVisible()
  await expect(page.getByText('@' + username)).toBeVisible()
  const skip = page.getByRole('link', { name: 'Skip to main content' })
  await page.getByRole('link', { name: 'Overview', exact: true }).focus()
  await page.keyboard.press('Shift+Tab')
  await expect(skip).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('main')).toBeFocused()

  await page.getByRole('link', { name: 'Network', exact: true }).focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/app\/network$/)
  await expect(page.getByRole('main')).toBeFocused()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Network status' })).toBeVisible()
  await expect(page.getByText('Service reachable', { exact: true })).toBeVisible()
  await expect(page.getByText(/No approved devices are visible/)).toBeVisible()
  if (screenshots) await page.screenshot({ path: screenshots + '/QVA-2.1-network.png', fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('link', { name: 'Members', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Workspace members' })).toBeVisible()
  await expect(page.getByText('@' + username)).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390)
  if (screenshots) await page.screenshot({ path: screenshots + '/QVA-2.1-members-mobile.png', fullPage: true })
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in to QVA' })).toBeVisible()
  expect((await page.request.get('/api/auth/me')).status()).toBe(401)
})

test('handles real session replacement and returns to the requested page after sign-in', async ({ page, browser, baseURL }) => {
  await page.goto('/app/members')
  await signIn(page)
  await expect(page.getByText('@' + username)).toBeVisible()

  const competingSession = await browser.newContext({ baseURL })
  try {
    const result = await competingSession.request.post('/api/auth/login', { form: { username, password: password! } })
    expect(result.ok()).toBeTruthy()
    await page.getByRole('button', { name: 'Refresh snapshot' }).click()
    await expect(page.getByText(/Your session expired/)).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Sign in to QVA' })).toBeFocused()
    await expect(page.getByText('@' + username)).toHaveCount(0)
    await signIn(page)
    await expect(page.getByRole('heading', { name: 'Workspace members' })).toBeVisible()
    await expect(page).toHaveURL(/\/app\/members$/)
  } finally {
    await competingSession.close()
  }
})

test('recovers from session and roster API failures without signing the user out', async ({ page }) => {
  await page.route('**/api/auth/me', route => route.fulfill({ status: 503 }), { times: 1 })
  await page.goto('/app/members')
  await expect(page.getByRole('alert')).toHaveText(/Unable to check your QVA session/)
  await page.getByRole('button', { name: 'Try again' }).click()

  await page.route('**/api/users', route => route.fulfill({ status: 503 }), { times: 1 })
  await signIn(page)
  await expect(page.getByRole('alert')).toHaveText(/Member presence is unavailable/)
  await page.getByRole('button', { name: 'Try again' }).click()
  await expect(page.getByText('@' + username)).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('packaged JAR keeps both interfaces and protects the existing member API', async ({ request }) => {
  test.skip(!process.env.QVA_TEST_BASE_URL, 'Verify JAR packaging with QVA_TEST_BASE_URL.')
  const classic = await request.get('/')
  expect(classic.ok()).toBe(true)
  expect(await classic.text()).toContain('Private Chat')

  for (const route of ['/app', '/app/', '/app/network', '/app/members', '/app/host']) {
    const response = await request.get(route)
    expect(response.ok()).toBe(true)
    const asset = (await response.text()).match(/src="(\/app\/assets\/[^"]+\.js)"/)?.[1]
    expect(asset).toBeTruthy()
    expect((await request.get(asset!)).ok()).toBe(true)
  }
  expect((await request.get('/app/assets/missing.js')).status()).toBe(404)
  expect((await request.get('/api/users')).status()).toBe(401)
})

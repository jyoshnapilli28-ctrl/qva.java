import { execFileSync } from 'node:child_process'
import { expect, test, type BrowserContext, type Page } from '@playwright/test'

const password = process.env.QVA_TEST_PASSWORD
const screenshots = process.env.QVA_SCREENSHOT_DIR
const required = process.env.QVA_TEST_OPERATIONS === '1'

test.describe.configure({ mode: 'serial' })
test.skip(!required, 'Requires the disposable HOST/MEMBER fixture.')

let host: Page
let member: Page
let hostContext: BrowserContext
let memberContext: BrowserContext

async function signIn(page: Page, username: string) {
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password!)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Operations overview' })).toBeVisible()
}

async function register(page: Page, name: string) {
  await page.getByLabel('Device name').fill(name)
  await page.getByRole('button', { name: 'Register this browser' }).click()
  await expect(page.getByRole('heading', { name: 'Your account and device' })).toBeVisible()
  await expect(page.getByText(name, { exact: true })).toBeVisible()
}

function deviceState(page: Page, name: string) {
  return page.getByRole('list', { name: 'Client connections' }).getByRole('listitem').filter({ hasText: name })
}

test.beforeAll(async ({ browser, baseURL }) => {
  if (!password) throw new Error('Missing disposable fixture password')
  hostContext = await browser.newContext({ baseURL, viewport: { width: 1366, height: 900 } })
  memberContext = await browser.newContext({ baseURL, viewport: { width: 1366, height: 900 } })
  host = await hostContext.newPage()
  member = await memberContext.newPage()
})

test.afterAll(async () => {
  await hostContext?.close()
  await memberContext?.close()
})

test('Milestone 3: role checks, first host enrollment, member approval and dashboard access', async () => {
  expect((await host.request.get('/api/v2/identity')).status()).toBe(401)
  await host.goto('/app/')
  await signIn(host, 'operator')
  expect((await host.request.get('/api/v2/host/dashboard')).status()).toBe(403)
  await register(host, 'Host console')
  await host.getByRole('button', { name: 'Approve first host device' }).click()
  await host.getByRole('link', { name: 'Host dashboard' }).click()
  await expect(host.getByRole('heading', { name: 'Host dashboard' })).toBeVisible()
  expect((await host.request.get('/api/v2/host/dashboard')).status()).toBe(200)

  await member.goto('/app/')
  await signIn(member, 'atlas')
  expect((await member.request.post('/api/v2/devices', { data: { name: 'No CSRF' } })).status()).toBe(403)
  await register(member, 'Atlas laptop')
  expect((await member.request.get('/api/v2/host/dashboard')).status()).toBe(403)
  const { token, headerName } = await (await member.request.get('/api/v2/csrf')).json()
  expect((await member.request.post('/api/v2/devices/bootstrap-host', { headers: { [headerName]: token } })).status()).toBe(403)
  expect(await member.evaluate(() => new Promise<boolean>(resolve => {
    const socket = new WebSocket(new URL('/api/v2/live', location.origin.replace(/^http/, 'ws')))
    socket.onopen = () => { socket.close(); resolve(true) }
    socket.onclose = () => resolve(false)
  }))).toBe(false)
  await member.goto('/app/host')
  await expect(member.getByRole('heading', { name: 'Host access required' })).toBeVisible()
  await expect(member.getByRole('link', { name: 'Host dashboard' })).toHaveCount(0)

  await host.getByRole('button', { name: 'Refresh snapshot' }).click()
  await host.locator('summary').filter({ hasText: 'Atlas laptop' }).click()
  await host.getByRole('button', { name: 'Approve Atlas laptop' }).click()
  await member.goto('/app/')
  await expect(member.getByText('APPROVED', { exact: true })).toBeVisible({ timeout: 18_000 })
  const topology = await (await member.request.get('/api/v2/network')).json()
  expect(topology.devices.map((entry: { device: { name: string } }) => entry.device.name)).toEqual(['Atlas laptop'])
  if (screenshots) {
    await expect(host.getByText('device approved').first()).toBeVisible()
    await host.screenshot({ path: screenshots + '/QVA-host.png', fullPage: true })
    await member.screenshot({ path: screenshots + '/QVA-member.png', fullPage: true })
  }
})

test('Milestone 4: live topology, duplicate tabs, disconnect and reconnect', async () => {
  await host.getByRole('link', { name: 'Network', exact: true }).click()
  await expect(deviceState(host, 'Atlas laptop')).toContainText('Connected')
  await expect(host.getByText('Unknown', { exact: true })).toBeVisible()
  if (screenshots) await host.screenshot({ path: screenshots + '/QVA-topology.png', fullPage: true })

  const duplicate = await memberContext.newPage()
  const ready = duplicate.waitForEvent('websocket', { predicate: socket => socket.url().endsWith('/api/v2/live') })
  await duplicate.goto('/app/')
  await ready
  await expect(duplicate.getByText('Connected', { exact: true })).toBeVisible()
  await duplicate.close()
  await expect(deviceState(host, 'Atlas laptop')).toContainText('Connected')

  await member.goto('/api/health')
  await expect(deviceState(host, 'Atlas laptop')).toContainText('Disconnected')
  await member.goto('/app/')
  await expect(deviceState(host, 'Atlas laptop')).toContainText('Connected')
  await host.setViewportSize({ width: 390, height: 844 })
  expect(await host.evaluate(() => document.documentElement.scrollWidth)).toBe(390)
  if (screenshots) await host.screenshot({ path: screenshots + '/QVA-mobile.png', fullPage: true })
  await host.setViewportSize({ width: 1366, height: 900 })
})

test('Milestone 4: heartbeat updates last seen and an idle connection becomes stale', async () => {
  test.setTimeout(65_000)
  await expect(deviceState(host, 'Atlas laptop')).toContainText('Connected')
  const lastSeen = async () => {
    const result = await (await host.request.get('/api/v2/network')).json()
    return (result.devices.find((entry: { device: { name: string } }) => entry.device.name === 'Atlas laptop')
      ?.device.lastSeen ?? null) as string | null
  }
  const before = await lastSeen()
  await expect.poll(lastSeen, { timeout: 14_000 }).not.toBe(before)

  await member.goto('/api/health')
  await member.evaluate(() => new Promise<void>((resolve, reject) => {
    const socket = new WebSocket(new URL('/api/v2/live', location.origin.replace(/^http/, 'ws')))
    socket.onopen = () => resolve()
    socket.onerror = () => reject(new Error('The approved device could not reconnect'))
  }))
  await expect(deviceState(host, 'Atlas laptop')).toContainText('Connected')
  await expect(deviceState(host, 'Atlas laptop')).toContainText('Disconnected', { timeout: 43_000 })
  await host.getByRole('link', { name: 'Host dashboard' }).click()
  await expect(host.getByText('connection stale').first()).toBeVisible()
})

test('Milestones 3 and 4: termination, device revocation and grant revocation close protected access', async () => {
  await member.goto('/app/')
  await expect(member.getByText('Connected', { exact: true })).toBeVisible()
  await host.getByRole('button', { name: 'Terminate session for atlas' }).click()
  await host.getByRole('button', { name: 'Confirm', exact: true }).click()
  await expect(member.getByText(/Your session expired/)).toBeVisible({ timeout: 18_000 })
  await signIn(member, 'atlas')
  await expect(member.getByText('Connected', { exact: true })).toBeVisible()

  const atlas = host.locator('summary').filter({ hasText: 'Atlas laptop' })
  await atlas.click()
  await host.getByRole('button', { name: 'Revoke Atlas laptop' }).click()
  await host.getByRole('button', { name: 'Confirm', exact: true }).click()
  await expect(member.getByText(/Your session expired/)).toBeVisible({ timeout: 18_000 })
  await signIn(member, 'atlas')
  await expect(member.getByText(/registration was revoked/)).toBeVisible()
  expect((await member.request.get('/api/v2/network')).status()).toBe(403)

  const fixture = process.env.QVA_TEST_DB_PATH
  if (fixture?.startsWith('/tmp/qva-v4-fixture.')) {
    execFileSync('python', ['../tools/qva-host.py', '--database', fixture, 'revoke', '1'])
    const response = await host.request.get('/api/v2/host/dashboard')
    expect(response.status() === 401 || response.status() === 403 || !(await response.text()).includes('"devices"')).toBe(true)
    await expect(host.getByText(/Your session expired/)).toBeVisible({ timeout: 18_000 })
  }
})

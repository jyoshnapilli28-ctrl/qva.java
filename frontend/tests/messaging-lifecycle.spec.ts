import { writeFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { expect, test, type Page } from '@playwright/test'

const password = process.env.QVA_M52_PASSWORD
const control = process.env.QVA_M52_CONTROL
const enabled = process.env.QVA_M52_FIXTURE === '1'
const screenshot = process.env.QVA_SCREENSHOT_DIR

test.skip(!enabled, 'Requires the isolated M5.2 browser fixture and restart runner.')

async function login(page: Page, username: string) {
  await page.goto('/app/messages')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password!)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Direct conversations' })).toBeVisible()
}

async function mutate(page: Page, method: 'post' | 'delete', path: string, data?: object) {
  const { headerName, token } = await (await page.request.get('/api/v2/csrf')).json()
  return page.request[method](path, { data, headers: { [headerName]: token } })
}

const send = (page: Page) => page.getByRole('button', { name: 'Send synthetic envelope' }).click()

async function classic(page: Page, username: string) {
  await page.goto('/')
  const login = page.locator('#login-btn')
  if (await login.isVisible() && !(await page.request.get('/api/auth/me')).ok()) {
    await page.locator('#username').fill(username)
    await page.locator('#password').fill(password!)
    await login.click()
  }
  await expect(page.locator('#connection-status')).toHaveText('Connected', { timeout: 15000 })
}

test('two approved browsers exchange, recover and acknowledge direct and group envelopes', async ({ browser, baseURL }) => {
  test.setTimeout(120000)
  if (!control || !password) throw new Error('Missing isolated fixture controls')
  const host = await browser.newContext({ baseURL, viewport: { width: 1280, height: 960 } })
  const member = await browser.newContext({ baseURL, viewport: { width: 1280, height: 960 } })
  try {
    for (const [context, value] of [[host, 'a'], [member, 'b']] as const) {
      const origin = new URL(baseURL!)
      await context.addCookies([{ name: 'QVA_DEVICE', value: value.repeat(43),
        domain: origin.hostname, path: '/api/v2', httpOnly: true }])
    }
    const a = await host.newPage()
    const b = await member.newPage()
    await Promise.all([login(a, 'test_a'), login(b, 'test_b')])
    await a.getByLabel('Participant').selectOption('920002')
    await a.getByRole('button', { name: 'Open conversation' }).click()
    await send(a)
    await b.reload()
    await b.getByRole('button', { name: 'Test Member A', exact: true }).click()
    await expect(b.getByText(/Fixture A.*Participant/)).toBeVisible()
    await b.getByRole('button', { name: 'Mark read' }).click()
    await b.getByLabel('Test fixture').selectOption('1')
    await send(b)
    await expect(a.getByText(/Fixture B.*Participant/)).toBeVisible()
    await a.getByRole('button', { name: 'View receipts' }).last().click()
    await expect(a.getByText(/Account 920002: Read/)).toBeVisible()
    if (screenshot) await a.screenshot({ path: join(screenshot, 'QVA-M5.2-direct.png'), fullPage: true })

    await a.getByLabel('Group name').fill('Fixture group')
    await a.getByRole('button', { name: 'Create group' }).click()
    await a.getByLabel('Add group participant').selectOption('920002')
    await a.getByRole('button', { name: 'Add participant', exact: true }).click()
    await expect(a.getByRole('button', { name: 'Remove Test Member B' })).toBeVisible()
    const group = (await (await a.request.get('/api/v2/conversations/groups')).json())[0]
    expect((await mutate(b, 'post', `/api/v2/conversations/groups/${group.id}/participants`, { userId: 1 })).status()).toBe(404)
    await b.goto('/api/health')
    await send(a)
    await expect(a.getByText(/Fixture A.*You/)).toBeVisible()
    await b.goto('/app/messages')
    await b.getByRole('button', { name: 'Group: Fixture group' }).click()
    await expect(b.getByText(/Fixture A.*Participant/)).toBeVisible()
    await b.getByRole('button', { name: 'Mark read' }).click()
    await expect(b.getByRole('button', { name: 'Read recorded' })).toBeVisible()
    if (screenshot) await b.screenshot({ path: join(screenshot, 'QVA-M5.2-group.png'), fullPage: true })

    writeFileSync(join(control, 'restart.request'), 'restart')
    await expect.poll(() => existsSync(join(control, 'restart.ready')), { timeout: 40000 }).toBe(true)
    await Promise.all([login(a, 'test_a'), login(b, 'test_b')])
    await a.getByRole('button', { name: 'Group: Fixture group' }).click()
    await expect(a.getByText(/Fixture A.*You/)).toBeVisible()
    await a.getByRole('button', { name: 'View receipts' }).click()
    await expect(a.getByText(/Account 920002: Read/)).toBeVisible()

    await classic(a, 'test_a')
    await classic(b, 'test_b')
    const text = 'Disposable legacy fixture'
    await a.locator('#message-input').fill(text)
    await a.locator('#send-btn').click()
    await expect(b.locator('#messages-list').getByText(text, { exact: true })).toBeVisible()

    await b.goto('/app/messages')
    await b.getByRole('button', { name: 'Group: Fixture group' }).click()
    await expect(b.getByText(/Fixture A.*Participant/)).toBeVisible()
    const identity = await (await b.request.get('/api/v2/identity')).json()
    expect((await mutate(a, 'post', `/api/v2/host/devices/${identity.device.id}/revoke`)).status()).toBe(200)
    await expect(b.getByText(/Your session expired|Conversation access is unavailable/)).toBeVisible({ timeout: 18000 })
    await expect.poll(async () => (await b.request.get(`/api/v2/conversations/${group.id}/envelopes`)).status()).toBe(401)
  } finally {
    await host.close()
    await member.close()
  }
})

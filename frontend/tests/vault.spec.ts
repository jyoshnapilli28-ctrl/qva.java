import { chromium, expect, test, type BrowserContext, type Page } from '@playwright/test'
import { randomBytes } from 'node:crypto'
import { access, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const baseURL = process.env.QVA_TEST_BASE_URL
const control = process.env.QVA_VAULT_CONTROL
const dataPath = process.env.QVA_TEST_DB_PATH
const password = process.env.QVA_TEST_PASSWORD
const device = process.env.QVA_VAULT_COOKIE
const username = process.env.QVA_TEST_USERNAME

test.skip(!baseURL || !control || !dataPath || !password || !device || !username, 'Needs the disposable packaged vault fixture.')

async function openVault(page: Page) {
  await page.goto('/app/vault')
  const login = page.getByRole('heading', { name: 'Sign in to QVA' })
  const vault = page.getByRole('heading', { name: 'Local encryption vault' })
  await expect(login.or(vault)).toBeVisible()
  if (await login.isVisible()) {
    await page.getByLabel('Username').fill(username!)
    await page.getByLabel('Password', { exact: true }).fill(password!)
    await page.getByRole('button', { name: 'Sign in' }).click()
  }
  await expect(vault).toBeVisible()
}

async function enter(page: Page, value: string, button = 'Unlock vault') {
  await page.getByLabel('Encryption password', { exact: true }).fill(value)
  await page.getByRole('button', { name: button }).click()
}

async function verifyLocalStorage(page: Page, secret: string) {
  return page.evaluate(async phrase => {
    const db = await new Promise<IDBDatabase>((resolve, reject) => {
      const opening = indexedDB.open('qva-local-vault', 1)
      opening.onsuccess = () => resolve(opening.result)
      opening.onerror = () => reject(opening.error)
    })
    const records = await new Promise<Array<{
      version: number; deviceId: string; kdf: string; iterations: number; salt: Uint8Array;
      cipher: string; nonce: Uint8Array; ciphertext: Uint8Array; key: string
    }>>((resolve, reject) => {
      const request = db.transaction('devices').objectStore('devices').getAll()
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
    db.close()
    if (records.length !== 1) throw new Error('Expected only one encrypted fixture record')
    const h = records[0]
    const encoder = new TextEncoder()
    const material = await crypto.subtle.importKey('raw', encoder.encode(phrase), 'PBKDF2', false, ['deriveBits', 'deriveKey'])
    const params = { name: 'PBKDF2', hash: 'SHA-256', salt: h.salt, iterations: h.iterations }
    const key = await crypto.subtle.deriveKey(params, material, { name: 'AES-GCM', length: 256 }, false, ['decrypt'])
    const bits = new Uint8Array(await crypto.subtle.deriveBits(params, material, 256))
    const aad = encoder.encode(JSON.stringify([
      'QVA local device vault', h.version, h.deviceId, h.kdf, h.iterations,
      Array.from(h.salt), h.cipher, Array.from(h.nonce), h.key,
    ]))
    const payload = new Uint8Array(await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: h.nonce, additionalData: aad, tagLength: 128 }, key, h.ciphertext,
    ))
    return {
      fields: Object.keys(h).sort(),
      privateKey: Array.from(payload.subarray(2, 2 + payload[1])),
      derivedKey: Array.from(bits),
      privateScalar: (await crypto.subtle.exportKey('jwk', await crypto.subtle.importKey(
        'pkcs8', payload.subarray(2, 2 + payload[1]), { name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign'],
      ))).d!,
      encryptedStorage: JSON.stringify(records),
      otherStorage: JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]),
      nonSecretHeader: { version: h.version, kdf: h.kdf, iterations: h.iterations, cipher: h.cipher, key: h.key },
    }
  }, secret)
}

test('packaged vault locks, rejects bad secrets, coordinates tabs and survives browser and server restart', async ({ browser }) => {
  test.setTimeout(120_000)
  const directory = await mkdtemp(join(tmpdir(), 'qva-vault-profile-'))
  const launch = () => chromium.launchPersistentContext(directory, {
    baseURL, executablePath: process.env.QVA_CHROMIUM_PATH, headless: true,

  })
  const phrase = randomBytes(24).toString('hex')
  const tokens: Buffer[] = [Buffer.from(phrase)]
  const traffic: Buffer[] = []
  const consoleOutput: string[] = []
  let context: BrowserContext | undefined
  let competitor: BrowserContext | undefined

  function observe(current: BrowserContext) {
    current.on('request', request => {
      const body = request.postDataBuffer()
      traffic.push(Buffer.from(request.url()), Buffer.from(JSON.stringify(request.headers())))
      if (body) traffic.push(body)
    })
    current.on('console', message => consoleOutput.push(message.text()))
  }

  try {
    context = await launch()
    observe(context)
    await context.addCookies([{
      url: baseURL, name: 'QVA_DEVICE', value: device!, expires: Math.floor(Date.now() / 1000) + 7200,
    }])
    const page = context.pages()[0]
    await openVault(page)
    await expect(page.getByText('Vault not initialized.')).toBeVisible()
    await page.getByLabel('Confirm encryption password').fill('unmatched passphrase')
    await enter(page, phrase, 'Create vault')
    await expect(page.getByRole('alert')).toHaveText(/matching encryption passphrase/)
    await page.getByLabel('Confirm encryption password').fill(phrase)
    await enter(page, phrase, 'Create vault')
    await expect(page.getByText('Vault unlocked on this tab.')).toBeVisible()
    const worker = page.workers().find(item => item.url().includes('vault.worker'))
    expect(worker).toBeDefined()
    await expect(page.getByLabel('Encryption password', { exact: true })).toHaveCount(0)

    const second = await context.newPage()
    await openVault(second)
    await expect(second.getByText('Vault locked.')).toBeVisible()
    await enter(second, phrase)
    await expect(second.getByRole('alert')).toHaveText(/active in another tab/)
    const terminated = worker!.waitForEvent('close')
    await page.getByRole('button', { name: 'Lock vault' }).click()
    await terminated
    await enter(second, 'synthetic wrong passphrase')
    await expect(second.getByRole('alert')).toHaveText(/Unable to unlock/)
    await enter(second, phrase)
    await expect(second.getByText('Vault unlocked on this tab.')).toBeVisible()

    const stored = await verifyLocalStorage(second, phrase)
    expect(stored.fields).toEqual(['cipher', 'ciphertext', 'deviceId', 'iterations', 'kdf', 'key', 'nonce', 'salt', 'version'])
    expect(stored.nonSecretHeader).toEqual({ version: 1, iterations: 600_000, kdf: 'PBKDF2-SHA-256', cipher: 'AES-256-GCM', key: 'ECDSA-P256' })
    expect(stored.privateKey.length).toBeGreaterThan(32)
    const pkcs8 = Buffer.from(stored.privateKey)
    const sensitive = [pkcs8, Buffer.from(stored.privateScalar, 'base64url'), Buffer.from(stored.derivedKey)]
    for (const token of sensitive) {
      tokens.push(token, Buffer.from(token.toString('base64')), Buffer.from(token.toString('base64url')), Buffer.from(token.toString('hex')),
        Buffer.from(JSON.stringify([...token])))
    }
    traffic.push(Buffer.from(stored.encryptedStorage), Buffer.from(stored.otherStorage))
    if (process.env.QVA_SCREENSHOT_DIR) await second.screenshot({
      path: join(process.env.QVA_SCREENSHOT_DIR, 'QVA-M6.3-vault-unlocked.png'), fullPage: true,
    })
    await expect(second.getByText('Vault locked.')).toBeVisible({ timeout: 18_000 })
    await enter(second, phrase)
    await expect(second.getByText('Vault unlocked on this tab.')).toBeVisible()
    await second.reload()
    await expect(second.getByText('Vault locked.')).toBeVisible()

    await enter(second, phrase)
    await expect(second.getByText('Vault unlocked on this tab.')).toBeVisible()
    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page.getByRole('heading', { name: 'Sign in to QVA' })).toBeVisible()
    await expect(second.getByText('Vault unlocked on this tab.')).toHaveCount(0)
    await context.close()
    context = undefined

    await writeFile(join(control!, 'restart.request'), '')
    await expect.poll(async () => access(join(control!, 'restart.ready')).then(() => true, () => false), {
      timeout: 35_000,
    }).toBe(true)
    context = await launch()
    observe(context)
    const restarted = context.pages()[0]
    await openVault(restarted)
    await expect(restarted.getByText('Vault locked.')).toBeVisible()
    await enter(restarted, phrase)
    await expect(restarted.getByText('Vault unlocked on this tab.')).toBeVisible()

    competitor = await browser.newContext({ baseURL })
    expect((await competitor.request.post('/api/auth/login', { form: { username: username!, password: password! } })).ok()).toBe(true)
    const active = setInterval(() => { void restarted.keyboard.press('Shift').catch(() => {}) }, 2_000)
    try {
      await expect(restarted.getByText(/Your session expired/)).toBeVisible({ timeout: 25_000 })
    } finally {
      clearInterval(active)
    }
    await expect(restarted.getByText('Vault unlocked on this tab.')).toHaveCount(0)
    await expect.poll(() => restarted.workers().length).toBe(0)

    const server = await Promise.all([
      readFile(dataPath!), readFile(dataPath! + '-wal').catch(() => Buffer.alloc(0)),
      ...['initial.log', 'restarted.log'].map(name => readFile(join(control!, name))),
    ])
    for (const secret of tokens) {
      expect([...traffic, ...server, Buffer.from(consoleOutput.join('\n'))].some(chunk => chunk.includes(secret)))
        .toBe(false)
    }
  } finally {
    await competitor?.close()
    await context?.close()
    await rm(directory, { force: true, recursive: true })
  }
})

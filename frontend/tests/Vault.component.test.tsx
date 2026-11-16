// @vitest-environment node

import { beforeAll, describe, expect, it } from 'vitest'
import { createVault, iterations, unlockVault, validateRecord, type VaultRecord } from '../src/vault/format'

const deviceId = '38fb2ef4-b924-4b1c-a1ac-cfcdb1825fae'
const password = () => new TextEncoder().encode('synthetic format test phrase, never a QVA login')
let record: VaultRecord

beforeAll(async () => {
  record = await createVault(deviceId, password())
})

describe('vault format 1', () => {
  it('unlocks a non-extractable signing key and clears the provided password bytes', async () => {
    const entered = password()
    const identity = await unlockVault(record, deviceId, entered)
    expect(identity).toMatchObject({ type: 'private', extractable: false, usages: ['sign'] })
    expect(entered.every(byte => byte === 0)).toBe(true)
    await expect(crypto.subtle.exportKey('pkcs8', identity)).rejects.toThrow()
  })

  it('rejects a wrong password and permits a correct retry', async () => {
    await expect(unlockVault(record, deviceId, new TextEncoder().encode('incorrect password')))
      .rejects.toMatchObject({ issue: 'credentials' })
    await expect(unlockVault(record, deviceId, password())).resolves.toMatchObject({ type: 'private' })
  })

  it('rejects mutations to ciphertext and authenticated header fields', async () => {
    for (const field of ['ciphertext', 'salt', 'nonce'] as const) {
      const altered = record[field].slice()
      altered[0] ^= 1
      await expect(unlockVault({ ...record, [field]: altered }, deviceId, password()))
        .rejects.toMatchObject({ issue: 'credentials' })
    }
    const otherDevice = 'a4405940-c645-47d2-a39d-30d867698198'
    await expect(unlockVault({ ...record, deviceId: otherDevice }, otherDevice, password()))
      .rejects.toMatchObject({ issue: 'credentials' })
  })

  it('rejects unsupported or oversized records and unsafe KDF parameters before derivation', () => {
    for (const bad of [
      null, [], { ...record, version: 2 }, { ...record, iterations: iterations * 200 },
      { ...record, iterations: 1 }, { ...record, kdf: 'Argon2id' }, { ...record, cipher: 'AES-CBC' },
      { ...record, ciphertext: new Uint8Array(1041) }, { ...record, salt: [] }, { ...record, extra: 0 },
    ]) {
      expect(() => validateRecord(bad, deviceId)).toThrow('malformed')
    }
  })

  it('uses independent random salts and nonces even for the same passphrase and device', async () => {
    const other = await createVault(deviceId, password())
    expect(other.salt).not.toEqual(record.salt)
    expect(other.nonce).not.toEqual(record.nonce)
    expect(other.ciphertext).not.toEqual(record.ciphertext)
  })
})

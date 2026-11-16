// All algorithm parameters are fixed for format 1. Select future formats explicitly.
export const iterations = 600_000
export const saltLength = 16
export const nonceLength = 12
export const minimumPassword = 15
export const maximumPassword = 256

type Header = {
  version: 1
  deviceId: string
  kdf: 'PBKDF2-SHA-256'
  iterations: typeof iterations
  salt: Uint8Array
  cipher: 'AES-256-GCM'
  nonce: Uint8Array
  key: 'ECDSA-P256'
}

export type VaultRecord = Header & { ciphertext: Uint8Array }
export type VaultIssue = 'malformed' | 'credentials' | 'unavailable' | 'exists' | 'missing'

export class VaultError extends Error {
  constructor(readonly issue: VaultIssue) {
    super(issue)
  }
}

const encoder = new TextEncoder()

export function validateRecord(value: unknown, deviceId: string): VaultRecord {
  if (typeof value !== 'object' || value === null) throw new VaultError('malformed')
  const record = value as Partial<VaultRecord>
  if (Object.keys(record).length !== 9 || record.version !== 1 || record.deviceId !== deviceId
      || record.kdf !== 'PBKDF2-SHA-256' || record.iterations !== iterations
      || record.cipher !== 'AES-256-GCM' || record.key !== 'ECDSA-P256' || !hasSize(record.salt, saltLength, saltLength)
      || !hasSize(record.nonce, nonceLength, nonceLength)
      || !hasSize(record.ciphertext, 17, 1040)) {
    throw new VaultError('malformed')
  }
  return record as VaultRecord
}

function hasSize(value: unknown, min: number, max: number): value is Uint8Array {
  return value instanceof Uint8Array && value.length >= min && value.length <= max
}

function authenticatedHeader(h: Header) {
  return encoder.encode(JSON.stringify([
    'QVA local device vault', h.version, h.deviceId, h.kdf, h.iterations,
    Array.from(h.salt), h.cipher, Array.from(h.nonce), h.key,
  ]))
}

async function derive(password: Uint8Array, salt: Uint8Array, usage: KeyUsage) {
  try {
    if (password.length === 0 || password.length > maximumPassword * 4) throw new VaultError('credentials')
    const base = await crypto.subtle.importKey('raw', password, 'PBKDF2', false, ['deriveKey'])
    return await crypto.subtle.deriveKey(
      { name: 'PBKDF2', hash: 'SHA-256', salt, iterations },
      base, { name: 'AES-GCM', length: 256 }, false, [usage],
    )
  } finally {
    password.fill(0)
  }
}

export async function createVault(deviceId: string, password: Uint8Array) {
  let characters = 0
  for (const byte of password) if ((byte & 0xc0) !== 0x80) characters++
  if (characters < minimumPassword) throw new VaultError('credentials')
  const header: Header = {
    version: 1, deviceId, kdf: 'PBKDF2-SHA-256', iterations,
    salt: crypto.getRandomValues(new Uint8Array(saltLength)), cipher: 'AES-256-GCM',
    nonce: crypto.getRandomValues(new Uint8Array(nonceLength)), key: 'ECDSA-P256',
  }
  const wrappingKey = await derive(password, header.salt, 'encrypt')
  const key = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])
  const privateBytes = new Uint8Array(await crypto.subtle.exportKey('pkcs8', key.privateKey))
  const publicBytes = new Uint8Array(await crypto.subtle.exportKey('spki', key.publicKey))
  const payload = new Uint8Array(2 + privateBytes.length + publicBytes.length)
  payload.set([1, privateBytes.length])
  payload.set(privateBytes, 2)
  payload.set(publicBytes, 2 + privateBytes.length)
  try {
    const ciphertext = new Uint8Array(await crypto.subtle.encrypt({
      name: 'AES-GCM', iv: header.nonce, additionalData: authenticatedHeader(header), tagLength: 128,
    }, wrappingKey, payload))
    return { ...header, ciphertext } satisfies VaultRecord
  } finally {
    privateBytes.fill(0)
    payload.fill(0)
  }
}

export async function unlockVault(value: unknown, deviceId: string, password: Uint8Array) {
  const record = validateRecord(value, deviceId)
  const wrappingKey = await derive(password, record.salt, 'decrypt')
  let payload: Uint8Array
  try {
    payload = new Uint8Array(await crypto.subtle.decrypt({
      name: 'AES-GCM', iv: record.nonce, additionalData: authenticatedHeader(record), tagLength: 128,
    }, wrappingKey, record.ciphertext))
  } catch {
    throw new VaultError('credentials')
  }
  try {
    const privateLength = payload[1]
    if (payload[0] !== 1 || !privateLength || payload.length !== 2 + privateLength + 91) {
      throw new VaultError('malformed')
    }
    return await crypto.subtle.importKey(
      'pkcs8', payload.subarray(2, 2 + privateLength),
      { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign'],
    )
  } catch {
    throw new VaultError('malformed')
  } finally {
    payload.fill(0)
  }
}

import { VaultError, type VaultRecord } from './format'

const database = 'qva-local-vault'
const store = 'devices'

function openDatabase() {
  return new Promise<IDBDatabase>((resolve, reject) => {
    const pending = indexedDB.open(database, 1)
    pending.onupgradeneeded = () => pending.result.createObjectStore(store)
    pending.onsuccess = () => resolve(pending.result)
    pending.onerror = () => reject(new VaultError('unavailable'))
  })
}

async function transact<T>(mode: IDBTransactionMode, operation: (table: IDBObjectStore) => IDBRequest<T>) {
  const db = await openDatabase()
  try {
    return await new Promise<T>((resolve, reject) => {
      const transaction = db.transaction(store, mode)
      const request = operation(transaction.objectStore(store))
      transaction.oncomplete = () => resolve(request.result)
      transaction.onabort = () => reject(new VaultError(request.error?.name === 'ConstraintError'
        ? 'exists' : 'unavailable'))
    })
  } finally {
    db.close()
  }
}

export const readVault = (id: string): Promise<unknown> => transact('readonly', table => table.get(id))
export const addVault = (record: VaultRecord) => transact('readwrite', table => table.add(record, record.deviceId))

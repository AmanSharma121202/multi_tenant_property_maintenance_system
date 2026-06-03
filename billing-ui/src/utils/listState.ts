/** Merge an item into a list by id (insert or replace). */
export function upsertById<T extends { id: string }>(list: T[], item: T): T[] {
  const index = list.findIndex((entry) => entry.id === item.id)
  if (index >= 0) {
    const next = [...list]
    next[index] = item
    return next
  }
  return [...list, item]
}

export function removeById<T extends { id: string }>(list: T[], id: string): T[] {
  return list.filter((entry) => entry.id !== id)
}

/**
 * Merge server list with local state. Server wins for shared ids;
 * keeps local-only rows until Couchbase N1QL indexes catch up.
 */
export function mergeListsById<T extends { id: string }>(server: T[], local: T[]): T[] {
  const byId = new Map<string, T>()
  for (const item of local) {
    byId.set(item.id, item)
  }
  for (const item of server) {
    byId.set(item.id, item)
  }
  return Array.from(byId.values())
}

/** Drop rows hidden by soft-delete (active=false). */
export function withoutInactive<T extends { active?: boolean }>(list: T[]): T[] {
  return list.filter((item) => item.active !== false)
}

/** Drop owners marked INACTIVE. */
export function withoutInactiveOwners<T extends { status?: string }>(list: T[]): T[] {
  return list.filter((item) => item.status !== 'INACTIVE')
}

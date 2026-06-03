/** Encode Couchbase ids (e.g. unit::uuid) for safe use in URL paths. */
export function enc(segment: string): string {
  return encodeURIComponent(segment)
}

export function tenantPath(tenantId: string): string {
  return `/tenants/${enc(tenantId)}`
}

export function tenantResource(tenantId: string, resource: string): string {
  return `${tenantPath(tenantId)}/${resource}`
}

export function tenantResourceId(
  tenantId: string,
  resource: string,
  id: string,
): string {
  return `${tenantResource(tenantId, resource)}/${enc(id)}`
}

export function tenantResourceAction(
  tenantId: string,
  resource: string,
  id: string,
  action: string,
): string {
  return `${tenantResourceId(tenantId, resource, id)}:${action}`
}

/** Collection-level sub-path (e.g. POST .../invoices/generate). */
export function tenantResourceSubpath(
  tenantId: string,
  resource: string,
  subpath: string,
): string {
  return `${tenantResource(tenantId, resource)}/${subpath}`
}

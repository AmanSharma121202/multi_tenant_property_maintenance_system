import { apiRequest } from './client'
import {
  tenantResource,
  tenantResourceAction,
  tenantResourceId,
} from './paths'
import type { Owner } from '../types'

const base = (tenantId: string) => tenantResource(tenantId, 'owners')

export function listOwners(tenantId: string, filter?: string) {
  const query = filter ? `?filter=${encodeURIComponent(filter)}` : ''
  return apiRequest<Owner[]>(`${base(tenantId)}${query}`)
}

export function createOwner(
  tenantId: string,
  data: { name: string; email: string; phone: string; status: string },
) {
  return apiRequest<Owner>(base(tenantId), {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateOwner(
  tenantId: string,
  ownerId: string,
  data: Partial<{ name: string; email: string; phone: string; status: string }>,
) {
  return apiRequest<Owner>(tenantResourceId(tenantId, 'owners', ownerId), {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export function deleteOwner(tenantId: string, ownerId: string) {
  return apiRequest<void>(tenantResourceId(tenantId, 'owners', ownerId), {
    method: 'DELETE',
  })
}

export function linkUnit(tenantId: string, ownerId: string, unitId: string) {
  return apiRequest<Owner>(tenantResourceAction(tenantId, 'owners', ownerId, 'link-unit'), {
    method: 'POST',
    body: JSON.stringify({ unitId }),
  })
}

export function unlinkUnit(tenantId: string, ownerId: string, unitId: string) {
  return apiRequest<Owner>(tenantResourceAction(tenantId, 'owners', ownerId, 'unlink-unit'), {
    method: 'POST',
    body: JSON.stringify({ unitId }),
  })
}

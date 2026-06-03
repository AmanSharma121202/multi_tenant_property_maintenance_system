import { apiRequest } from './client'
import {
  tenantResource,
  tenantResourceAction,
  tenantResourceId,
} from './paths'
import type { Unit } from '../types'

const base = (tenantId: string) => tenantResource(tenantId, 'units')

export function listUnits(tenantId: string, filter?: string) {
  const query = filter ? `?filter=${encodeURIComponent(filter)}` : ''
  return apiRequest<Unit[]>(`${base(tenantId)}${query}`)
}

export function createUnit(
  tenantId: string,
  data: { unitNumber: string; profileCode: string; active: boolean },
) {
  return apiRequest<Unit>(base(tenantId), {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateUnit(
  tenantId: string,
  unitId: string,
  data: Partial<{ unitNumber: string; profileCode: string; active: boolean }>,
) {
  return apiRequest<Unit>(tenantResourceId(tenantId, 'units', unitId), {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export function deleteUnit(tenantId: string, unitId: string) {
  return apiRequest<void>(tenantResourceId(tenantId, 'units', unitId), {
    method: 'DELETE',
  })
}

export function linkOwner(
  tenantId: string,
  unitId: string,
  ownerId: string,
  primary = true,
) {
  return apiRequest<Unit>(tenantResourceAction(tenantId, 'units', unitId, 'link-owner'), {
    method: 'POST',
    body: JSON.stringify({ ownerId, primary }),
  })
}

export function unlinkOwner(tenantId: string, unitId: string) {
  return apiRequest<Unit>(tenantResourceAction(tenantId, 'units', unitId, 'unlink-owner'), {
    method: 'POST',
  })
}

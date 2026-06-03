import { apiRequest } from './client'
import type { Tenant } from '../types'

export function listTenants() {
  return apiRequest<Tenant[]>('/tenants')
}

export function getTenant(tenantId: string) {
  return apiRequest<Tenant>(`/tenants/${tenantId}`)
}

export function createTenant(data: {
  name: string
  currency: string
  billing_date: string
  lateFeeType: string
  lateFeeValue: number
  address: string
}) {
  return apiRequest<Tenant>('/tenants', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateTenant(
  tenantId: string,
  data: Partial<{
    name: string
    currency: string
    billing_date: string
    lateFeeType: string
    lateFeeValue: number
    address: string
  }>,
) {
  return apiRequest<Tenant>(`/tenants/${tenantId}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

import { apiRequest } from './client'
import {
  tenantResource,
  tenantResourceId,
  tenantResourceSubpath,
} from './paths'
import type { Invoice } from '../types'

const base = (tenantId: string) => tenantResource(tenantId, 'invoices')

export function listInvoices(tenantId: string, filter?: string) {
  const query = filter ? `?filter=${encodeURIComponent(filter)}` : ''
  return apiRequest<Invoice[]>(`${base(tenantId)}${query}`)
}

export function generateTenantInvoices(
  tenantId: string,
  data: { year: number; month: number; unitId?: string },
) {
  return apiRequest<Record<string, string>>(
    tenantResourceSubpath(tenantId, 'invoices', 'generate'),
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  )
}

export function getInvoice(tenantId: string, invoiceId: string) {
  return apiRequest<Invoice>(tenantResourceId(tenantId, 'invoices', invoiceId))
}

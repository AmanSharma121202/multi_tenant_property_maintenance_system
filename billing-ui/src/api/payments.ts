import { apiRequest } from './client'
import type { Payment } from '../types'

const base = (tenantId: string) => `/tenants/${tenantId}/payments`

export function listPayments(tenantId: string, filter?: string) {
  const query = filter ? `?filter=${encodeURIComponent(filter)}` : ''
  return apiRequest<Payment[]>(`${base(tenantId)}${query}`)
}

export function recordPayment(
  tenantId: string,
  data: {
    unitId: string
    method: string
    amount: number
    txnRef?: string
    notes?: string
    paidBy?: string
  },
  idempotencyKey?: string,
) {
  const headers: Record<string, string> = {}
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey
  }
  return apiRequest<Payment>(base(tenantId), {
    method: 'POST',
    headers,
    body: JSON.stringify(data),
  })
}

export function formatMoney(amount: number | undefined, currency = 'INR') {
  if (amount == null) return '—'
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount)
}

export function formatDate(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('en-IN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

export function tenantBillingDate(tenant: { billing_date?: string; billingDate?: string }) {
  return tenant.billing_date ?? tenant.billingDate ?? ''
}

export function monthName(month: number) {
  return new Date(2000, month - 1, 1).toLocaleString('en', { month: 'long' })
}

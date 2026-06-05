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

export function tenantBillingDay(tenant: { billing_day?: number; billingDay?: number }) {
  return tenant.billing_day ?? tenant.billingDay ?? 1
}

export function formatOrdinalDay(day: number) {
  if (day <= 0 || day > 31) return String(day)
  const suffixes = ['th', 'st', 'nd', 'rd']
  const mod100 = day % 100
  const suffix = (mod100 >= 11 && mod100 <= 13) ? 'th' : (suffixes[day % 10] ?? 'th')
  return `${day}${suffix}`
}

export function monthName(month: number) {
  return new Date(2000, month - 1, 1).toLocaleString('en', { month: 'long' })
}


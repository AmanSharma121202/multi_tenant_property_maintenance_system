export type FilterModel = 'owner' | 'unit' | 'profile' | 'payment' | 'invoice'

export type FilterOperator = 'eq' | 'ne' | 'gte' | 'lte'

export type FilterFieldKind = 'text' | 'select' | 'boolean' | 'number' | 'date' | 'month'

export interface FilterFieldOption {
  value: string
  label: string
}

export interface FilterFieldConfig {
  field: string
  label: string
  kind: FilterFieldKind
  operators: FilterOperator[]
  options?: FilterFieldOption[]
  placeholder?: string
}

export interface FilterChip {
  id: string
  field: string
  operator: FilterOperator
  value: string
}

export const OPERATOR_LABELS: Record<FilterOperator, string> = {
  eq: 'is',
  ne: 'is not',
  gte: 'from',
  lte: 'until',
}

const OWNER_STATUS: FilterFieldOption[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
]

const INVOICE_STATUS: FilterFieldOption[] = [
  { value: 'PAID', label: 'Paid' },
  { value: 'DUE', label: 'Due' },
  { value: 'OVERDUE', label: 'Overdue' },
]

const PAYMENT_METHODS: FilterFieldOption[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'UPI', label: 'UPI' },
  { value: 'CHEQUE', label: 'Cheque' },
]

const MONTHS: FilterFieldOption[] = Array.from({ length: 12 }, (_, i) => ({
  value: String(i + 1),
  label: new Date(2000, i, 1).toLocaleString('en-US', { month: 'long' }),
}))

const BOOLEAN_OPTIONS: FilterFieldOption[] = [
  { value: 'true', label: 'Yes' },
  { value: 'false', label: 'No' },
]

/** Filterable fields per model — must stay in sync with backend FILTERABLE_FIELDS. */
export const FILTER_FIELDS: Record<FilterModel, FilterFieldConfig[]> = {
  owner: [
    { field: 'name', label: 'Name', kind: 'text', operators: ['eq', 'ne'], placeholder: 'Owner name' },
    { field: 'email', label: 'Email', kind: 'text', operators: ['eq', 'ne'], placeholder: 'Email address' },
    { field: 'phone', label: 'Phone', kind: 'text', operators: ['eq', 'ne'], placeholder: 'Phone number' },
    { field: 'status', label: 'Status', kind: 'select', operators: ['eq', 'ne'], options: OWNER_STATUS },
  ],
  unit: [
    { field: 'unitNumber', label: 'Unit number', kind: 'text', operators: ['eq', 'ne'], placeholder: 'e.g. A-101' },
    { field: 'profileCode', label: 'Profile', kind: 'select', operators: ['eq', 'ne'], options: [] },
    { field: 'active', label: 'Active', kind: 'boolean', operators: ['eq'], options: BOOLEAN_OPTIONS },
  ],
  profile: [
    { field: 'code', label: 'Code', kind: 'text', operators: ['eq', 'ne'], placeholder: 'e.g. 2BHK' },
    { field: 'label', label: 'Label', kind: 'text', operators: ['eq', 'ne'], placeholder: 'Profile label' },
    { field: 'monthlyAmount', label: 'Monthly amount', kind: 'number', operators: ['eq', 'gte', 'lte'], placeholder: 'Amount' },
    { field: 'active', label: 'Active', kind: 'boolean', operators: ['eq'], options: BOOLEAN_OPTIONS },
  ],
  payment: [
    { field: 'method', label: 'Method', kind: 'select', operators: ['eq', 'ne'], options: PAYMENT_METHODS },
    { field: 'amount', label: 'Amount', kind: 'number', operators: ['eq', 'gte', 'lte'], placeholder: 'Amount' },
    { field: 'receivedAt', label: 'Received date', kind: 'date', operators: ['gte', 'lte'], placeholder: 'YYYY-MM-DD' },
    { field: 'txnRef', label: 'Txn ref', kind: 'text', operators: ['eq', 'ne'], placeholder: 'Reference' },
  ],
  invoice: [
    { field: 'unitId', label: 'Unit', kind: 'select', operators: ['eq', 'ne'], options: [] },
    { field: 'year', label: 'Year', kind: 'number', operators: ['eq', 'gte', 'lte'], placeholder: 'e.g. 2026' },
    { field: 'month', label: 'Month', kind: 'month', operators: ['eq'], options: MONTHS },
    { field: 'status', label: 'Status', kind: 'select', operators: ['eq', 'ne'], options: INVOICE_STATUS },
    { field: 'issueDate', label: 'Issue date', kind: 'date', operators: ['gte', 'lte'] },
    { field: 'dueDate', label: 'Due date', kind: 'date', operators: ['gte', 'lte'] },
  ],
}

export function getFieldConfig(model: FilterModel, field: string): FilterFieldConfig | undefined {
  return FILTER_FIELDS[model].find((f) => f.field === field)
}

export function resolveFieldOptions(
  config: FilterFieldConfig,
  dynamicOptions?: Record<string, FilterFieldOption[]>,
): FilterFieldOption[] {
  if (config.options?.length) return config.options
  return dynamicOptions?.[config.field] ?? []
}

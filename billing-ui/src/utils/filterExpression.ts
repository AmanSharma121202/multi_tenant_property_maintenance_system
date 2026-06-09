import {
  type FilterChip,
  type FilterFieldConfig,
  type FilterFieldOption,
  type FilterModel,
  type FilterOperator,
  OPERATOR_LABELS,
  getFieldConfig,
  resolveFieldOptions,
} from './filterFields'

const OPERATOR_SYMBOLS: Record<FilterOperator, string> = {
  eq: '==',
  ne: '!=',
  gte: '>=',
  lte: '<=',
}

function quoteString(value: string): string {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`
}

function formatLiteral(config: FilterFieldConfig, value: string): string {
  switch (config.kind) {
    case 'boolean':
      return value === 'true' ? 'true' : 'false'
    case 'number':
    case 'month':
      return value.trim()
    case 'date':
      return quoteString(`${value}T00:00:00Z`)
    default:
      return quoteString(value)
  }
}

export function newFilterChipId(): string {
  return `chip-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
}

export function isDuplicateChip(
  chips: FilterChip[],
  field: string,
  operator: FilterOperator,
  value: string,
): boolean {
  const normalized = value.trim()
  return chips.some(
    (c) => c.field === field && c.operator === operator && c.value === normalized,
  )
}

export function usedFilterFields(chips: FilterChip[], model: FilterModel): Set<string> {
  const effective = model === 'invoice' ? normalizeInvoiceFilterChips(chips) : chips
  return new Set(effective.map((c) => c.field))
}

export function isFieldUsed(chips: FilterChip[], field: string, model: FilterModel): boolean {
  return usedFilterFields(chips, model).has(field)
}

/** Invoice month filter requires year — inject current year when missing. */
export function normalizeInvoiceFilterChips(chips: FilterChip[]): FilterChip[] {
  const hasMonth = chips.some((c) => c.field === 'month')
  const hasYear = chips.some((c) => c.field === 'year')
  if (!hasMonth || hasYear) return chips

  return [
    {
      id: newFilterChipId(),
      field: 'year',
      operator: 'eq',
      value: String(new Date().getFullYear()),
    },
    ...chips,
  ]
}

export function buildFilterExpression(chips: FilterChip[], model: FilterModel): string {
  const effectiveChips = model === 'invoice' ? normalizeInvoiceFilterChips(chips) : chips
  if (!effectiveChips.length) return ''

  const parts = effectiveChips
    .map((chip) => {
      const config = getFieldConfig(model, chip.field)
      if (!config || !chip.value.trim()) return ''
      const symbol = OPERATOR_SYMBOLS[chip.operator]
      return `${chip.field}${symbol}${formatLiteral(config, chip.value.trim())}`
    })
    .filter(Boolean)

  return parts.join(' && ')
}

function displayValue(
  config: FilterFieldConfig,
  value: string,
  dynamicOptions?: Record<string, FilterFieldOption[]>,
): string {
  const options = resolveFieldOptions(config, dynamicOptions)
  const match = options.find((o) => o.value === value)
  if (match) return match.label

  if (config.kind === 'boolean') {
    return value === 'true' ? 'Yes' : 'No'
  }
  if (config.kind === 'month') {
    const month = options.find((o) => o.value === value)
    return month?.label ?? value
  }
  if (config.kind === 'date') {
    try {
      return new Date(`${value}T00:00:00Z`).toLocaleDateString()
    } catch {
      return value
    }
  }
  return value
}

export function formatChipLabel(
  chip: FilterChip,
  model: FilterModel,
  dynamicOptions?: Record<string, FilterFieldOption[]>,
): string {
  const config = getFieldConfig(model, chip.field)
  if (!config) return chip.field

  const op = OPERATOR_LABELS[chip.operator]
  const val = displayValue(config, chip.value, dynamicOptions)
  return `${config.label} ${op} ${val}`
}

/** Options not already used for the same field + operator in existing chips. */
export function availableOptionsForField(
  chips: FilterChip[],
  config: FilterFieldConfig,
  operator: FilterOperator,
  dynamicOptions?: Record<string, FilterFieldOption[]>,
): FilterFieldOption[] {
  const options = resolveFieldOptions(config, dynamicOptions)
  const usedValues = new Set(
    chips
      .filter((c) => c.field === config.field && c.operator === operator)
      .map((c) => c.value),
  )
  return options.filter((o) => !usedValues.has(o.value))
}

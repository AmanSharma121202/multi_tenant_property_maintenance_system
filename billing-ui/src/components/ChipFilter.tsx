import { useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  FILTER_FIELDS,
  type FilterChip,
  type FilterFieldConfig,
  type FilterFieldOption,
  type FilterModel,
  type FilterOperator,
  OPERATOR_LABELS,
} from '../utils/filterFields'
import {
  availableOptionsForField,
  formatChipLabel,
  isDuplicateChip,
  isFieldUsed,
  newFilterChipId,
  normalizeInvoiceFilterChips,
  usedFilterFields,
} from '../utils/filterExpression'

interface ChipFilterProps {
  model: FilterModel
  chips: FilterChip[]
  onChange: (chips: FilterChip[]) => void
  dynamicOptions?: Record<string, FilterFieldOption[]>
}

export function ChipFilter({ model, chips, onChange, dynamicOptions }: ChipFilterProps) {
  const fieldConfigs = useMemo(() => FILTER_FIELDS[model], [model])
  const usedFields = useMemo(() => usedFilterFields(chips, model), [chips, model])
  const availableFieldConfigs = useMemo(
    () => fieldConfigs.filter((f) => !usedFields.has(f.field)),
    [fieldConfigs, usedFields],
  )

  const [field, setField] = useState(availableFieldConfigs[0]?.field ?? '')
  const [operator, setOperator] = useState<FilterOperator>('eq')
  const [value, setValue] = useState('')

  const displayChips = model === 'invoice' ? normalizeInvoiceFilterChips(chips) : chips

  const activeConfig =
    availableFieldConfigs.find((f) => f.field === field) ?? availableFieldConfigs[0]
  const operators = activeConfig?.operators ?? ['eq']
  const selectableOptions = activeConfig
    ? availableOptionsForField(chips, activeConfig, operator, dynamicOptions)
    : []

  useEffect(() => {
    if (availableFieldConfigs.length === 0) return
    if (!availableFieldConfigs.some((f) => f.field === field)) {
      setField(availableFieldConfigs[0].field)
    }
  }, [availableFieldConfigs, field])

  useEffect(() => {
    if (!activeConfig) return
    setOperator(activeConfig.operators[0])
    if (activeConfig.kind === 'text' || activeConfig.kind === 'number' || activeConfig.kind === 'date') {
      setValue('')
      return
    }
    const options = availableOptionsForField(
      chips,
      activeConfig,
      activeConfig.operators[0],
      dynamicOptions,
    )
    setValue(options[0]?.value ?? '')
  }, [activeConfig?.field, chips, dynamicOptions])

  useEffect(() => {
    if (!activeConfig) return
    if (activeConfig.kind === 'text' || activeConfig.kind === 'number' || activeConfig.kind === 'date') {
      return
    }
    const options = availableOptionsForField(chips, activeConfig, operator, dynamicOptions)
    if (value && options.some((o) => o.value === value)) return
    setValue(options[0]?.value ?? '')
  }, [operator, activeConfig, chips, dynamicOptions, value])

  const resetDraft = (config: FilterFieldConfig) => {
    setField(config.field)
    setOperator(config.operators[0])
    setValue(defaultValueForField(chips, config, config.operators[0], dynamicOptions))
  }

  const handleFieldChange = (nextField: string) => {
    const config = availableFieldConfigs.find((f) => f.field === nextField)
    if (config) resetDraft(config)
  }

  const handleAdd = (e: FormEvent) => {
    e.preventDefault()
    if (!activeConfig || !value.trim()) return
    if (isFieldUsed(chips, activeConfig.field, model)) return
    if (isDuplicateChip(chips, activeConfig.field, operator, value)) return

    onChange([
      ...chips,
      {
        id: newFilterChipId(),
        field: activeConfig.field,
        operator,
        value: value.trim(),
      },
    ])
    setValue(defaultValueForField(chips, activeConfig, operator, dynamicOptions))
  }

  const removeChip = (id: string) => {
    onChange(chips.filter((c) => c.id !== id))
  }

  const clearAll = () => onChange([])

  const canAdd =
    !!activeConfig &&
    value.trim().length > 0 &&
    !isFieldUsed(chips, activeConfig.field, model) &&
    !isDuplicateChip(chips, activeConfig.field, operator, value) &&
    (activeConfig.kind === 'text' ||
      activeConfig.kind === 'number' ||
      activeConfig.kind === 'date' ||
      selectableOptions.some((o) => o.value === value))

  if (fieldConfigs.length === 0) return null

  return (
    <div className="chip-filter">
      {displayChips.length > 0 && (
        <div className="chip-filter-chips">
          {displayChips.map((chip) => (
            <span key={chip.id} className="filter-chip">
              <span className="filter-chip-label">
                {formatChipLabel(chip, model, dynamicOptions)}
              </span>
              {chips.some((c) => c.id === chip.id) && (
                <button
                  type="button"
                  className="filter-chip-remove"
                  onClick={() => removeChip(chip.id)}
                  aria-label="Remove filter"
                >
                  ×
                </button>
              )}
            </span>
          ))}
          <button type="button" className="btn btn-sm btn-ghost" onClick={clearAll}>
            Clear all
          </button>
        </div>
      )}

      {availableFieldConfigs.length === 0 ? (
        <p className="chip-filter-meta">All filter fields are in use. Remove a chip to add another.</p>
      ) : (
        <form className="chip-filter-add" onSubmit={handleAdd}>
          <select
            value={field}
            onChange={(e) => handleFieldChange(e.target.value)}
            aria-label="Filter field"
          >
            {availableFieldConfigs.map((f) => (
              <option key={f.field} value={f.field}>
                {f.label}
              </option>
            ))}
          </select>

          {operators.length > 1 && (
            <select
              value={operator}
              onChange={(e) => setOperator(e.target.value as FilterOperator)}
              aria-label="Filter operator"
            >
              {operators.map((op) => (
                <option key={op} value={op}>
                  {OPERATOR_LABELS[op]}
                </option>
              ))}
            </select>
          )}

          {activeConfig && renderValueInput(activeConfig, value, setValue, selectableOptions)}

          <button type="submit" className="btn btn-sm btn-primary" disabled={!canAdd}>
            Add filter
          </button>
        </form>
      )}
    </div>
  )
}

function defaultValueForField(
  chips: FilterChip[],
  config: FilterFieldConfig,
  operator: FilterOperator,
  dynamicOptions?: Record<string, FilterFieldOption[]>,
): string {
  const options = availableOptionsForField(chips, config, operator, dynamicOptions)
  if (options.length) return options[0].value
  return ''
}

function renderValueInput(
  config: FilterFieldConfig,
  value: string,
  onChange: (v: string) => void,
  options: FilterFieldOption[],
) {
  if (config.kind === 'select' || config.kind === 'boolean' || config.kind === 'month') {
    return (
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-label="Filter value"
        required
        disabled={options.length === 0}
      >
        {options.length === 0 && <option value="">No values left</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    )
  }

  if (config.kind === 'number') {
    return (
      <input
        type="number"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={config.placeholder}
        required
      />
    )
  }

  if (config.kind === 'date') {
    return (
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required
      />
    )
  }

  return (
    <input
      type="text"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={config.placeholder}
      required
    />
  )
}

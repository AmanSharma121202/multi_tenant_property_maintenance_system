import type { Unit } from '../types'
import { formatDate, monthName } from './format'

function cycleStartIso(year: number, month: number): string {
  return `${year}-${String(month).padStart(2, '0')}-01`
}

function isCycleBeforeUnitStart(year: number, month: number, unitStartDate: string): boolean {
  return cycleStartIso(year, month) < unitStartDate.slice(0, 10)
}

/** Returns an error message when generation must be blocked, otherwise null. */
export function validateInvoiceGenerationPeriod(
  year: number,
  month: number,
  units: Unit[],
  unitId?: string,
): string | null {
  const targets = unitId
    ? units.filter((u) => u.id === unitId)
    : units.filter((u) => u.active)

  if (unitId && targets.length === 0) {
    return 'Selected unit not found'
  }

  const blocked = targets.filter(
    (u) => u.unitStartDate && isCycleBeforeUnitStart(year, month, u.unitStartDate),
  )

  if (blocked.length === 0) {
    return null
  }

  if (blocked.length === 1) {
    const unit = blocked[0]
    return `Cannot generate invoice for ${unit.unitNumber}: billing period is before unit start date (${formatDate(unit.unitStartDate)})`
  }

  const example = blocked[0]
  return `Cannot generate invoices for ${monthName(month)} ${year}: ${blocked.length} unit(s) did not exist yet (e.g. ${example.unitNumber} starts ${formatDate(example.unitStartDate)})`
}

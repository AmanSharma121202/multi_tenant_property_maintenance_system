import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { generateTenantInvoices, listInvoices } from '../../api/invoices'
import { listUnits } from '../../api/units'
import { ApiClientError } from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import type { Invoice, Unit } from '../../types'
import { useLoadSequence } from '../../hooks/useLoadSequence'
import { formatMoney, monthName } from '../../utils/format'
import { Modal } from '../../components/Modal'
import { RefreshButton } from '../../components/RefreshButton'

export function InvoicesPage() {
  const { tenantId } = useAuth()
  const [searchParams] = useSearchParams()
  const [invoices, setInvoices] = useState<Invoice[]>([])
  const [units, setUnits] = useState<Unit[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filterInput, setFilterInput] = useState('')
  const [appliedFilter, setAppliedFilter] = useState('')
  const [generateOpen, setGenerateOpen] = useState(false)
  const [generateForm, setGenerateForm] = useState(() => {
    const now = new Date()
    const prev = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1))
    return {
      year: prev.getUTCFullYear(),
      month: prev.getUTCMonth() + 1,
      unitId: '',
    }
  })
  const { nextLoadId, isLatest } = useLoadSequence()

  const unitFilterId = searchParams.get('unitId')

  const load = useCallback(async () => {
    if (!tenantId) return
    const loadId = nextLoadId()
    setLoading(true)
    setError('')
    try {
      const filterParts = [] as string[]
      if (unitFilterId) {
        filterParts.push(`unitId=="${unitFilterId}"`)
      }
      if (appliedFilter) {
        filterParts.push(appliedFilter)
      }
      const filter = filterParts.length ? filterParts.join(' && ') : undefined
      const [inv, u] = await Promise.all([
        listInvoices(tenantId, filter),
        listUnits(tenantId),
      ])
      if (!isLatest(loadId)) return
      setInvoices(
        [...inv].sort((a, b) => b.year - a.year || b.month - a.month),
      )
      setUnits(u)
    } catch (err) {
      if (!isLatest(loadId)) return
      setError(err instanceof ApiClientError ? err.message : 'Failed to load invoices')
    } finally {
      if (isLatest(loadId)) setLoading(false)
    }
  }, [tenantId, nextLoadId, isLatest, unitFilterId, appliedFilter])

  useEffect(() => {
    load()
  }, [load])

  const unitNumber = (unitId: string) =>
    units.find((u) => u.id === unitId)?.unitNumber ?? unitId

  const applyFilter = (e: FormEvent) => {
    e.preventDefault()
    setAppliedFilter(filterInput.trim())
  }

  const clearFilter = () => {
    setFilterInput('')
    setAppliedFilter('')
  }

  const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

  const handleGenerate = async (e: FormEvent) => {
    e.preventDefault()
    if (!tenantId) return
    setError('')
    try {
      const result = await generateTenantInvoices(tenantId, {
        year: generateForm.year,
        month: generateForm.month,
        unitId: generateForm.unitId || undefined,
      })
      setGenerateOpen(false)
      // Allow Kafka consumption + Couchbase index lag before refresh.
      const delayMs = result.queued === 'true' ? 2000 : 700
      await sleep(delayMs)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Failed to generate invoices')
      return
    }
    await load()
  }

  const statusClass = (s: string) => {
    if (s === 'PAID') return 'badge-success'
    if (s === 'OVERDUE') return 'badge-danger'
    return 'badge-warning'
  }

  if (!tenantId) {
    return <div className="alert alert-error">No tenant assigned.</div>
  }

  return (
    <div className="page-section">
      <div className="toolbar">
        <p className="toolbar-meta">{invoices.length} invoice(s)</p>
        <form className="filter-form" onSubmit={applyFilter}>
          <input
            value={filterInput}
            onChange={(e) => setFilterInput(e.target.value)}
            placeholder='Filter (e.g. status=="DUE" && year==2026 && month==5)'
          />
          <button type="submit" className="btn btn-sm">Apply</button>
          <button type="button" className="btn btn-sm" onClick={clearFilter}>Clear</button>
        </form>
        <div className="toolbar-actions">
          <RefreshButton onClick={() => load()} disabled={loading} />
          <button type="button" className="btn btn-primary" onClick={() => setGenerateOpen(true)}>
            Generate invoices
          </button>
        </div>
      </div>

      {unitFilterId && (
        <div className="alert alert-info">
          Showing invoices for unit <strong>{unitNumber(unitFilterId)}</strong>
        </div>
      )}

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <div className="page-center"><div className="spinner" /></div>
      ) : (
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Unit</th>
                <th>Period</th>
                <th>Charges</th>
                <th>Closing</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id}>
                  <td>{unitNumber(inv.unitId)}</td>
                  <td>{monthName(inv.month)} {inv.year}</td>
                  <td>{formatMoney(Number(inv.currentCharges))}</td>
                  <td>{formatMoney(Number(inv.closingBalance))}</td>
                  <td>
                    <span className={`badge ${statusClass(inv.status)}`}>{inv.status}</span>
                  </td>
                </tr>
              ))}
              {invoices.length === 0 && (
                <tr>
                  <td colSpan={5} className="empty-cell">
                    No invoices found for this view.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        title="Generate invoices"
        open={generateOpen}
        onClose={() => setGenerateOpen(false)}
      >
        <form className="form-stack" onSubmit={handleGenerate}>
          <label>
            Year
            <input
              type="number"
              min={2000}
              max={2100}
              value={generateForm.year}
              onChange={(e) => setGenerateForm({ ...generateForm, year: Number(e.target.value) })}
              required
            />
          </label>
          <label>
            Month
            <input
              type="number"
              min={1}
              max={12}
              value={generateForm.month}
              onChange={(e) => setGenerateForm({ ...generateForm, month: Number(e.target.value) })}
              required
            />
          </label>
          <label>
            Unit (optional)
            <select
              value={generateForm.unitId}
              onChange={(e) => setGenerateForm({ ...generateForm, unitId: e.target.value })}
            >
              <option value="">All units</option>
              {units.map((u) => (
                <option key={u.id} value={u.id}>{u.unitNumber}</option>
              ))}
            </select>
          </label>
          <div className="form-actions">
            <button type="button" className="btn" onClick={() => setGenerateOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              Generate
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}

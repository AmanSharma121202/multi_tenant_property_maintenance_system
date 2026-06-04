import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { listPayments } from '../../api/payments'
import { listUnits } from '../../api/units'
import { ApiClientError } from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import type { Payment, Unit } from '../../types'
import { useLoadSequence } from '../../hooks/useLoadSequence'
import { formatDate, formatMoney } from '../../utils/format'
import { mergeListsById } from '../../utils/listState'
import { Modal } from '../../components/Modal'
import { RefreshButton } from '../../components/RefreshButton'

export function PaymentsPage() {
  const { tenantId } = useAuth()
  const [payments, setPayments] = useState<Payment[]>([])
  const [units, setUnits] = useState<Unit[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filterInput, setFilterInput] = useState('')
  const [appliedFilter, setAppliedFilter] = useState('')
  const [selected, setSelected] = useState<Payment | null>(null)
  const { nextLoadId, isLatest } = useLoadSequence()

  const load = useCallback(async () => {
    if (!tenantId) return
    const loadId = nextLoadId()
    setLoading(true)
    setError('')
    try {
      const [p, u] = await Promise.all([
        listPayments(tenantId, appliedFilter || undefined),
        listUnits(tenantId),
      ])
      if (!isLatest(loadId)) return
      setPayments(p)
      setUnits((prev) => mergeListsById(u, prev))
    } catch (err) {
      if (!isLatest(loadId)) return
      setError(err instanceof ApiClientError ? err.message : 'Failed to load payments')
    } finally {
      if (isLatest(loadId)) setLoading(false)
    }
  }, [tenantId, nextLoadId, isLatest, appliedFilter])

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

  if (!tenantId) {
    return <div className="alert alert-error">No tenant assigned.</div>
  }

  return (
    <div className="page-section">
      <div className="toolbar">
        <p className="toolbar-meta">{payments.length} payment(s)</p>
        <form className="filter-form" onSubmit={applyFilter}>
          <input
            value={filterInput}
            onChange={(e) => setFilterInput(e.target.value)}
            placeholder='Filter (e.g. method=="UPI" && amount>=500)'
          />
          <button type="submit" className="btn btn-sm">Apply</button>
          <button type="button" className="btn btn-sm" onClick={clearFilter}>Clear</button>
        </form>
        <RefreshButton onClick={() => load()} disabled={loading} />
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <div className="page-center"><div className="spinner" /></div>
      ) : (
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Unit</th>
                <th>Method</th>
                <th>Amount</th>
                <th>Received</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id}>
                  <td>{unitNumber(p.unitId)}</td>
                  <td>{p.method}</td>
                  <td>{formatMoney(p.amount)}</td>
                  <td>{formatDate(p.receivedAt)}</td>
                  <td className="actions-cell">
                    <button type="button" className="btn btn-sm" onClick={() => setSelected(p)}>
                      View details
                    </button>
                  </td>
                </tr>
              ))}
              {payments.length === 0 && (
                <tr>
                  <td colSpan={5} className="empty-cell">
                    No payments found for this view.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        title={`Payment details — ${selected?.id ?? ''}`}
        open={!!selected}
        onClose={() => setSelected(null)}
      >
        {selected && (
          <div className="form-stack">
            <p><strong>Unit:</strong> {unitNumber(selected.unitId)}</p>
            <p><strong>Method:</strong> {selected.method}</p>
            <p><strong>Amount:</strong> {formatMoney(selected.amount)}</p>
            <p><strong>Received:</strong> {formatDate(selected.receivedAt)}</p>
            <p><strong>Txn ref:</strong> {selected.txnRef ?? '—'}</p>
            <p><strong>Paid by:</strong> {selected.paidBy ?? '—'}</p>
            <p><strong>Notes:</strong> {selected.notes ?? '—'}</p>
            <div className="form-actions">
              <button type="button" className="btn" onClick={() => setSelected(null)}>
                Close
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

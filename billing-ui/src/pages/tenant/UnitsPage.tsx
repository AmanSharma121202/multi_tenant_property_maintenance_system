import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { listOwners } from '../../api/owners'
import { listProfiles } from '../../api/profiles'
import {
  createUnit,
  deleteUnit,
  linkOwner,
  listUnits,
  unlinkOwner,
  updateUnit,
} from '../../api/units'
import { Modal } from '../../components/Modal'
import { ApiClientError } from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import type { Owner, Profile, Unit } from '../../types'
import { useLoadSequence } from '../../hooks/useLoadSequence'
import {
  mergeListsById,
  removeById,
  upsertById,
  withoutInactive,
  withoutInactiveOwners,
} from '../../utils/listState'
import { recordPayment } from '../../api/payments'
import { formatMoney } from '../../utils/format'

const PROFILE_CODES = ['1BHK', '2BHK', '3BHK', 'VILLA']
const PAYMENT_METHODS = ['CASH', 'BANK_TRANSFER', 'UPI', 'CHEQUE']

export function UnitsPage() {
  const { tenantId } = useAuth()
  const navigate = useNavigate()
  const [units, setUnits] = useState<Unit[]>([])
  const [profiles, setProfiles] = useState<Profile[]>([])
  const [owners, setOwners] = useState<Owner[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filterInput, setFilterInput] = useState('')
  const [appliedFilter, setAppliedFilter] = useState('')
  const [modal, setModal] = useState<'create' | 'edit' | 'link' | 'details' | null>(null)
  const [selected, setSelected] = useState<Unit | null>(null)
  const [form, setForm] = useState({ unitNumber: '', profileCode: '2BHK', active: true })
  const [linkOwnerId, setLinkOwnerId] = useState('')
  const [saving, setSaving] = useState(false)
  const [paymentSaving, setPaymentSaving] = useState(false)
  const [paymentForm, setPaymentForm] = useState({
    method: 'UPI',
    amount: 0,
    txnRef: '',
    notes: '',
    paidBy: '',
  })
  const { nextLoadId, isLatest } = useLoadSequence()

  const load = useCallback(async () => {
    if (!tenantId) return
    const loadId = nextLoadId()
    setLoading(true)
    setError('')
    try {
      const [u, p, o] = await Promise.all([
        listUnits(tenantId, appliedFilter || undefined),
        listProfiles(tenantId),
        listOwners(tenantId),
      ])
      if (!isLatest(loadId)) return
      setUnits((prev) => {
        if (appliedFilter) {
          // When filtering, show exactly what the server returns (avoid merging in stale local rows).
          return u
        }
        const merged = mergeListsById(u, prev)
        return withoutInactive(merged)
      })
      setProfiles((prev) => withoutInactive(mergeListsById(p, prev)))
      setOwners((prev) => withoutInactiveOwners(mergeListsById(o, prev)))
    } catch (err) {
      if (!isLatest(loadId)) return
      setError(err instanceof ApiClientError ? err.message : 'Failed to load units')
    } finally {
      if (isLatest(loadId)) setLoading(false)
    }
  }, [tenantId, nextLoadId, isLatest, appliedFilter])

  useEffect(() => {
    load()
  }, [load])

  const profileCodes = profiles.length
    ? profiles.map((p) => p.code)
    : PROFILE_CODES

  const openCreate = () => {
    setForm({ unitNumber: '', profileCode: profileCodes[0] ?? '2BHK', active: true })
    setModal('create')
  }

  const openEdit = (unit: Unit) => {
    setSelected(unit)
    setForm({
      unitNumber: unit.unitNumber,
      profileCode: unit.profileCode,
      active: unit.active,
    })
    setModal('edit')
  }

  const openLink = (unit: Unit) => {
    setSelected(unit)
    setLinkOwnerId(unit.ownerId ?? '')
    setModal('link')
  }

  const openDetails = (unit: Unit) => {
    setSelected(unit)
    setPaymentForm({
      method: 'UPI',
      amount: Number(unit.dueAmount ?? 0),
      txnRef: '',
      notes: `Payment for ${unit.unitNumber}`,
      paidBy: '',
    })
    setModal('details')
  }

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault()
    if (!tenantId) return
    setSaving(true)
    try {
      const created = await createUnit(tenantId, form)
      setUnits((prev) => upsertById(prev, created))
      setModal(null)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Create failed')
    } finally {
      setSaving(false)
    }
  }

  const handleUpdate = async (e: FormEvent) => {
    e.preventDefault()
    if (!tenantId || !selected) return
    setSaving(true)
    try {
      const updated = await updateUnit(tenantId, selected.id, form)
      setUnits((prev) => upsertById(prev, updated))
      setModal(null)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Update failed')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (unit: Unit) => {
    if (!tenantId || !confirm(`Deactivate unit ${unit.unitNumber}? You can add it again later with the same number.`)) return
    setError('')
    try {
      await deleteUnit(tenantId, unit.id)
      setUnits((prev) => removeById(prev, unit.id))
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Delete failed')
    }
  }

  const handleLink = async (e: FormEvent) => {
    e.preventDefault()
    if (!tenantId || !selected || !linkOwnerId) return
    setSaving(true)
    try {
      if (selected.ownerId) {
        await unlinkOwner(tenantId, selected.id)
      }
      const updated = await linkOwner(tenantId, selected.id, linkOwnerId)
      setUnits((prev) => upsertById(prev, updated))
      setModal(null)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Link failed')
    } finally {
      setSaving(false)
    }
  }

  const handleUnlink = async () => {
    if (!tenantId || !selected) return
    setSaving(true)
    try {
      const updated = await unlinkOwner(tenantId, selected.id)
      setUnits((prev) => upsertById(prev, updated))
      setModal(null)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Unlink failed')
    } finally {
      setSaving(false)
    }
  }

  const handlePayment = async (e: FormEvent) => {
    e.preventDefault()
    if (!tenantId || !selected) return
    setPaymentSaving(true)
    setError('')
    try {
      const key = `ui-${selected.id}-${Date.now()}`
      await recordPayment(
        tenantId,
        {
          unitId: selected.id,
          method: paymentForm.method,
          amount: paymentForm.amount,
          txnRef: paymentForm.txnRef || undefined,
          notes: paymentForm.notes || undefined,
          paidBy: paymentForm.paidBy || undefined,
        },
        key,
      )
      await load()
      setModal(null)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Payment failed')
    } finally {
      setPaymentSaving(false)
    }
  }

  const ownerName = (id?: string) =>
    owners.find((o) => o.id === id)?.name ?? (id ? id : '—')

  const displayAmount = (value?: number | null) =>
    value == null ? '—' : formatMoney(Number(value))

  const applyFilter = (e: FormEvent) => {
    e.preventDefault()
    setAppliedFilter(filterInput.trim())
  }

  const clearFilter = () => {
    setFilterInput('')
    setAppliedFilter('')
  }

  if (!tenantId) {
    return (
      <div className="alert alert-error">
        No tenant assigned to your account. Sign up with a valid tenant ID.
      </div>
    )
  }

  return (
    <div className="page-section">
      <div className="toolbar">
        <p className="toolbar-meta">{units.length} unit(s)</p>
        <form className="filter-form" onSubmit={applyFilter}>
          <input
            value={filterInput}
            onChange={(e) => setFilterInput(e.target.value)}
            placeholder='Filter (e.g. unitNumber=="A-101" && active==true)'
          />
          <button type="submit" className="btn btn-sm">Apply</button>
          <button type="button" className="btn btn-sm" onClick={clearFilter}>Clear</button>
        </form>
        <button type="button" className="btn btn-primary" onClick={openCreate}>
          + Add unit
        </button>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <div className="page-center"><div className="spinner" /></div>
      ) : (
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>UNIT</th>
                <th>Profile</th>
                <th>Owner</th>
                <th>Active</th>
                <th>Balance</th>
                <th>Due</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {units.map((u) => (
                <tr key={u.id}>
                  <td><strong>{u.unitNumber}</strong></td>
                  <td>{u.profileCode}</td>
                  <td>{ownerName(u.ownerId)}</td>
                  <td>
                    <span className={`badge ${u.active ? 'badge-success' : 'badge-muted'}`}>
                      {u.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td>{displayAmount(u.unitBalance)}</td>
                  <td>{displayAmount(u.dueAmount)}</td>
                  <td className="actions-cell">
                    <button type="button" className="btn btn-sm" onClick={() => openEdit(u)}>
                      Edit
                    </button>
                    <button type="button" className="btn btn-sm" onClick={() => openLink(u)}>
                      Link owner
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-danger"
                      onClick={() => handleDelete(u)}
                    >
                      Delete
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm"
                      onClick={() => openDetails(u)}
                    >
                      View details
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        title={modal === 'create' ? 'Add unit' : 'Edit unit'}
        open={modal === 'create' || modal === 'edit'}
        onClose={() => setModal(null)}
      >
        <form className="form-stack" onSubmit={modal === 'create' ? handleCreate : handleUpdate}>
          <label>
            Unit number
            <input
              value={form.unitNumber}
              onChange={(e) => setForm({ ...form, unitNumber: e.target.value })}
              required
              placeholder="A-101"
            />
          </label>
          <label>
            Profile code
            <select
              value={form.profileCode}
              onChange={(e) => setForm({ ...form, profileCode: e.target.value })}
            >
              {profileCodes.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </label>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={form.active}
              onChange={(e) => setForm({ ...form, active: e.target.checked })}
            />
            Active
          </label>
          <div className="form-actions">
            <button type="button" className="btn" onClick={() => setModal(null)}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal
        title={`Link owner — ${selected?.unitNumber ?? ''}`}
        open={modal === 'link'}
        onClose={() => setModal(null)}
      >
        <form className="form-stack" onSubmit={handleLink}>
          <label>
            Owner
            <select
              value={linkOwnerId}
              onChange={(e) => setLinkOwnerId(e.target.value)}
              required
            >
              <option value="">Select owner…</option>
              {owners.map((o) => (
                <option key={o.id} value={o.id}>{o.name} ({o.email})</option>
              ))}
            </select>
          </label>
          <div className="form-actions">
            {selected?.ownerId && (
              <button
                type="button"
                className="btn btn-danger"
                onClick={handleUnlink}
                disabled={saving}
              >
                Unlink current
              </button>
            )}
            <button type="button" className="btn" onClick={() => setModal(null)}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={saving || !linkOwnerId}>
              Link
            </button>
          </div>
        </form>
      </Modal>

      <Modal
        title={`Unit details — ${selected?.unitNumber ?? ''}`}
        open={modal === 'details'}
        onClose={() => setModal(null)}
      >
        {selected && (
          <div className="form-stack">
            <div>
              <p className="payment-summary">
                Balance <strong>{formatMoney(Number(selected.unitBalance ?? 0))}</strong>
              </p>
              <p className="payment-summary">
                Due <strong>{formatMoney(Number(selected.dueAmount ?? 0))}</strong>
              </p>
            </div>
            <div className="form-actions">
              <button
                type="button"
                className="btn"
                onClick={() => navigate(`/tenant/invoices?unitId=${encodeURIComponent(selected.id)}`)}
              >
                Show invoices for unit
              </button>
            </div>
            <form className="form-stack" onSubmit={handlePayment}>
              <label>
                Payment method
                <select
                  value={paymentForm.method}
                  onChange={(e) => setPaymentForm({ ...paymentForm, method: e.target.value })}
                >
                  {PAYMENT_METHODS.map((m) => (
                    <option key={m} value={m}>{m}</option>
                  ))}
                </select>
              </label>
              <label>
                Amount
                <input
                  type="number"
                  min={0.01}
                  step={0.01}
                  value={paymentForm.amount}
                  onChange={(e) =>
                    setPaymentForm({ ...paymentForm, amount: parseFloat(e.target.value) })
                  }
                  required
                />
              </label>
              <label>
                Transaction ref
                <input
                  value={paymentForm.txnRef}
                  onChange={(e) => setPaymentForm({ ...paymentForm, txnRef: e.target.value })}
                  placeholder="UPI-2026-0001"
                />
              </label>
              <label>
                Paid by
                <input
                  value={paymentForm.paidBy}
                  onChange={(e) => setPaymentForm({ ...paymentForm, paidBy: e.target.value })}
                  placeholder="Owner name"
                />
              </label>
              <label>
                Notes
                <textarea
                  value={paymentForm.notes}
                  onChange={(e) => setPaymentForm({ ...paymentForm, notes: e.target.value })}
                  rows={2}
                />
              </label>
              <div className="form-actions">
                <button type="button" className="btn" onClick={() => setModal(null)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={paymentSaving}>
                  {paymentSaving ? 'Recording…' : 'Register payment'}
                </button>
              </div>
            </form>
          </div>
        )}
      </Modal>
    </div>
  )
}

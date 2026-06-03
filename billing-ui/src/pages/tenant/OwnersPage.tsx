import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { listUnits } from '../../api/units'
import {
  createOwner,
  deleteOwner,
  linkUnit,
  listOwners,
  unlinkUnit,
  updateOwner,
} from '../../api/owners'
import { Modal } from '../../components/Modal'
import { ApiClientError } from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import type { Owner, Unit } from '../../types'
import { useLoadSequence } from '../../hooks/useLoadSequence'
import {
  mergeListsById,
  removeById,
  upsertById,
  withoutInactiveOwners,
} from '../../utils/listState'

export function OwnersPage() {
  const { tenantId } = useAuth()
  const [owners, setOwners] = useState<Owner[]>([])
  const [units, setUnits] = useState<Unit[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filterInput, setFilterInput] = useState('')
  const [appliedFilter, setAppliedFilter] = useState('')
  const [modal, setModal] = useState<'create' | 'edit' | 'link' | null>(null)
  const [selected, setSelected] = useState<Owner | null>(null)
  const [form, setForm] = useState({
    name: '',
    email: '',
    phone: '+91-',
    status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
  })
  const [linkUnitId, setLinkUnitId] = useState('')
  const [saving, setSaving] = useState(false)
  const { nextLoadId, isLatest } = useLoadSequence()

  const load = useCallback(async () => {
    if (!tenantId) return
    const loadId = nextLoadId()
    setLoading(true)
    setError('')
    try {
      const [o, u] = await Promise.all([
        listOwners(tenantId, appliedFilter || undefined),
        listUnits(tenantId),
      ])
      if (!isLatest(loadId)) return
      setOwners((prev) => {
        if (appliedFilter) {
          // When filtering, show exactly what the server returns (avoid merging in stale local rows).
          return o
        }
        const merged = mergeListsById(o, prev)
        return withoutInactiveOwners(merged)
      })
      setUnits((prev) => mergeListsById(u, prev))
    } catch (err) {
      if (!isLatest(loadId)) return
      setError(err instanceof ApiClientError ? err.message : 'Failed to load owners')
    } finally {
      if (isLatest(loadId)) setLoading(false)
    }
  }, [tenantId, nextLoadId, isLatest, appliedFilter])

  useEffect(() => {
    load()
  }, [load])

  const openCreate = () => {
    setForm({ name: '', email: '', phone: '+91-', status: 'ACTIVE' })
    setModal('create')
  }

  const openEdit = (o: Owner) => {
    setSelected(o)
    setForm({
      name: o.name,
      email: o.email,
      phone: o.phone,
      status: o.status,
    })
    setModal('edit')
  }

  const openLink = (o: Owner) => {
    setSelected(o)
    setLinkUnitId('')
    setModal('link')
  }

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault()
    if (!tenantId) return
    setSaving(true)
    try {
      const created = await createOwner(tenantId, form)
      setOwners((prev) => upsertById(prev, created))
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
      const updated = await updateOwner(tenantId, selected.id, form)
      setOwners((prev) => upsertById(prev, updated))
      setModal(null)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Update failed')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (owner: Owner) => {
    if (!tenantId || !confirm(`Deactivate owner ${owner.name}? You can add them again later with the same email.`)) return
    setError('')
    try {
      await deleteOwner(tenantId, owner.id)
      setOwners((prev) => removeById(prev, owner.id))
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Delete failed')
    }
  }

  const handleLink = async (e: FormEvent) => {
    e.preventDefault()
    if (!tenantId || !selected || !linkUnitId) return
    setSaving(true)
    try {
      const updated = await linkUnit(tenantId, selected.id, linkUnitId)
      setOwners((prev) => upsertById(prev, updated))
      setSelected(updated)
      setModal(null)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Link failed')
    } finally {
      setSaving(false)
    }
  }

  const handleUnlink = async (unitId: string) => {
    if (!tenantId || !selected) return
    setSaving(true)
    try {
      const updated = await unlinkUnit(tenantId, selected.id, unitId)
      setOwners((prev) => upsertById(prev, updated))
      setSelected(updated)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Unlink failed')
    } finally {
      setSaving(false)
    }
  }

  const unitLabel = (id: string) =>
    units.find((u) => u.id === id)?.unitNumber ?? id

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
        <p className="toolbar-meta">{owners.length} owner(s)</p>
        <form className="filter-form" onSubmit={applyFilter}>
          <input
            value={filterInput}
            onChange={(e) => setFilterInput(e.target.value)}
            placeholder='Filter (e.g. status=="ACTIVE" && email=="a@b.com")'
          />
          <button type="submit" className="btn btn-sm">Apply</button>
          <button type="button" className="btn btn-sm" onClick={clearFilter}>Clear</button>
        </form>
        <button type="button" className="btn btn-primary" onClick={openCreate}>
          + Add owner
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
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Status</th>
                <th>Units</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {owners.map((o) => (
                <tr key={o.id}>
                  <td><strong>{o.name}</strong></td>
                  <td>{o.email}</td>
                  <td>{o.phone}</td>
                  <td>
                    <span className={`badge ${o.status === 'ACTIVE' ? 'badge-success' : 'badge-muted'}`}>
                      {o.status}
                    </span>
                  </td>
                  <td>
                    {(o.unitIds ?? []).length
                      ? o.unitIds!.map(unitLabel).join(', ')
                      : '—'}
                  </td>
                  <td className="actions-cell">
                    <button type="button" className="btn btn-sm" onClick={() => openEdit(o)}>
                      Edit
                    </button>
                    <button type="button" className="btn btn-sm" onClick={() => openLink(o)}>
                      Link unit
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-danger"
                      onClick={() => handleDelete(o)}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        title={modal === 'create' ? 'Add owner' : 'Edit owner'}
        open={modal === 'create' || modal === 'edit'}
        onClose={() => setModal(null)}
      >
        <form className="form-stack" onSubmit={modal === 'create' ? handleCreate : handleUpdate}>
          <label>
            Name
            <input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
          </label>
          <label>
            Email
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              required
            />
          </label>
          <label>
            Phone
            <input
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              required
              placeholder="+91-9876543210"
            />
          </label>
          <label>
            Status
            <select
              value={form.status}
              onChange={(e) =>
                setForm({ ...form, status: e.target.value as 'ACTIVE' | 'INACTIVE' })
              }
            >
              <option value="ACTIVE">ACTIVE</option>
              <option value="INACTIVE">INACTIVE</option>
            </select>
          </label>
          <div className="form-actions">
            <button type="button" className="btn" onClick={() => setModal(null)}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>Save</button>
          </div>
        </form>
      </Modal>

      <Modal
        title={`Link unit — ${selected?.name ?? ''}`}
        open={modal === 'link'}
        onClose={() => setModal(null)}
      >
        {selected && (
          <>
            {(selected.unitIds ?? []).length > 0 && (
              <div className="linked-list">
                <p><strong>Linked units</strong></p>
                <ul>
                  {selected.unitIds!.map((id) => (
                    <li key={id}>
                      {unitLabel(id)}
                      <button
                        type="button"
                        className="btn btn-sm btn-danger"
                        onClick={() => handleUnlink(id)}
                        disabled={saving}
                      >
                        Unlink
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <form className="form-stack" onSubmit={handleLink}>
              <label>
                Unit
                <select
                  value={linkUnitId}
                  onChange={(e) => setLinkUnitId(e.target.value)}
                  required
                >
                  <option value="">Select unit…</option>
                  {units
                    .filter((u) => !(selected.unitIds ?? []).includes(u.id))
                    .map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.unitNumber} ({u.profileCode})
                      </option>
                    ))}
                </select>
              </label>
              <div className="form-actions">
                <button type="button" className="btn" onClick={() => setModal(null)}>
                  Close
                </button>
                <button type="submit" className="btn btn-primary" disabled={saving}>
                  Link unit
                </button>
              </div>
            </form>
          </>
        )}
      </Modal>
    </div>
  )
}

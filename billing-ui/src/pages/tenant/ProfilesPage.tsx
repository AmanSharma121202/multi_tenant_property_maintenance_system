import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  createProfile,
  deleteProfile,
  listProfiles,
  updateProfile,
} from '../../api/profiles'
import { Modal } from '../../components/Modal'
import { RefreshButton } from '../../components/RefreshButton'
import { ApiClientError } from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import type { Profile } from '../../types'
import { formatMoney } from '../../utils/format'
import { useLoadSequence } from '../../hooks/useLoadSequence'
import {
  mergeListsById,
  removeById,
  upsertById,
  withoutInactive,
} from '../../utils/listState'

const PROFILE_CODES = ['1BHK', '2BHK', '3BHK', 'VILLA']

export function ProfilesPage() {
  const { tenantId } = useAuth()
  const [profiles, setProfiles] = useState<Profile[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filterInput, setFilterInput] = useState('')
  const [appliedFilter, setAppliedFilter] = useState('')
  const [modal, setModal] = useState<'create' | 'edit' | null>(null)
  const [selected, setSelected] = useState<Profile | null>(null)
  const [form, setForm] = useState({
    code: '2BHK',
    label: '',
    monthlyAmount: 10000,
    active: true,
  })
  const [saving, setSaving] = useState(false)
  const { nextLoadId, isLatest } = useLoadSequence()

  const load = useCallback(async () => {
    if (!tenantId) return
    const loadId = nextLoadId()
    setLoading(true)
    setError('')
    try {
      const data = await listProfiles(tenantId, appliedFilter || undefined)
      if (!isLatest(loadId)) return
      setProfiles((prev) => {
        if (appliedFilter) {
          // When filtering, show exactly what the server returns (avoid merging in stale local rows).
          return data
        }
        const merged = mergeListsById(data, prev)
        return withoutInactive(merged)
      })
    } catch (err) {
      if (!isLatest(loadId)) return
      setError(err instanceof ApiClientError ? err.message : 'Failed to load profiles')
    } finally {
      if (isLatest(loadId)) setLoading(false)
    }
  }, [tenantId, nextLoadId, isLatest, appliedFilter])

  useEffect(() => {
    load()
  }, [load])

  const openCreate = () => {
    setForm({ code: '2BHK', label: '2 BHK', monthlyAmount: 10000, active: true })
    setModal('create')
  }

  const openEdit = (p: Profile) => {
    setSelected(p)
    setForm({
      code: p.code,
      label: p.label,
      monthlyAmount: p.monthlyAmount,
      active: p.active,
    })
    setModal('edit')
  }

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault()
    if (!tenantId) return
    setSaving(true)
    try {
      const created = await createProfile(tenantId, form)
      setProfiles((prev) => upsertById(prev, created))
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
      const updated = await updateProfile(tenantId, selected.id, form)
      setProfiles((prev) => upsertById(prev, updated))
      setModal(null)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Update failed')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (p: Profile) => {
    if (!tenantId || !confirm(`Deactivate profile ${p.code}? You can add it again later with the same code.`)) return
    setError('')
    try {
      await deleteProfile(tenantId, p.id)
      setProfiles((prev) => removeById(prev, p.id))
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Delete failed')
    }
  }

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
        <p className="toolbar-meta">{profiles.length} profile(s)</p>
        <form className="filter-form" onSubmit={applyFilter}>
          <input
            value={filterInput}
            onChange={(e) => setFilterInput(e.target.value)}
            placeholder='Filter (e.g. code=="2BHK" && active==true)'
          />
          <button type="submit" className="btn btn-sm">Apply</button>
          <button type="button" className="btn btn-sm" onClick={clearFilter}>Clear</button>
        </form>
        <div className="toolbar-actions">
          <RefreshButton onClick={() => load()} disabled={loading} />
          <button type="button" className="btn btn-primary" onClick={openCreate}>
            + Add profile
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <div className="page-center"><div className="spinner" /></div>
      ) : (
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Label</th>
                <th>Monthly amount</th>
                <th>Active</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {profiles.map((p) => (
                <tr key={p.id}>
                  <td><strong>{p.code}</strong></td>
                  <td>{p.label}</td>
                  <td>{formatMoney(p.monthlyAmount)}</td>
                  <td>
                    <span className={`badge ${p.active ? 'badge-success' : 'badge-muted'}`}>
                      {p.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td className="actions-cell">
                    <button type="button" className="btn btn-sm" onClick={() => openEdit(p)}>
                      Edit
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-danger"
                      onClick={() => handleDelete(p)}
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
        title={modal === 'create' ? 'Add profile' : 'Edit profile'}
        open={modal === 'create' || modal === 'edit'}
        onClose={() => setModal(null)}
      >
        <form className="form-stack" onSubmit={modal === 'create' ? handleCreate : handleUpdate}>
          <label>
            Code
            <select
              value={form.code}
              onChange={(e) => setForm({ ...form, code: e.target.value })}
              disabled={modal === 'edit'}
            >
              {PROFILE_CODES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </label>
          <label>
            Label
            <input
              value={form.label}
              onChange={(e) => setForm({ ...form, label: e.target.value })}
              required
            />
          </label>
          <label>
            Monthly amount
            <input
              type="number"
              min={1}
              value={form.monthlyAmount}
              onChange={(e) =>
                setForm({ ...form, monthlyAmount: parseFloat(e.target.value) })
              }
              required
            />
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
            <button type="submit" className="btn btn-primary" disabled={saving}>Save</button>
          </div>
        </form>
      </Modal>
    </div>
  )
}

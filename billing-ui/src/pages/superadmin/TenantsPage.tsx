import { useCallback, useEffect, useState } from 'react'
import { createTenant, listTenants, updateTenant } from '../../api/tenants'
import { Modal } from '../../components/Modal'
import { ApiClientError } from '../../api/client'
import type { Tenant } from '../../types'
import { tenantBillingDay, formatOrdinalDay } from '../../utils/format'

const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP']
const LATE_FEE_TYPES = ['PERCENTAGE', 'FIXED', 'NONE']

const emptyCreate = {
  name: '',
  currency: 'INR',
  billing_day: 1,
  lateFeeType: 'PERCENTAGE',
  lateFeeValue: 2,
  address: '',
}

export function TenantsPage() {
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<Tenant | null>(null)
  const [editForm, setEditForm] = useState<Partial<Tenant>>({})
  const [showCreate, setShowCreate] = useState(false)
  const [createForm, setCreateForm] = useState(emptyCreate)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setTenants(await listTenants())
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Failed to load tenants')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const openDetail = (tenant: Tenant) => {
    setSelected(tenant)
    setEditForm({
      name: tenant.name,
      currency: tenant.currency,
      billing_day: tenantBillingDay(tenant),
      lateFeeType: tenant.lateFeeType,
      lateFeeValue: tenant.lateFeeValue,
      address: tenant.address ?? '',
    })
  }

  const handleUpdate = async () => {
    if (!selected) return
    setSaving(true)
    setError('')
    try {
      const updated = await updateTenant(selected.id, {
        name: editForm.name,
        currency: editForm.currency,
        billing_day: editForm.billing_day as number,
        lateFeeType: editForm.lateFeeType,
        lateFeeValue: editForm.lateFeeValue,
        address: editForm.address,
      })
      setTenants((prev) => prev.map((t) => (t.id === updated.id ? updated : t)))
      setSelected(updated)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Update failed')
    } finally {
      setSaving(false)
    }
  }

  const handleCreate = async () => {
    setSaving(true)
    setError('')
    try {
      const created = await createTenant(createForm)
      setTenants((prev) => [...prev, created])
      setShowCreate(false)
      setCreateForm(emptyCreate)
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : 'Create failed')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page-section">
      <div className="toolbar">
        <p className="toolbar-meta">{tenants.length} tenant(s)</p>
        <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + New tenant
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
                <th>ID</th>
                <th>Currency</th>
                <th>Billing day</th>
                <th>Late fee</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {tenants.map((t) => (
                <tr key={t.id}>
                  <td><strong>{t.name}</strong></td>
                  <td><code className="code-sm">{t.id}</code></td>
                  <td>{t.currency}</td>
                  <td>{formatOrdinalDay(tenantBillingDay(t))} of every month</td>
                  <td>{t.lateFeeType} ({t.lateFeeValue})</td>
                  <td>
                    <button type="button" className="btn btn-sm" onClick={() => openDetail(t)}>
                      View / Edit
                    </button>
                  </td>
                </tr>
              ))}
              {tenants.length === 0 && (
                <tr>
                  <td colSpan={6} className="empty-cell">No tenants yet. Create one to get started.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        title={selected ? `Tenant — ${selected.name}` : 'Tenant'}
        open={!!selected}
        onClose={() => setSelected(null)}
        wide
      >
        {selected && (
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault()
              handleUpdate()
            }}
          >
            <label>
              ID
              <input value={selected.id} readOnly className="readonly" />
            </label>
            <label>
              Name
              <input
                value={editForm.name ?? ''}
                onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                required
              />
            </label>
            <label>
              Currency
              <select
                value={editForm.currency ?? 'INR'}
                onChange={(e) => setEditForm({ ...editForm, currency: e.target.value })}
              >
                {CURRENCIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </label>
            <label>
              Billing day (1–31)
              <input
                type="number"
                min={1}
                max={31}
                value={editForm.billing_day ?? 1}
                onChange={(e) => setEditForm({ ...editForm, billing_day: parseInt(e.target.value) || 1 })}
                required
              />
            </label>
            <label>
              Late fee type
              <select
                value={editForm.lateFeeType ?? 'NONE'}
                onChange={(e) => setEditForm({ ...editForm, lateFeeType: e.target.value })}
              >
                {LATE_FEE_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </label>
            <label>
              Late fee value
              <input
                type="number"
                min={0}
                step={0.01}
                value={editForm.lateFeeValue ?? 0}
                onChange={(e) =>
                  setEditForm({ ...editForm, lateFeeValue: parseFloat(e.target.value) })
                }
              />
            </label>
            <label className="span-2">
              Address
              <textarea
                value={editForm.address ?? ''}
                onChange={(e) => setEditForm({ ...editForm, address: e.target.value })}
                rows={2}
              />
            </label>
            <div className="form-actions span-2">
              <button type="button" className="btn" onClick={() => setSelected(null)}>
                Cancel
              </button>
              <button type="submit" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save changes'}
              </button>
            </div>
          </form>
        )}
      </Modal>

      <Modal title="Create tenant" open={showCreate} onClose={() => setShowCreate(false)} wide>
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault()
            handleCreate()
          }}
        >
          <label>
            Society name
            <input
              value={createForm.name}
              onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
              required
              placeholder="Sunrise Residency"
            />
          </label>
          <label>
            Currency
            <select
              value={createForm.currency}
              onChange={(e) => setCreateForm({ ...createForm, currency: e.target.value })}
            >
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </label>
          <label>
            Billing day (1–31)
            <input
              type="number"
              min={1}
              max={31}
              value={createForm.billing_day}
              onChange={(e) => setCreateForm({ ...createForm, billing_day: parseInt(e.target.value) || 1 })}
              required
            />
          </label>
          <label>
            Late fee type
            <select
              value={createForm.lateFeeType}
              onChange={(e) => setCreateForm({ ...createForm, lateFeeType: e.target.value })}
            >
              {LATE_FEE_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </label>
          <label>
            Late fee value
            <input
              type="number"
              min={0}
              step={0.01}
              value={createForm.lateFeeValue}
              onChange={(e) =>
                setCreateForm({ ...createForm, lateFeeValue: parseFloat(e.target.value) })
              }
            />
          </label>
          <label className="span-2">
            Address
            <textarea
              value={createForm.address}
              onChange={(e) => setCreateForm({ ...createForm, address: e.target.value })}
              rows={2}
              placeholder="Tower A, Main Street"
            />
          </label>
          <div className="form-actions span-2">
            <button type="button" className="btn" onClick={() => setShowCreate(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Creating…' : 'Create tenant'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}


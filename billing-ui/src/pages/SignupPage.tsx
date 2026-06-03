import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiClientError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import type { Role } from '../types'

type AccountType = 'superadmin' | 'tenant_admin'

export function SignupPage() {
  const { signup } = useAuth()
  const navigate = useNavigate()
  const [accountType, setAccountType] = useState<AccountType>('tenant_admin')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [tenantId, setTenantId] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const roles: Role[] =
        accountType === 'superadmin' ? ['SUPERADMIN'] : ['TENANT_ADMIN']
      const resolvedTenantId =
        accountType === 'superadmin' ? 'superadmin' : tenantId.trim()

      await signup({
        name,
        email,
        password,
        tenantId: resolvedTenantId,
        roles,
      })
      navigate('/dashboard')
    } catch (err) {
      if (err instanceof ApiClientError && err.status === 409) {
        setError('This email is already registered. Try signing in instead.')
      } else {
        setError(err instanceof ApiClientError ? err.message : 'Signup failed')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card auth-card-wide">
        <div className="auth-brand">
          <span className="brand-icon lg">◈</span>
          <h1>Create account</h1>
          <p>Register as platform admin or society admin</p>
        </div>
        <form onSubmit={handleSubmit} className="auth-form">
          {error && <div className="alert alert-error">{error}</div>}

          <div className="role-toggle">
            <button
              type="button"
              className={accountType === 'superadmin' ? 'active' : ''}
              onClick={() => setAccountType('superadmin')}
            >
              Super Admin
            </button>
            <button
              type="button"
              className={accountType === 'tenant_admin' ? 'active' : ''}
              onClick={() => setAccountType('tenant_admin')}
            >
              Tenant Admin
            </button>
          </div>

          <label>
            Full name
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={100}
              placeholder="Amit Sharma"
            />
          </label>
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              placeholder="admin@example.com"
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
              placeholder="Min. 8 characters"
            />
          </label>

          {accountType === 'tenant_admin' && (
            <label>
              Tenant ID
              <input
                value={tenantId}
                onChange={(e) => setTenantId(e.target.value)}
                required
                placeholder="tenant::uuid-from-superadmin"
              />
              <span className="hint">
                Use the tenant ID created by a super admin (e.g. tenant::abc-123)
              </span>
            </label>
          )}

          <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
            {submitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>
        <p className="auth-footer">
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  )
}

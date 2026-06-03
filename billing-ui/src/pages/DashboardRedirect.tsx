import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function DashboardRedirect() {
  const { user, loading, isSuperAdmin, isTenantAdmin } = useAuth()

  if (loading) {
    return (
      <div className="page-center">
        <div className="spinner" />
      </div>
    )
  }

  if (!user) return <Navigate to="/login" replace />

  if (isSuperAdmin) return <Navigate to="/superadmin/tenants" replace />
  if (isTenantAdmin) return <Navigate to="/tenant/units" replace />

  return (
    <div className="page-center">
      <div className="empty-state">
        <h2>No dashboard for your role</h2>
        <p>Your account has roles: {user.roles.join(', ')}</p>
        <p className="hint">This UI supports SUPERADMIN and TENANT_ADMIN.</p>
      </div>
    </div>
  )
}

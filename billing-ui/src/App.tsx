import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './components/ProtectedRoute'
import { AuthProvider, useAuth } from './context/AuthContext'
import { SuperadminLayout } from './layouts/SuperadminLayout'
import { TenantLayout } from './layouts/TenantLayout'
import { DashboardRedirect } from './pages/DashboardRedirect'
import { LoginPage } from './pages/LoginPage'
import { SignupPage } from './pages/SignupPage'
import { TenantsPage } from './pages/superadmin/TenantsPage'
import { InvoicesPage } from './pages/tenant/InvoicesPage'
import { OwnersPage } from './pages/tenant/OwnersPage'
import { ProfilesPage } from './pages/tenant/ProfilesPage'
import { UnitsPage } from './pages/tenant/UnitsPage'
import { PaymentsPage } from './pages/tenant/PaymentsPage'

function PublicOnly({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <div className="page-center">
        <div className="spinner" />
      </div>
    )
  }
  if (user) return <Navigate to="/dashboard" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route
            path="/login"
            element={
              <PublicOnly>
                <LoginPage />
              </PublicOnly>
            }
          />
          <Route
            path="/signup"
            element={
              <PublicOnly>
                <SignupPage />
              </PublicOnly>
            }
          />
          <Route element={<ProtectedRoute />}>
            <Route path="/dashboard" element={<DashboardRedirect />} />
          </Route>

          <Route element={<ProtectedRoute roles={['SUPERADMIN']} />}>
            <Route path="/superadmin" element={<SuperadminLayout />}>
              <Route index element={<Navigate to="tenants" replace />} />
              <Route path="tenants" element={<TenantsPage />} />
            </Route>
          </Route>

          <Route element={<ProtectedRoute roles={['TENANT_ADMIN']} />}>
            <Route path="/tenant" element={<TenantLayout />}>
              <Route index element={<Navigate to="units" replace />} />
              <Route path="units" element={<UnitsPage />} />
              <Route path="profiles" element={<ProfilesPage />} />
              <Route path="owners" element={<OwnersPage />} />
              <Route path="invoices" element={<InvoicesPage />} />
              <Route path="payments" element={<PaymentsPage />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

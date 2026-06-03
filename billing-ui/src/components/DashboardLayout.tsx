import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

interface NavItem {
  to: string
  label: string
}

interface DashboardLayoutProps {
  title: string
  nav: NavItem[]
}

export function DashboardLayout({ title, nav }: DashboardLayoutProps) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const pageTitle =
    nav.find((item) => location.pathname.startsWith(item.to))?.label ?? title

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="dashboard">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-icon">◈</span>
          <span>Billing</span>
        </div>
        <nav className="sidebar-nav">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="user-chip">
            <strong>{user?.name}</strong>
            <small>{user?.roles.join(', ')}</small>
          </div>
          <button type="button" className="btn btn-ghost btn-sm" onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </aside>
      <main className="main-content">
        <header className="page-header">
          <h1>{pageTitle}</h1>
        </header>
        <Outlet />
      </main>
    </div>
  )
}

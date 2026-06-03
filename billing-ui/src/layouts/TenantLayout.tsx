import { DashboardLayout } from '../components/DashboardLayout'

const nav = [
  { to: '/tenant/profiles', label: 'Profiles' },
  { to: '/tenant/units', label: 'Units' },
  { to: '/tenant/owners', label: 'Owners' },
  { to: '/tenant/invoices', label: 'Invoices' },
  { to: '/tenant/payments', label: 'Payments' },
]

export function TenantLayout() {
  return <DashboardLayout title="Society administration" nav={nav} />
}

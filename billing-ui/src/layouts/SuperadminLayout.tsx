import { DashboardLayout } from '../components/DashboardLayout'

const nav = [{ to: '/superadmin/tenants', label: 'Tenants' }]

export function SuperadminLayout() {
  return <DashboardLayout title="Platform administration" nav={nav} />
}

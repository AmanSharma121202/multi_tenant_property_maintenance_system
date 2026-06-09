export type Role = 'SUPERADMIN' | 'TENANT_ADMIN' | 'CSR' | 'OWNER'

export interface User {
  id: string
  name: string
  email: string
  tenantId: string
  roles: Role[]
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface SignupResponse {
  user: User
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface Tenant {
  id: string
  name: string
  currency: string
  billing_day?: number
  billingDay?: number
  lateFeeType: string
  lateFeeValue: number
  address: string
  status?: 'ACTIVE' | 'INACTIVE'
  tenantId?: string
  createdAt?: string
  updatedAt?: string
}

export interface Unit {
  id: string
  unitNumber: string
  profileCode: string
  ownerId?: string
  active: boolean
  dueAmount?: number
  unitBalance?: number
  unitStartDate?: string
  tenantId?: string
}

export interface Profile {
  id: string
  code: string
  label: string
  monthlyAmount: number
  active: boolean
  tenantId?: string
}

export interface Owner {
  id: string
  name: string
  email: string
  phone: string
  status: 'ACTIVE' | 'INACTIVE'
  unitIds?: string[]
  tenantId?: string
}

export interface Invoice {
  id: string
  unitId: string
  ownerId?: string
  year: number
  month: number
  openingBalance: number
  currentCharges: number
  lateFee: number
  adjustments: number
  paymentsInPeriod: number
  closingBalance: number
  status: 'PAID' | 'DUE' | 'OVERDUE'
  issueDate?: string
  dueDate?: string
  paymentDate?: string
  tenantId?: string
}

export interface Payment {
  id: string
  unitId: string
  ownerId?: string
  method: string
  amount: number
  txnRef?: string
  receivedAt?: string
  notes?: string
  paidBy?: string
  tenantId?: string
}

export interface ApiError {
  code?: string
  message: string
  timestamp?: string
}

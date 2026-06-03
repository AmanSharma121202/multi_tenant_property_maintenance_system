import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { getMe, login as apiLogin, signup as apiSignup } from '../api/auth'
import type { Role, User } from '../types'

interface AuthContextValue {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (data: {
    name: string
    email: string
    password: string
    tenantId: string
    roles: Role[]
  }) => Promise<void>
  logout: () => void
  hasRole: (role: Role) => boolean
  isSuperAdmin: boolean
  isTenantAdmin: boolean
  tenantId: string | null
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const loadUser = useCallback(async () => {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      setUser(null)
      setLoading(false)
      return
    }
    try {
      const me = await getMe()
      setUser(me)
    } catch {
      localStorage.removeItem('accessToken')
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadUser()
  }, [loadUser])

  const login = async (email: string, password: string) => {
    const token = await apiLogin(email, password)
    localStorage.setItem('accessToken', token.accessToken)
    const me = await getMe()
    setUser(me)
  }

  const signup = async (data: {
    name: string
    email: string
    password: string
    tenantId: string
    roles: Role[]
  }) => {
    const result = await apiSignup(data)
    localStorage.setItem('accessToken', result.accessToken)
    setUser(result.user)
  }

  const logout = () => {
    localStorage.removeItem('accessToken')
    setUser(null)
  }

  const hasRole = (role: Role) => user?.roles?.includes(role) ?? false

  const value = useMemo(
    () => ({
      user,
      loading,
      login,
      signup,
      logout,
      hasRole,
      isSuperAdmin: hasRole('SUPERADMIN'),
      isTenantAdmin: hasRole('TENANT_ADMIN'),
      tenantId: user?.tenantId && user.tenantId !== 'superadmin' ? user.tenantId : null,
    }),
    [user, loading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

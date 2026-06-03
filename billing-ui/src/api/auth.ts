import { apiRequest } from './client'
import type { Role, SignupResponse, TokenResponse, User } from '../types'

export function login(email: string, password: string) {
  return apiRequest<TokenResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function signup(data: {
  name: string
  email: string
  password: string
  tenantId: string
  roles: Role[]
}) {
  return apiRequest<SignupResponse>('/auth/signup', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function getMe() {
  return apiRequest<User>('/auth/me')
}

import type { ApiError } from '../types'

const API_BASE = import.meta.env.VITE_API_URL ?? '/api'

export class ApiClientError extends Error {
  status: number
  body?: ApiError

  constructor(status: number, message: string, body?: ApiError) {
    super(message)
    this.status = status
    this.body = body
  }
}

function getToken(): string | null {
  return localStorage.getItem('accessToken')
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers)
  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  })

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    const err = data as ApiError | null
    const fallback =
      response.status === 403
        ? 'Access denied — log out and log in again, or check your tenant access'
        : response.status === 500
          ? 'Server error — restart the billing API after pulling latest changes'
          : `Request failed (${response.status})`
    throw new ApiClientError(
      response.status,
      err?.message ?? fallback,
      err ?? undefined,
    )
  }

  return data as T
}

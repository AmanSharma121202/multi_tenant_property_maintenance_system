import { apiRequest } from './client'
import { tenantResource, tenantResourceId } from './paths'
import type { Profile } from '../types'

const base = (tenantId: string) => tenantResource(tenantId, 'profiles')

export function listProfiles(tenantId: string, filter?: string) {
  const query = filter ? `?filter=${encodeURIComponent(filter)}` : ''
  return apiRequest<Profile[]>(`${base(tenantId)}${query}`)
}

export function createProfile(
  tenantId: string,
  data: { code: string; label: string; monthlyAmount: number; active: boolean },
) {
  return apiRequest<Profile>(base(tenantId), {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateProfile(
  tenantId: string,
  profileId: string,
  data: Partial<{ code: string; label: string; monthlyAmount: number; active: boolean }>,
) {
  return apiRequest<Profile>(tenantResourceId(tenantId, 'profiles', profileId), {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export function deleteProfile(tenantId: string, profileId: string) {
  return apiRequest<void>(tenantResourceId(tenantId, 'profiles', profileId), {
    method: 'DELETE',
  })
}

import api from './client'
import type { IdentityProvider, CreateIdpRequest, UpdateIdpRequest } from '@/types'

export const idpApi = {
  list: async (realmName: string): Promise<IdentityProvider[]> => {
    const response = await api.get<IdentityProvider[]>(`/api/super/realms/${realmName}/idp`)
    return response.data
  },

  get: async (realmName: string, alias: string): Promise<IdentityProvider> => {
    const response = await api.get<IdentityProvider>(`/api/super/realms/${realmName}/idp/${alias}`)
    return response.data
  },

  create: async (realmName: string, request: CreateIdpRequest): Promise<IdentityProvider> => {
    const response = await api.post<IdentityProvider>(`/api/super/realms/${realmName}/idp`, request)
    return response.data
  },

  update: async (realmName: string, alias: string, request: UpdateIdpRequest): Promise<IdentityProvider> => {
    const response = await api.put<IdentityProvider>(`/api/super/realms/${realmName}/idp/${alias}`, request)
    return response.data
  },

  delete: async (realmName: string, alias: string): Promise<void> => {
    await api.delete(`/api/super/realms/${realmName}/idp/${alias}`)
  }
}

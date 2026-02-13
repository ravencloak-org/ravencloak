import api from './client'
import type { Realm, RealmDetails, CreateRealmRequest } from '@/types'

export const realmsApi = {
  list: async (): Promise<Realm[]> => {
    const response = await api.get<Realm[]>('/api/super/realms')
    return response.data
  },

  get: async (name: string): Promise<RealmDetails> => {
    const response = await api.get<RealmDetails>(`/api/super/realms/${name}`)
    return response.data
  },

  create: async (request: CreateRealmRequest): Promise<Realm> => {
    const response = await api.post<Realm>('/api/super/realms', request)
    return response.data
  },

  update: async (name: string, request: Partial<CreateRealmRequest>): Promise<Realm> => {
    const response = await api.put<Realm>(`/api/super/realms/${name}`, request)
    return response.data
  },

  delete: async (name: string): Promise<void> => {
    await api.delete(`/api/super/realms/${name}`)
  },

  enableSpi: async (name: string, apiUrl?: string): Promise<void> => {
    await api.post(`/api/super/realms/${name}/spi`, { apiUrl })
  },

  sync: async (name: string): Promise<void> => {
    await api.post(`/api/super/realms/${name}/sync`)
  },

  syncAll: async (): Promise<void> => {
    await api.post('/api/super/realms/sync')
  }
}

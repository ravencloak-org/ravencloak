import api from './client'
import type { Client, CreateClientRequest, CreateApplicationRequest, UpdateClientRequest, ClientDetailResponse, ClientSecretResponse, ApplicationResponse } from '@/types'

export const clientsApi = {
  list: async (realmName: string): Promise<Client[]> => {
    const response = await api.get<Client[]>(`/api/super/realms/${realmName}/clients`)
    return response.data
  },

  get: async (realmName: string, clientId: string): Promise<ClientDetailResponse> => {
    const response = await api.get<ClientDetailResponse>(`/api/super/realms/${realmName}/clients/${clientId}`)
    return response.data
  },

  create: async (realmName: string, request: CreateClientRequest): Promise<Client> => {
    const response = await api.post<Client>(`/api/super/realms/${realmName}/clients`, request)
    return response.data
  },

  update: async (realmName: string, clientId: string, request: UpdateClientRequest): Promise<Client> => {
    const response = await api.put<Client>(`/api/super/realms/${realmName}/clients/${clientId}`, request)
    return response.data
  },

  delete: async (realmName: string, clientId: string): Promise<void> => {
    await api.delete(`/api/super/realms/${realmName}/clients/${clientId}`)
  },

  getSecret: async (realmName: string, clientId: string): Promise<ClientSecretResponse> => {
    const response = await api.get<ClientSecretResponse>(`/api/super/realms/${realmName}/clients/${clientId}/secret`)
    return response.data
  },

  regenerateSecret: async (realmName: string, clientId: string): Promise<ClientSecretResponse> => {
    const response = await api.post<ClientSecretResponse>(`/api/super/realms/${realmName}/clients/${clientId}/secret`)
    return response.data
  },

  createApplication: async (realmName: string, request: CreateApplicationRequest): Promise<ApplicationResponse> => {
    const response = await api.post<ApplicationResponse>(`/api/super/realms/${realmName}/clients/applications`, request)
    return response.data
  }
}

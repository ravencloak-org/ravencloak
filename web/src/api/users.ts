import api from './client'
import type { User, RealmUser, RealmUserDetail, CreateRealmUserRequest, UpdateRealmUserRequest, AssignClientsRequest } from '@/types'

export const usersApi = {
  getCurrentUser: async (): Promise<User> => {
    const response = await api.get<User>('/auth/super/me')
    return response.data
  },

  // Realm user management
  list: async (realmName: string): Promise<RealmUser[]> => {
    const response = await api.get<RealmUser[]>(`/api/super/realms/${realmName}/users`)
    return response.data
  },

  get: async (realmName: string, userId: string): Promise<RealmUserDetail> => {
    const response = await api.get<RealmUserDetail>(`/api/super/realms/${realmName}/users/${userId}`)
    return response.data
  },

  getByEmail: async (realmName: string, email: string): Promise<RealmUserDetail> => {
    const response = await api.get<RealmUserDetail>(`/api/super/realms/${realmName}/users/by-email/${encodeURIComponent(email)}`)
    return response.data
  },

  create: async (realmName: string, request: CreateRealmUserRequest): Promise<RealmUserDetail> => {
    const response = await api.post<RealmUserDetail>(`/api/super/realms/${realmName}/users`, request)
    return response.data
  },

  update: async (realmName: string, userId: string, request: UpdateRealmUserRequest): Promise<RealmUserDetail> => {
    const response = await api.put<RealmUserDetail>(`/api/super/realms/${realmName}/users/${userId}`, request)
    return response.data
  },

  delete: async (realmName: string, userId: string): Promise<void> => {
    await api.delete(`/api/super/realms/${realmName}/users/${userId}`)
  },

  assignClients: async (realmName: string, userId: string, request: AssignClientsRequest): Promise<RealmUserDetail> => {
    const response = await api.post<RealmUserDetail>(`/api/super/realms/${realmName}/users/${userId}/clients`, request)
    return response.data
  },

  removeFromClient: async (realmName: string, userId: string, clientId: string): Promise<RealmUserDetail> => {
    const response = await api.delete<RealmUserDetail>(`/api/super/realms/${realmName}/users/${userId}/clients/${clientId}`)
    return response.data
  }
}

import api from './client'
import type { Role, CreateRoleRequest, UpdateRoleRequest } from '@/types'

export const rolesApi = {
  listRealmRoles: async (realmName: string): Promise<Role[]> => {
    const response = await api.get<Role[]>(`/api/super/realms/${realmName}/roles`)
    return response.data
  },

  listClientRoles: async (realmName: string, clientId: string): Promise<Role[]> => {
    const response = await api.get<Role[]>(`/api/super/realms/${realmName}/clients/${clientId}/roles`)
    return response.data
  },

  getRealmRole: async (realmName: string, roleName: string): Promise<Role> => {
    const response = await api.get<Role>(`/api/super/realms/${realmName}/roles/${roleName}`)
    return response.data
  },

  createRealmRole: async (realmName: string, request: CreateRoleRequest): Promise<Role> => {
    const response = await api.post<Role>(`/api/super/realms/${realmName}/roles`, request)
    return response.data
  },

  createClientRole: async (realmName: string, clientId: string, request: CreateRoleRequest): Promise<Role> => {
    const response = await api.post<Role>(`/api/super/realms/${realmName}/clients/${clientId}/roles`, request)
    return response.data
  },

  updateRealmRole: async (realmName: string, roleName: string, request: UpdateRoleRequest): Promise<Role> => {
    const response = await api.put<Role>(`/api/super/realms/${realmName}/roles/${roleName}`, request)
    return response.data
  },

  deleteRealmRole: async (realmName: string, roleName: string): Promise<void> => {
    await api.delete(`/api/super/realms/${realmName}/roles/${roleName}`)
  },

  deleteClientRole: async (realmName: string, clientId: string, roleName: string): Promise<void> => {
    await api.delete(`/api/super/realms/${realmName}/clients/${clientId}/roles/${roleName}`)
  },

  addCompositeRole: async (realmName: string, parentRoleName: string, childRoleName: string): Promise<void> => {
    await api.post(`/api/super/realms/${realmName}/roles/${parentRoleName}/composites`, { roleName: childRoleName })
  },

  removeCompositeRole: async (realmName: string, parentRoleName: string, childRoleName: string): Promise<void> => {
    await api.delete(`/api/super/realms/${realmName}/roles/${parentRoleName}/composites/${childRoleName}`)
  }
}

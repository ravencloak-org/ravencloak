import api from './client'
import type { Group, CreateGroupRequest, UpdateGroupRequest } from '@/types'

export const groupsApi = {
  list: async (realmName: string): Promise<Group[]> => {
    const response = await api.get<Group[]>(`/api/super/realms/${realmName}/groups`)
    return response.data
  },

  get: async (realmName: string, groupId: string): Promise<Group> => {
    const response = await api.get<Group>(`/api/super/realms/${realmName}/groups/${groupId}`)
    return response.data
  },

  create: async (realmName: string, request: CreateGroupRequest): Promise<Group> => {
    const response = await api.post<Group>(`/api/super/realms/${realmName}/groups`, request)
    return response.data
  },

  createSubgroup: async (realmName: string, parentGroupId: string, request: CreateGroupRequest): Promise<Group> => {
    const response = await api.post<Group>(`/api/super/realms/${realmName}/groups/${parentGroupId}/children`, request)
    return response.data
  },

  update: async (realmName: string, groupId: string, request: UpdateGroupRequest): Promise<Group> => {
    const response = await api.put<Group>(`/api/super/realms/${realmName}/groups/${groupId}`, request)
    return response.data
  },

  delete: async (realmName: string, groupId: string): Promise<void> => {
    await api.delete(`/api/super/realms/${realmName}/groups/${groupId}`)
  },

  assignRoles: async (realmName: string, groupId: string, roleNames: string[]): Promise<void> => {
    await api.put(`/api/super/realms/${realmName}/groups/${groupId}/roles`, { roles: roleNames })
  },

  removeRoles: async (realmName: string, groupId: string, roleNames: string[]): Promise<void> => {
    await api.delete(`/api/super/realms/${realmName}/groups/${groupId}/roles`, { data: { roles: roleNames } })
  }
}

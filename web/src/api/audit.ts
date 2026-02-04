import api from './client'

export interface AuditLog {
  id: string
  actorKeycloakId: string
  actorEmail?: string
  actorDisplayName?: string
  actorIssuer?: string
  actionType: 'CREATE' | 'UPDATE' | 'DELETE'
  entityType: 'CLIENT' | 'REALM' | 'ROLE' | 'GROUP' | 'IDP' | 'USER'
  entityId: string
  entityKeycloakId?: string
  entityName: string
  realmName: string
  realmId?: string
  beforeState?: Record<string, unknown>
  afterState?: Record<string, unknown>
  changedFields?: string[]
  reverted: boolean
  revertedAt?: string
  revertedByKeycloakId?: string
  revertReason?: string
  revertOfActionId?: string
  createdAt: string
  canRevert: boolean
}

export interface AuditPageResponse {
  content: AuditLog[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface RevertRequest {
  reason: string
}

export interface RevertResponse {
  success: boolean
  message: string
  newActionId?: string
}

export const auditApi = {
  getMyActions: async (page: number = 0, size: number = 20): Promise<AuditPageResponse> => {
    const response = await api.get<AuditPageResponse>('/api/audit/my-actions', {
      params: { page, size }
    })
    return response.data
  },

  getRealmAudit: async (realmName: string, page: number = 0, size: number = 20): Promise<AuditPageResponse> => {
    const response = await api.get<AuditPageResponse>(`/api/super/realms/${realmName}/audit`, {
      params: { page, size }
    })
    return response.data
  },

  getAllAudit: async (page: number = 0, size: number = 20): Promise<AuditPageResponse> => {
    const response = await api.get<AuditPageResponse>('/api/super/audit', {
      params: { page, size }
    })
    return response.data
  },

  getEntityAudit: async (
    realmName: string,
    entityType: string,
    entityId: string,
    page: number = 0,
    size: number = 20
  ): Promise<AuditPageResponse> => {
    const response = await api.get<AuditPageResponse>(
      `/api/super/realms/${realmName}/audit/entities/${entityType}/${entityId}`,
      { params: { page, size } }
    )
    return response.data
  },

  canRevert: async (realmName: string, actionId: string): Promise<boolean> => {
    const response = await api.get<{ canRevert: boolean }>(
      `/api/super/realms/${realmName}/audit/${actionId}/can-revert`
    )
    return response.data.canRevert
  },

  revertAction: async (realmName: string, actionId: string, reason: string): Promise<RevertResponse> => {
    const response = await api.post<RevertResponse>(
      `/api/super/realms/${realmName}/audit/${actionId}/revert`,
      { reason }
    )
    return response.data
  }
}

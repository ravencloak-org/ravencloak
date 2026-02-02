export interface User {
  id: string
  email: string
  displayName?: string
  firstName?: string
  lastName?: string
  roles: string[]
}

export interface Realm {
  id: string
  name: string
  displayName?: string
  enabled: boolean
  spiEnabled: boolean
  createdAt: string
  updatedAt: string
}

export interface RealmDetails extends Realm {
  clients: Client[]
  roles: Role[]
  groups: Group[]
}

export interface Client {
  id: string
  clientId: string
  name?: string
  description?: string
  enabled: boolean
  publicClient: boolean
  standardFlowEnabled: boolean
  directAccessGrantsEnabled: boolean
  serviceAccountsEnabled: boolean
}

export interface Role {
  id: string
  name: string
  description?: string
  composite: boolean
  clientRole: boolean
  containerId: string
}

export interface Group {
  id: string
  name: string
  path: string
  parentId?: string
  subGroups?: Group[]
}

export interface CreateRealmRequest {
  name: string
  displayName?: string
  enableSpi: boolean
}

export interface ApiError {
  message: string
  status: number
  timestamp: string
}

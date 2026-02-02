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
  realmName: string
  displayName?: string
  enabled: boolean
  spiEnabled: boolean
  accountId?: string
  createdAt: string
}

export interface RealmDetails extends Realm {
  spiApiUrl?: string
  attributes?: Record<string, unknown>
  clients: Client[]
  roles: Role[]
  groups: Group[]
  userStorageProviders?: UserStorageProvider[]
  syncedAt: string
}

export interface UserStorageProvider {
  id: string
  name: string
  providerId: string
  priority: number
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
  realmName: string
  displayName?: string
  enableUserStorageSpi: boolean
}

export interface ApiError {
  message: string
  status: number
  timestamp: string
}

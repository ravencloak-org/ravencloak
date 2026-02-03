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
  standardFlowEnabled?: boolean
  directAccessGrantsEnabled?: boolean
  serviceAccountsEnabled?: boolean
}

export interface ClientDetailResponse extends Client {
  rootUrl?: string
  baseUrl?: string
  redirectUris: string[]
  webOrigins: string[]
  pairedClientId?: string
  pairedClientClientId?: string
  createdAt: string
}

export type ApplicationType = 'FRONTEND_ONLY' | 'BACKEND_ONLY' | 'FULL_STACK'

export interface CreateApplicationRequest {
  applicationName: string
  displayName?: string
  description?: string
  applicationType: ApplicationType
  rootUrl?: string
  baseUrl?: string
  redirectUris?: string[]
  webOrigins?: string[]
}

export interface ApplicationResponse {
  frontendClient?: ClientDetailResponse
  backendClient?: ClientDetailResponse
}

export interface CreateClientRequest {
  clientId: string
  name?: string
  description?: string
  publicClient?: boolean
  standardFlowEnabled?: boolean
  directAccessGrantsEnabled?: boolean
  serviceAccountsEnabled?: boolean
  rootUrl?: string
  baseUrl?: string
  redirectUris?: string[]
  webOrigins?: string[]
}

export interface UpdateClientRequest {
  name?: string
  description?: string
  enabled?: boolean
  publicClient?: boolean
  standardFlowEnabled?: boolean
  directAccessGrantsEnabled?: boolean
  serviceAccountsEnabled?: boolean
  rootUrl?: string
  baseUrl?: string
  redirectUris?: string[]
  webOrigins?: string[]
}

export interface ClientSecretResponse {
  secret: string
}

export interface Role {
  id: string
  name: string
  description?: string
  composite: boolean
  clientRole?: boolean
  containerId?: string
}

export interface CreateRoleRequest {
  name: string
  description?: string
}

export interface UpdateRoleRequest {
  description?: string
}

export interface Group {
  id: string
  name: string
  path: string
  parentId?: string
  subGroups?: Group[]
  attributes?: Record<string, string[]>
}

export interface CreateGroupRequest {
  name: string
  attributes?: Record<string, string[]>
}

export interface UpdateGroupRequest {
  name?: string
  attributes?: Record<string, string[]>
}

export interface IdentityProvider {
  alias: string
  displayName?: string
  providerId: string
  enabled: boolean
  trustEmail: boolean
  config: Record<string, string>
}

export interface CreateIdpRequest {
  alias: string
  displayName?: string
  providerId: string
  enabled?: boolean
  trustEmail?: boolean
  config?: Record<string, string>
}

export interface UpdateIdpRequest {
  displayName?: string
  enabled?: boolean
  trustEmail?: boolean
  config?: Record<string, string>
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

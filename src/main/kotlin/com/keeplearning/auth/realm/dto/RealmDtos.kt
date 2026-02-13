package com.keeplearning.auth.realm.dto

import java.time.Instant
import java.util.UUID

data class CreateRealmRequest(
    val realmName: String,
    val displayName: String? = null,
    val accountId: UUID? = null,
    val enableUserStorageSpi: Boolean = false,
    val spiApiUrl: String? = null,
    val attributes: Map<String, String>? = null,
    val defaultRoles: List<String>? = null,
    val defaultClients: List<DefaultClientRequest>? = null
)

data class DefaultClientRequest(
    val clientId: String,
    val name: String? = null,
    val publicClient: Boolean = true,
    val redirectUris: List<String>? = null,
    val webOrigins: List<String>? = null
)

data class UpdateRealmRequest(
    val displayName: String? = null,
    val enabled: Boolean? = null,
    val spiEnabled: Boolean? = null,
    val spiApiUrl: String? = null,
    val attributes: Map<String, String>? = null
)

data class EnableSpiRequest(
    val apiUrl: String? = null
)

data class RealmResponse(
    val id: UUID,
    val realmName: String,
    val displayName: String?,
    val enabled: Boolean,
    val spiEnabled: Boolean,
    val accountId: UUID?,
    val createdAt: Instant
)

data class RealmDetailResponse(
    val id: UUID,
    val realmName: String,
    val displayName: String?,
    val enabled: Boolean,
    val spiEnabled: Boolean,
    val spiApiUrl: String?,
    val accountId: UUID?,
    val attributes: Map<String, Any>?,
    val clients: List<ClientResponse>,
    val roles: List<RoleResponse>,
    val groups: List<GroupResponse>,
    val userStorageProviders: List<UserStorageProviderResponse>,
    val createdAt: Instant,
    val syncedAt: Instant
)

data class ClientResponse(
    val id: UUID,
    val clientId: String,
    val name: String?,
    val enabled: Boolean,
    val publicClient: Boolean
)

data class RoleResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val composite: Boolean
)

data class GroupResponse(
    val id: UUID,
    val name: String,
    val path: String
)

data class UserStorageProviderResponse(
    val id: UUID,
    val name: String,
    val providerId: String,
    val priority: Int
)

data class SyncResponse(
    val realmName: String,
    val clientsProcessed: Int,
    val rolesProcessed: Int,
    val groupsProcessed: Int,
    val userStorageProvidersProcessed: Int,
    val success: Boolean
)

data class BulkSyncResponse(
    val totalProcessed: Int,
    val imported: Int,
    val updated: Int,
    val failed: Int,
    val results: List<SyncResponse>
)

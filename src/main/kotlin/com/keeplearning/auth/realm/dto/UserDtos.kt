package com.keeplearning.auth.realm.dto

import java.time.Instant
import java.util.UUID

data class RealmUserResponse(
    val id: UUID,
    val keycloakUserId: String,
    val email: String,
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
    val phone: String?,
    val jobTitle: String?,
    val department: String?,
    val avatarUrl: String?,
    val status: String,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val authorizedClients: List<AuthorizedClientInfo> = emptyList()
)

data class AuthorizedClientInfo(
    val clientId: UUID,
    val clientName: String,
    val assignedAt: Instant
)

data class RealmUserDetailResponse(
    val id: UUID,
    val keycloakUserId: String,
    val email: String,
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
    val phone: String?,
    val bio: String?,
    val jobTitle: String?,
    val department: String?,
    val avatarUrl: String?,
    val status: String,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val authorizedClients: List<AuthorizedClientDetail> = emptyList()
)

data class AuthorizedClientDetail(
    val clientId: UUID,
    val clientIdName: String,
    val clientDisplayName: String?,
    val publicClient: Boolean,
    val assignedAt: Instant,
    val assignedBy: String?
)

data class CreateRealmUserRequest(
    val email: String,
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val jobTitle: String? = null,
    val department: String? = null,
    val clientIds: List<UUID> = emptyList()
)

data class UpdateRealmUserRequest(
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    val jobTitle: String? = null,
    val department: String? = null,
    val status: String? = null
)

data class AssignClientsRequest(
    val clientIds: List<UUID>
)

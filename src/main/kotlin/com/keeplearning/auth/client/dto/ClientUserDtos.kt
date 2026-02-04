package com.keeplearning.auth.client.dto

import java.time.Instant
import java.util.UUID

data class UserRef(
    val email: String,
    val keycloakId: String? = null
)

data class AddUsersRequest(
    val users: List<UserRef>
)

data class RemoveUsersRequest(
    val emails: List<String>
)

data class ClientUserResponse(
    val id: UUID,
    val email: String,
    val keycloakId: String?,
    val assignedAt: Instant,
    val assignedBy: String?
)

data class AuthorizationCheckResponse(
    val authorized: Boolean,
    val email: String,
    val clientId: String
)

data class AddUsersResponse(
    val added: List<String>,
    val alreadyExists: List<String>,
    val failed: List<String>
)

data class RemoveUsersResponse(
    val removed: List<String>,
    val notFound: List<String>
)

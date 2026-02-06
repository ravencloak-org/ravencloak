package com.keeplearning.auth.client.dto

import java.time.Instant
import java.util.UUID

data class UserRef(
    val email: String,
    val keycloakId: String? = null
)

data class OnboardUserRequest(
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val phone: String? = null,
    val jobTitle: String? = null,
    val department: String? = null,
    val roles: List<String> = emptyList()
)

data class OnboardUsersRequest(
    val users: List<OnboardUserRequest>
)

data class OnboardUserResult(
    val email: String,
    val status: OnboardStatus,
    val userId: UUID? = null,
    val assignedRoles: List<String> = emptyList(),
    val error: String? = null
)

enum class OnboardStatus {
    CREATED,
    ALREADY_EXISTS,
    FAILED
}

data class OnboardUsersResponse(
    val results: List<OnboardUserResult>,
    val summary: OnboardSummary
)

data class OnboardSummary(
    val total: Int,
    val created: Int,
    val alreadyExists: Int,
    val failed: Int
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

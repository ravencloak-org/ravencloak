package com.keeplearning.auth.client.dto

import java.time.Instant
import java.util.UUID

data class CreateClientRoleRequest(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val isDefault: Boolean = false
)

data class UpdateClientRoleRequest(
    val displayName: String? = null,
    val description: String? = null,
    val isDefault: Boolean? = null
)

data class ClientRoleResponse(
    val id: UUID,
    val name: String,
    val displayName: String?,
    val description: String?,
    val isDefault: Boolean,
    val createdAt: Instant
)

package com.keeplearning.auth.realm.dto

import java.util.UUID

data class CreateRoleRequest(
    val name: String,
    val description: String? = null
)

data class UpdateRoleRequest(
    val description: String? = null
)

data class RoleDetailResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val composite: Boolean,
    val clientRole: Boolean,
    val containerId: String?
)

data class CompositeRoleRequest(
    val roleName: String
)

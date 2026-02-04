package com.keeplearning.auth.realm.dto

import java.util.UUID

data class CreateGroupRequest(
    val name: String,
    val attributes: Map<String, List<String>>? = null
)

data class UpdateGroupRequest(
    val name: String? = null,
    val attributes: Map<String, List<String>>? = null
)

data class GroupDetailResponse(
    val id: UUID,
    val name: String,
    val path: String,
    val parentId: UUID?,
    val subGroups: List<GroupDetailResponse>,
    val attributes: Map<String, List<String>>?
)

data class GroupRolesRequest(
    val roles: List<String>
)

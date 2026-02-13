package com.keeplearning.auth.keycloak.client.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserRoleMappingResponse(
    val clientRoles: List<RoleRepresentation>,
    val availableClientRoles: List<RoleRepresentation>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssignClientRolesRequest(
    val clientId: String,
    val roles: List<String>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserAttributeResponse(
    val attributes: Map<String, List<String>>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SetUserAttributeRequest(
    val values: List<String>
)

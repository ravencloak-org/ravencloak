package com.keeplearning.auth.keycloak.service

import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.RoleRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Service for managing user roles and approval scopes via email lookup.
 * Wraps KeycloakUserManagementService with email-based API for ECS integration.
 */
@Service
class AccountUserRoleService(
    private val keycloakAdminClient: KeycloakAdminClient
) {
    private val logger = LoggerFactory.getLogger(AccountUserRoleService::class.java)

    companion object {
        private const val DEFAULT_REALM = "kos"
        private const val ECS_CLIENT_ID = "ecs"
    }

    suspend fun getUserRolesByEmail(email: String): Map<String, Any> {
        logger.debug("Getting roles for user with email: $email")

        // Find user in Keycloak by email
        val user = keycloakAdminClient.getUserByEmail(DEFAULT_REALM, email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: $email")

        val userId = user.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User ID not available")

        // Get ECS client UUID
        val ecsClient = keycloakAdminClient.getClientByClientId(DEFAULT_REALM, ECS_CLIENT_ID)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "ECS client not found")

        val clientUuid = ecsClient.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Client UUID not available")

        // Get user's client roles
        val clientRoles = keycloakAdminClient.getUserClientRoleMappings(DEFAULT_REALM, userId, clientUuid)
        val roleNames = clientRoles.map { it.name }

        // Get user's approval_scopes attribute
        val approvalScopes = user.attributes?.get("approval_scopes") ?: emptyList()

        return mapOf(
            "roles" to roleNames,
            "approval_scopes" to approvalScopes
        )
    }

    suspend fun updateUserRolesByEmail(
        email: String,
        roles: List<String>,
        approvalScopes: List<String>
    ): Map<String, Any> {
        logger.info("Updating roles for user: $email - roles: $roles, approvalScopes: $approvalScopes")

        // Find user in Keycloak by email
        val user = keycloakAdminClient.getUserByEmail(DEFAULT_REALM, email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: $email")

        val userId = user.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User ID not available")

        // Get ECS client UUID
        val ecsClient = keycloakAdminClient.getClientByClientId(DEFAULT_REALM, ECS_CLIENT_ID)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "ECS client not found")

        val clientUuid = ecsClient.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Client UUID not available")

        // Get all available client roles
        val allClientRoles = keycloakAdminClient.getClientRoles(DEFAULT_REALM, clientUuid)

        // Get current role assignments
        val currentRoles = keycloakAdminClient.getUserClientRoleMappings(DEFAULT_REALM, userId, clientUuid)
        val currentRoleNames = currentRoles.map { it.name }.toSet()

        // Determine roles to add and remove
        val rolesToAdd = roles.filter { it !in currentRoleNames }
        val rolesToRemove = currentRoleNames.filter { it !in roles }

        // Remove roles no longer assigned
        if (rolesToRemove.isNotEmpty()) {
            val roleReps = rolesToRemove.mapNotNull { roleName ->
                allClientRoles.find { it.name == roleName }
            }
            if (roleReps.isNotEmpty()) {
                keycloakAdminClient.removeUserClientRoleMappings(DEFAULT_REALM, userId, clientUuid, roleReps)
                logger.info("Removed roles $rolesToRemove from user $email")
            }
        }

        // Add new roles
        if (rolesToAdd.isNotEmpty()) {
            val roleReps = rolesToAdd.mapNotNull { roleName ->
                allClientRoles.find { it.name == roleName }
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found for ECS client")
            }
            keycloakAdminClient.addUserClientRoleMappings(DEFAULT_REALM, userId, clientUuid, roleReps)
            logger.info("Added roles $rolesToAdd to user $email")
        }

        // Update approval_scopes attribute
        val updatedAttributes = (user.attributes ?: emptyMap()).toMutableMap()
        if (approvalScopes.isNotEmpty()) {
            updatedAttributes["approval_scopes"] = approvalScopes
        } else {
            updatedAttributes.remove("approval_scopes")
        }

        val updatedUser = user.copy(attributes = updatedAttributes)
        keycloakAdminClient.updateUser(DEFAULT_REALM, userId, updatedUser)
        logger.info("Updated approval_scopes for user $email: $approvalScopes")

        return mapOf(
            "roles" to roles,
            "approval_scopes" to approvalScopes
        )
    }
}

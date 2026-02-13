package com.keeplearning.auth.keycloak.service

import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class KeycloakUserManagementService(
    private val keycloakAdminClient: KeycloakAdminClient
) {
    private val logger = LoggerFactory.getLogger(KeycloakUserManagementService::class.java)

    suspend fun getUserClientRoles(
        realmName: String,
        userId: String,
        clientId: String
    ): UserRoleMappingResponse {
        val clientRep = keycloakAdminClient.getClientByClientId(realmName, clientId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found in realm '$realmName'")
        val clientUuid = clientRep.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Client UUID not available")

        val assignedRoles = keycloakAdminClient.getUserClientRoleMappings(realmName, userId, clientUuid)
        val allClientRoles = keycloakAdminClient.getClientRoles(realmName, clientUuid)
        val assignedRoleNames = assignedRoles.map { it.name }.toSet()
        val availableRoles = allClientRoles.filter { it.name !in assignedRoleNames }

        return UserRoleMappingResponse(
            clientRoles = assignedRoles,
            availableClientRoles = availableRoles
        )
    }

    suspend fun assignClientRoles(
        realmName: String,
        userId: String,
        request: AssignClientRolesRequest
    ) {
        val clientRep = keycloakAdminClient.getClientByClientId(realmName, request.clientId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '${request.clientId}' not found in realm '$realmName'")
        val clientUuid = clientRep.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Client UUID not available")

        val allClientRoles = keycloakAdminClient.getClientRoles(realmName, clientUuid)
        val rolesToAssign = request.roles.map { roleName ->
            allClientRoles.find { it.name == roleName }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found for client '${request.clientId}'")
        }

        keycloakAdminClient.addUserClientRoleMappings(realmName, userId, clientUuid, rolesToAssign)
        logger.info("Assigned roles ${request.roles} to user $userId for client ${request.clientId} in realm $realmName")
    }

    suspend fun removeClientRoles(
        realmName: String,
        userId: String,
        request: AssignClientRolesRequest
    ) {
        val clientRep = keycloakAdminClient.getClientByClientId(realmName, request.clientId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '${request.clientId}' not found in realm '$realmName'")
        val clientUuid = clientRep.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Client UUID not available")

        val allClientRoles = keycloakAdminClient.getClientRoles(realmName, clientUuid)
        val rolesToRemove = request.roles.map { roleName ->
            allClientRoles.find { it.name == roleName }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found for client '${request.clientId}'")
        }

        keycloakAdminClient.removeUserClientRoleMappings(realmName, userId, clientUuid, rolesToRemove)
        logger.info("Removed roles ${request.roles} from user $userId for client ${request.clientId} in realm $realmName")
    }

    suspend fun getUserAttributes(
        realmName: String,
        userId: String
    ): UserAttributeResponse {
        val user = keycloakAdminClient.getUser(realmName, userId)
        return UserAttributeResponse(
            attributes = user.attributes ?: emptyMap()
        )
    }

    suspend fun setUserAttribute(
        realmName: String,
        userId: String,
        attributeName: String,
        values: List<String>
    ) {
        val user = keycloakAdminClient.getUser(realmName, userId)
        val updatedAttributes = (user.attributes ?: emptyMap()).toMutableMap()
        updatedAttributes[attributeName] = values
        val updatedUser = user.copy(attributes = updatedAttributes)
        keycloakAdminClient.updateUser(realmName, userId, updatedUser)
        logger.info("Set attribute '$attributeName' for user $userId in realm $realmName")
    }

    suspend fun removeUserAttribute(
        realmName: String,
        userId: String,
        attributeName: String
    ) {
        val user = keycloakAdminClient.getUser(realmName, userId)
        val updatedAttributes = (user.attributes ?: emptyMap()).toMutableMap()
        updatedAttributes.remove(attributeName)
        val updatedUser = user.copy(attributes = updatedAttributes)
        keycloakAdminClient.updateUser(realmName, userId, updatedUser)
        logger.info("Removed attribute '$attributeName' from user $userId in realm $realmName")
    }
}

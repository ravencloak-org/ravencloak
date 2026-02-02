package com.keeplearning.auth.realm.service

import com.keeplearning.auth.domain.entity.KcRole
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.KcRoleRepository
import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.RoleRepresentation
import com.keeplearning.auth.realm.dto.*
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class RoleService(
    private val keycloakClient: KeycloakAdminClient,
    private val roleRepository: KcRoleRepository,
    private val realmRepository: KcRealmRepository,
    private val clientRepository: KcClientRepository
) {
    private val logger = LoggerFactory.getLogger(RoleService::class.java)

    suspend fun createRealmRole(realmName: String, request: CreateRoleRequest): RoleResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Create in Keycloak
        val roleRep = RoleRepresentation(
            name = request.name,
            description = request.description
        )
        keycloakClient.createRealmRole(realmName, roleRep)

        // Get the created role to obtain its ID
        val createdRole = keycloakClient.getRealmRole(realmName, request.name)

        // Save to local database
        val role = roleRepository.save(
            KcRole(
                realmId = realm.id!!,
                clientId = null,
                name = request.name,
                description = request.description,
                composite = false,
                keycloakId = createdRole.id ?: request.name
            )
        ).awaitSingle()

        logger.info("Created realm role: ${request.name} in realm: $realmName")
        return role.toResponse()
    }

    suspend fun createClientRole(realmName: String, clientId: String, request: CreateRoleRequest): RoleResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val client = clientRepository.findByRealmIdAndClientId(realm.id!!, clientId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found")

        // Create in Keycloak
        val roleRep = RoleRepresentation(
            name = request.name,
            description = request.description,
            clientRole = true
        )
        keycloakClient.createClientRole(realmName, client.keycloakId, roleRep)

        // Save to local database
        val role = roleRepository.save(
            KcRole(
                realmId = realm.id,
                clientId = client.id,
                name = request.name,
                description = request.description,
                composite = false,
                keycloakId = "${client.keycloakId}/${request.name}"
            )
        ).awaitSingle()

        logger.info("Created client role: ${request.name} for client: $clientId in realm: $realmName")
        return role.toResponse()
    }

    suspend fun listRealmRoles(realmName: String): List<RoleResponse> {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        return roleRepository.findByRealmIdAndClientIdIsNull(realm.id!!)
            .collectList()
            .awaitSingle()
            .map { it.toResponse() }
    }

    suspend fun listClientRoles(realmName: String, clientId: String): List<RoleResponse> {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val client = clientRepository.findByRealmIdAndClientId(realm.id!!, clientId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found")

        return roleRepository.findByClientId(client.id!!)
            .collectList()
            .awaitSingle()
            .map { it.toResponse() }
    }

    suspend fun getRealmRole(realmName: String, roleName: String): RoleResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val role = roleRepository.findByRealmIdAndNameAndClientIdIsNull(realm.id!!, roleName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found")

        return role.toResponse()
    }

    suspend fun updateRealmRole(realmName: String, roleName: String, request: UpdateRoleRequest): RoleResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val role = roleRepository.findByRealmIdAndNameAndClientIdIsNull(realm.id!!, roleName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found")

        // Update in Keycloak
        val updatedRoleRep = RoleRepresentation(
            name = roleName,
            description = request.description ?: role.description
        )
        keycloakClient.updateRealmRole(realmName, roleName, updatedRoleRep)

        // Update local database
        val updatedRole = roleRepository.save(
            role.copy(
                description = request.description ?: role.description,
                updatedAt = Instant.now()
            )
        ).awaitSingle()

        logger.info("Updated realm role: $roleName in realm: $realmName")
        return updatedRole.toResponse()
    }

    suspend fun deleteRealmRole(realmName: String, roleName: String) {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val role = roleRepository.findByRealmIdAndNameAndClientIdIsNull(realm.id!!, roleName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found")

        // Delete from Keycloak
        keycloakClient.deleteRealmRole(realmName, roleName)

        // Delete from local database
        roleRepository.delete(role).awaitSingleOrNull()

        logger.info("Deleted realm role: $roleName from realm: $realmName")
    }

    suspend fun deleteClientRole(realmName: String, clientId: String, roleName: String) {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val client = clientRepository.findByRealmIdAndClientId(realm.id!!, clientId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found")

        val role = roleRepository.findByClientIdAndName(client.id!!, roleName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found")

        // Delete from Keycloak
        keycloakClient.deleteClientRole(realmName, client.keycloakId, roleName)

        // Delete from local database
        roleRepository.delete(role).awaitSingleOrNull()

        logger.info("Deleted client role: $roleName from client: $clientId in realm: $realmName")
    }

    private fun KcRole.toResponse() = RoleResponse(
        id = id!!,
        name = name,
        description = description,
        composite = composite
    )
}

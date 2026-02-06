package com.keeplearning.auth.client.service

import com.keeplearning.auth.client.dto.*
import com.keeplearning.auth.domain.entity.ClientCustomRole
import com.keeplearning.auth.domain.repository.ClientCustomRoleRepository
import com.keeplearning.auth.domain.repository.KcClientRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class ClientRoleService(
    private val clientRepository: KcClientRepository,
    private val roleRepository: ClientCustomRoleRepository
) {
    private val logger = LoggerFactory.getLogger(ClientRoleService::class.java)

    suspend fun createRole(
        clientUuid: UUID,
        request: CreateClientRoleRequest,
        actorId: String? = null
    ): ClientRoleResponse {
        val client = clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        // Check if role name already exists for this client
        val exists = roleRepository.existsByClientIdAndName(clientUuid, request.name).awaitSingle()
        if (exists) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Role '${request.name}' already exists for this client")
        }

        // If this role is set as default, clear any existing default
        if (request.isDefault) {
            clearDefaultRole(clientUuid)
        }

        val role = roleRepository.save(
            ClientCustomRole(
                clientId = clientUuid,
                name = request.name,
                displayName = request.displayName,
                description = request.description,
                isDefault = request.isDefault
            )
        ).awaitSingle()

        logger.info("Created custom role '${request.name}' for client ${client.clientId}")
        return role.toResponse()
    }

    suspend fun listRoles(clientUuid: UUID): List<ClientRoleResponse> {
        clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        return roleRepository.findByClientId(clientUuid)
            .collectList()
            .awaitSingle()
            .map { it.toResponse() }
    }

    suspend fun getRole(clientUuid: UUID, roleName: String): ClientRoleResponse {
        clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        val role = roleRepository.findByClientIdAndName(clientUuid, roleName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found")

        return role.toResponse()
    }

    suspend fun updateRole(
        clientUuid: UUID,
        roleName: String,
        request: UpdateClientRoleRequest
    ): ClientRoleResponse {
        clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        val role = roleRepository.findByClientIdAndName(clientUuid, roleName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found")

        // If setting this role as default, clear any existing default
        if (request.isDefault == true && !role.isDefault) {
            clearDefaultRole(clientUuid)
        }

        val updatedRole = roleRepository.save(
            role.copy(
                displayName = request.displayName ?: role.displayName,
                description = request.description ?: role.description,
                isDefault = request.isDefault ?: role.isDefault,
                updatedAt = Instant.now()
            )
        ).awaitSingle()

        logger.info("Updated custom role '$roleName' for client $clientUuid")
        return updatedRole.toResponse()
    }

    suspend fun deleteRole(clientUuid: UUID, roleName: String) {
        clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        val role = roleRepository.findByClientIdAndName(clientUuid, roleName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Role '$roleName' not found")

        roleRepository.delete(role).awaitSingleOrNull()
        logger.info("Deleted custom role '$roleName' from client $clientUuid")
    }

    suspend fun getDefaultRole(clientUuid: UUID): ClientCustomRole? {
        return roleRepository.findByClientIdAndIsDefaultTrue(clientUuid).awaitSingleOrNull()
    }

    suspend fun getRolesByNames(clientUuid: UUID, roleNames: List<String>): List<ClientCustomRole> {
        val allRoles = roleRepository.findByClientId(clientUuid).collectList().awaitSingle()
        return allRoles.filter { it.name in roleNames }
    }

    private suspend fun clearDefaultRole(clientUuid: UUID) {
        val existingDefault = roleRepository.findByClientIdAndIsDefaultTrue(clientUuid).awaitSingleOrNull()
        if (existingDefault != null) {
            roleRepository.save(existingDefault.copy(isDefault = false, updatedAt = Instant.now())).awaitSingle()
        }
    }

    private fun ClientCustomRole.toResponse() = ClientRoleResponse(
        id = id!!,
        name = name,
        displayName = displayName,
        description = description,
        isDefault = isDefault,
        createdAt = createdAt
    )
}

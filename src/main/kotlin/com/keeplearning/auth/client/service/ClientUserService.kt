package com.keeplearning.auth.client.service

import com.keeplearning.auth.client.dto.*
import com.keeplearning.auth.domain.entity.KcUserClient
import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.domain.repository.KcUserClientRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ClientUserService(
    private val clientRepository: KcClientRepository,
    private val userClientRepository: KcUserClientRepository
) {
    private val logger = LoggerFactory.getLogger(ClientUserService::class.java)

    /**
     * Add users to a client's authorized users list
     */
    suspend fun addUsers(
        clientUuid: UUID,
        request: AddUsersRequest,
        actorId: String
    ): AddUsersResponse {
        val client = clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        val added = mutableListOf<String>()
        val alreadyExists = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (userRef in request.users) {
            try {
                val exists = userClientRepository.existsByClientIdAndUserEmail(clientUuid, userRef.email)
                    .awaitSingle()

                if (exists) {
                    alreadyExists.add(userRef.email)
                    continue
                }

                userClientRepository.save(
                    KcUserClient(
                        realmId = client.realmId,
                        userKeycloakId = userRef.keycloakId ?: "",
                        userEmail = userRef.email,
                        clientId = clientUuid,
                        assignedByKeycloakId = actorId
                    )
                ).awaitSingle()

                added.add(userRef.email)
                logger.info("Added user ${userRef.email} to client ${client.clientId}")
            } catch (e: Exception) {
                logger.error("Failed to add user ${userRef.email} to client ${client.clientId}", e)
                failed.add(userRef.email)
            }
        }

        return AddUsersResponse(
            added = added,
            alreadyExists = alreadyExists,
            failed = failed
        )
    }

    /**
     * Remove users from a client's authorized users list
     */
    suspend fun removeUsers(
        clientUuid: UUID,
        request: RemoveUsersRequest
    ): RemoveUsersResponse {
        val client = clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        val removed = mutableListOf<String>()
        val notFound = mutableListOf<String>()

        for (email in request.emails) {
            val userClient = userClientRepository.findByClientIdAndUserEmail(clientUuid, email)
                .awaitSingleOrNull()

            if (userClient == null) {
                notFound.add(email)
                continue
            }

            userClientRepository.delete(userClient).awaitSingleOrNull()
            removed.add(email)
            logger.info("Removed user $email from client ${client.clientId}")
        }

        return RemoveUsersResponse(
            removed = removed,
            notFound = notFound
        )
    }

    /**
     * List all authorized users for a client
     */
    suspend fun listUsers(clientUuid: UUID): List<ClientUserResponse> {
        clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        return userClientRepository.findByClientId(clientUuid)
            .collectList()
            .awaitSingle()
            .map { it.toResponse() }
    }

    /**
     * Check if a user is authorized for a client
     */
    suspend fun isUserAuthorized(
        clientUuid: UUID,
        email: String
    ): AuthorizationCheckResponse {
        val client = clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        val authorized = userClientRepository.existsByClientIdAndUserEmail(clientUuid, email)
            .awaitSingle()

        return AuthorizationCheckResponse(
            authorized = authorized,
            email = email,
            clientId = client.clientId
        )
    }

    /**
     * Verify that the caller (identified by JWT) is authorized to manage users for the given client.
     *
     * Authorization is granted if:
     * 1. The caller is a super admin (has ROLE_SUPER_ADMIN)
     * 2. The caller's azp (authorized party) matches the client's clientId (service account)
     * 3. The caller's azp matches a backend client paired with this client
     */
    suspend fun verifyClientAccess(jwt: Jwt, clientUuid: UUID): Boolean {
        // Check if super admin
        val roles = jwt.getClaimAsStringList("realm_access")
            ?.let { null } // realm_access is a map, not a list
            ?: run {
                @Suppress("UNCHECKED_CAST")
                val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access")
                (realmAccess?.get("roles") as? List<String>) ?: emptyList()
            }

        if (roles.contains("SUPER_ADMIN")) {
            return true
        }

        // Check if the caller is the client itself or its paired client
        val azp = jwt.getClaimAsString("azp") ?: return false

        val client = clientRepository.findById(clientUuid).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")

        // Direct match: caller is this client
        if (azp == client.clientId) {
            return true
        }

        // Check paired client: if this is a frontend client, allow its paired backend
        if (client.pairedClientId != null) {
            val pairedClient = clientRepository.findById(client.pairedClientId).awaitSingleOrNull()
            if (pairedClient?.clientId == azp) {
                return true
            }
        }

        // Check reverse pairing: if caller is a backend client that has this client as paired
        val reverseClient = clientRepository.findByPairedClientId(clientUuid).awaitSingleOrNull()
        if (reverseClient?.clientId == azp) {
            return true
        }

        return false
    }

    /**
     * Get client UUID by clientId string
     */
    suspend fun getClientUuidByClientId(clientId: String): UUID? {
        return clientRepository.findByKeycloakId(clientId).awaitSingleOrNull()?.id
    }

    private fun KcUserClient.toResponse() = ClientUserResponse(
        id = id!!,
        email = userEmail,
        keycloakId = userKeycloakId.ifEmpty { null },
        assignedAt = assignedAt,
        assignedBy = assignedByKeycloakId
    )
}

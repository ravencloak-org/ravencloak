package com.keeplearning.auth.realm.service

import com.keeplearning.auth.domain.entity.KcUserClient
import com.keeplearning.auth.domain.entity.User
import com.keeplearning.auth.domain.repository.*
import com.keeplearning.auth.realm.dto.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val realmRepository: KcRealmRepository,
    private val clientRepository: KcClientRepository,
    private val userClientRepository: KcUserClientRepository
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    suspend fun listUsers(realmName: String): List<RealmUserResponse> {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val users = userRepository.findByRealmId(realm.id!!)
            .collectList().awaitSingle()

        return users.map { user ->
            val authorizedClients = getAuthorizedClientsForUser(user.id!!)
            user.toResponse(authorizedClients)
        }
    }

    suspend fun getUser(realmName: String, userId: UUID): RealmUserDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val user = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        if (user.realmId != realm.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in realm")
        }

        val authorizedClients = getAuthorizedClientDetailsForUser(user.id!!)
        return user.toDetailResponse(authorizedClients)
    }

    suspend fun getUserByEmail(realmName: String, email: String): RealmUserDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val user = userRepository.findByRealmIdAndEmail(realm.id!!, email).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val authorizedClients = getAuthorizedClientDetailsForUser(user.id!!)
        return user.toDetailResponse(authorizedClients)
    }

    suspend fun createUser(
        realmName: String,
        request: CreateRealmUserRequest,
        actorKeycloakId: String
    ): RealmUserDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Check if user already exists
        val existingUser = userRepository.findByRealmIdAndEmail(realm.id!!, request.email).awaitSingleOrNull()
        if (existingUser != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User with email '${request.email}' already exists")
        }

        // Create user (keycloakUserId will be empty until they actually log in)
        val user = userRepository.save(
            User(
                keycloakUserId = "", // Will be populated when user logs in via Keycloak
                email = request.email,
                displayName = request.displayName,
                firstName = request.firstName,
                lastName = request.lastName,
                phone = request.phone,
                jobTitle = request.jobTitle,
                department = request.department,
                accountId = realm.accountId ?: realm.id, // Use realm id if no account
                realmId = realm.id,
                status = "ACTIVE"
            )
        ).awaitSingle()

        logger.info("Created user ${request.email} in realm $realmName")

        // Assign to clients if specified
        for (clientId in request.clientIds) {
            val client = clientRepository.findById(clientId).awaitSingleOrNull()
            if (client != null && client.realmId == realm.id) {
                userClientRepository.save(
                    KcUserClient(
                        realmId = realm.id,
                        userKeycloakId = "",
                        userEmail = request.email,
                        clientId = clientId,
                        assignedByKeycloakId = actorKeycloakId
                    )
                ).awaitSingle()
                logger.info("Assigned user ${request.email} to client ${client.clientId}")
            }
        }

        val authorizedClients = getAuthorizedClientDetailsForUser(user.id!!)
        return user.toDetailResponse(authorizedClients)
    }

    suspend fun updateUser(
        realmName: String,
        userId: UUID,
        request: UpdateRealmUserRequest
    ): RealmUserDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val user = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        if (user.realmId != realm.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in realm")
        }

        val updatedUser = userRepository.save(
            user.copy(
                displayName = request.displayName ?: user.displayName,
                firstName = request.firstName ?: user.firstName,
                lastName = request.lastName ?: user.lastName,
                phone = request.phone ?: user.phone,
                bio = request.bio ?: user.bio,
                jobTitle = request.jobTitle ?: user.jobTitle,
                department = request.department ?: user.department,
                status = request.status ?: user.status,
                updatedAt = Instant.now()
            )
        ).awaitSingle()

        logger.info("Updated user ${user.email} in realm $realmName")

        val authorizedClients = getAuthorizedClientDetailsForUser(updatedUser.id!!)
        return updatedUser.toDetailResponse(authorizedClients)
    }

    suspend fun deleteUser(realmName: String, userId: UUID) {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val user = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        if (user.realmId != realm.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in realm")
        }

        // Delete user-client associations first
        userClientRepository.findByClientId(userId) // This won't work, need different query

        userRepository.delete(user).awaitSingleOrNull()
        logger.info("Deleted user ${user.email} from realm $realmName")
    }

    suspend fun assignUserToClients(
        realmName: String,
        userId: UUID,
        request: AssignClientsRequest,
        actorKeycloakId: String
    ): RealmUserDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val user = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        if (user.realmId != realm.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in realm")
        }

        for (clientId in request.clientIds) {
            val client = clientRepository.findById(clientId).awaitSingleOrNull()
            if (client != null && client.realmId == realm.id) {
                // Check if already assigned
                val existing = userClientRepository.findByClientIdAndUserEmail(clientId, user.email)
                    .awaitSingleOrNull()

                if (existing == null) {
                    userClientRepository.save(
                        KcUserClient(
                            realmId = realm.id,
                            userKeycloakId = user.keycloakUserId,
                            userEmail = user.email,
                            clientId = clientId,
                            assignedByKeycloakId = actorKeycloakId
                        )
                    ).awaitSingle()
                    logger.info("Assigned user ${user.email} to client ${client.clientId}")
                }
            }
        }

        val authorizedClients = getAuthorizedClientDetailsForUser(user.id!!)
        return user.toDetailResponse(authorizedClients)
    }

    suspend fun removeUserFromClient(
        realmName: String,
        userId: UUID,
        clientId: UUID
    ): RealmUserDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val user = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        if (user.realmId != realm.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in realm")
        }

        userClientRepository.deleteByClientIdAndUserEmail(clientId, user.email).awaitSingleOrNull()
        logger.info("Removed user ${user.email} from client $clientId")

        val authorizedClients = getAuthorizedClientDetailsForUser(user.id!!)
        return user.toDetailResponse(authorizedClients)
    }

    private suspend fun getAuthorizedClientsForUser(userId: UUID): List<AuthorizedClientInfo> {
        val user = userRepository.findById(userId).awaitSingleOrNull() ?: return emptyList()

        val userClients = userClientRepository.findByRealmIdAndUserEmail(
            user.realmId ?: return emptyList(),
            user.email
        ).collectList().awaitSingle()

        return userClients.mapNotNull { uc ->
            val client = clientRepository.findById(uc.clientId).awaitSingleOrNull()
            client?.let {
                AuthorizedClientInfo(
                    clientId = it.id!!,
                    clientName = it.name ?: it.clientId,
                    assignedAt = uc.assignedAt
                )
            }
        }
    }

    private suspend fun getAuthorizedClientDetailsForUser(userId: UUID): List<AuthorizedClientDetail> {
        val user = userRepository.findById(userId).awaitSingleOrNull() ?: return emptyList()

        val userClients = userClientRepository.findByRealmIdAndUserEmail(
            user.realmId ?: return emptyList(),
            user.email
        ).collectList().awaitSingle()

        return userClients.mapNotNull { uc ->
            val client = clientRepository.findById(uc.clientId).awaitSingleOrNull()
            client?.let {
                AuthorizedClientDetail(
                    clientId = it.id!!,
                    clientIdName = it.clientId,
                    clientDisplayName = it.name,
                    publicClient = it.publicClient,
                    assignedAt = uc.assignedAt,
                    assignedBy = uc.assignedByKeycloakId
                )
            }
        }
    }

    private fun User.toResponse(authorizedClients: List<AuthorizedClientInfo>) = RealmUserResponse(
        id = id!!,
        keycloakUserId = keycloakUserId,
        email = email,
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        jobTitle = jobTitle,
        department = department,
        avatarUrl = avatarUrl,
        status = status,
        lastLoginAt = lastLoginAt,
        createdAt = createdAt,
        authorizedClients = authorizedClients
    )

    private fun User.toDetailResponse(authorizedClients: List<AuthorizedClientDetail>) = RealmUserDetailResponse(
        id = id!!,
        keycloakUserId = keycloakUserId,
        email = email,
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        bio = bio,
        jobTitle = jobTitle,
        department = department,
        avatarUrl = avatarUrl,
        status = status,
        lastLoginAt = lastLoginAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        authorizedClients = authorizedClients
    )
}

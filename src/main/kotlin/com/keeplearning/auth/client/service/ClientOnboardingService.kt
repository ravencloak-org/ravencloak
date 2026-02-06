package com.keeplearning.auth.client.service

import com.keeplearning.auth.client.dto.*
import com.keeplearning.auth.domain.entity.KcClient
import com.keeplearning.auth.domain.entity.KcUserClient
import com.keeplearning.auth.domain.entity.User
import com.keeplearning.auth.domain.entity.UserClientCustomRole
import com.keeplearning.auth.domain.repository.*
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ClientOnboardingService(
    private val clientRepository: KcClientRepository,
    private val realmRepository: KcRealmRepository,
    private val userRepository: UserRepository,
    private val userClientRepository: KcUserClientRepository,
    private val roleRepository: ClientCustomRoleRepository,
    private val userRoleRepository: UserClientCustomRoleRepository
) {
    private val logger = LoggerFactory.getLogger(ClientOnboardingService::class.java)

    /**
     * Onboard users for the client identified by the JWT's azp claim.
     * Handles paired clients: if request comes from backend client, authorizes for frontend client.
     */
    suspend fun onboardUsers(
        jwt: Jwt,
        request: OnboardUsersRequest
    ): OnboardUsersResponse {
        // Extract client info from JWT
        val azp = jwt.getClaimAsString("azp")
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing 'azp' claim in token")

        // Find the calling client by its Keycloak client ID
        val callingClient = clientRepository.findByKeycloakId(azp).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$azp' not found")

        // Determine the target client for user authorization
        // If this is a backend client, find the paired frontend client
        val targetClient = resolveTargetClient(callingClient)

        // Get the realm
        val realm = realmRepository.findById(targetClient.realmId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm not found")

        // Get available roles for this client
        val availableRoles = roleRepository.findByClientId(targetClient.id!!).collectList().awaitSingle()
        val defaultRole = availableRoles.find { it.isDefault }

        // Validate that roles are defined if we need them
        if (availableRoles.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "No roles defined for client '${targetClient.clientId}'. Please create roles before onboarding users."
            )
        }

        val results = mutableListOf<OnboardUserResult>()

        for (userRequest in request.users) {
            try {
                val result = onboardSingleUser(
                    userRequest = userRequest,
                    targetClient = targetClient,
                    realmId = realm.id!!,
                    accountId = realm.accountId ?: realm.id,
                    availableRoles = availableRoles.associateBy { it.name },
                    defaultRole = defaultRole,
                    actorId = jwt.subject
                )
                results.add(result)
            } catch (e: Exception) {
                logger.error("Failed to onboard user ${userRequest.email}", e)
                results.add(
                    OnboardUserResult(
                        email = userRequest.email,
                        status = OnboardStatus.FAILED,
                        error = e.message ?: "Unknown error"
                    )
                )
            }
        }

        val summary = OnboardSummary(
            total = results.size,
            created = results.count { it.status == OnboardStatus.CREATED },
            alreadyExists = results.count { it.status == OnboardStatus.ALREADY_EXISTS },
            failed = results.count { it.status == OnboardStatus.FAILED }
        )

        return OnboardUsersResponse(results = results, summary = summary)
    }

    private suspend fun onboardSingleUser(
        userRequest: OnboardUserRequest,
        targetClient: KcClient,
        realmId: UUID,
        accountId: UUID,
        availableRoles: Map<String, com.keeplearning.auth.domain.entity.ClientCustomRole>,
        defaultRole: com.keeplearning.auth.domain.entity.ClientCustomRole?,
        actorId: String
    ): OnboardUserResult {
        // Check if user already exists in this realm
        var user = userRepository.findByRealmIdAndEmail(realmId, userRequest.email).awaitSingleOrNull()
        val isNewUser = user == null

        if (user == null) {
            // Create new user
            user = userRepository.save(
                User(
                    keycloakUserId = "", // Will be populated when user logs in via Keycloak
                    email = userRequest.email,
                    displayName = userRequest.displayName,
                    firstName = userRequest.firstName,
                    lastName = userRequest.lastName,
                    phone = userRequest.phone,
                    jobTitle = userRequest.jobTitle,
                    department = userRequest.department,
                    accountId = accountId,
                    realmId = realmId,
                    status = "ACTIVE"
                )
            ).awaitSingle()
            logger.info("Created user ${userRequest.email} in realm")
        }

        // Check if user is already authorized for this client
        val existingAuthorization = userClientRepository
            .existsByClientIdAndUserEmail(targetClient.id!!, userRequest.email)
            .awaitSingle()

        if (existingAuthorization && !isNewUser) {
            return OnboardUserResult(
                email = userRequest.email,
                status = OnboardStatus.ALREADY_EXISTS,
                userId = user.id
            )
        }

        // Add client authorization if not exists
        if (!existingAuthorization) {
            userClientRepository.save(
                KcUserClient(
                    realmId = realmId,
                    userKeycloakId = user.keycloakUserId,
                    userEmail = userRequest.email,
                    clientId = targetClient.id,
                    assignedByKeycloakId = actorId
                )
            ).awaitSingle()
            logger.info("Authorized user ${userRequest.email} for client ${targetClient.clientId}")
        }

        // Determine which roles to assign
        val rolesToAssign = if (userRequest.roles.isNotEmpty()) {
            // Validate requested roles exist
            val invalidRoles = userRequest.roles.filter { it !in availableRoles }
            if (invalidRoles.isNotEmpty()) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid roles: ${invalidRoles.joinToString()}. Available roles: ${availableRoles.keys.joinToString()}"
                )
            }
            userRequest.roles.mapNotNull { availableRoles[it] }
        } else if (defaultRole != null) {
            listOf(defaultRole)
        } else {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "No roles specified and no default role configured for client '${targetClient.clientId}'"
            )
        }

        // Assign roles
        val assignedRoleNames = mutableListOf<String>()
        for (role in rolesToAssign) {
            // Check if role already assigned
            val existing = userRoleRepository
                .findByUserIdAndCustomRoleId(user.id!!, role.id!!)
                .awaitSingleOrNull()

            if (existing == null) {
                userRoleRepository.save(
                    UserClientCustomRole(
                        userId = user.id,
                        customRoleId = role.id,
                        assignedBy = null // We don't have the actor's user UUID
                    )
                ).awaitSingle()
                assignedRoleNames.add(role.name)
                logger.info("Assigned role '${role.name}' to user ${userRequest.email}")
            } else {
                assignedRoleNames.add(role.name)
            }
        }

        return OnboardUserResult(
            email = userRequest.email,
            status = if (isNewUser) OnboardStatus.CREATED else OnboardStatus.ALREADY_EXISTS,
            userId = user.id,
            assignedRoles = assignedRoleNames
        )
    }

    /**
     * Resolve the target client for user authorization.
     * If the calling client is a backend client (has a frontend paired to it),
     * return the frontend client. Otherwise, return the calling client itself.
     */
    private suspend fun resolveTargetClient(callingClient: KcClient): KcClient {
        // Check if any frontend client has this client as its paired backend
        val frontendClient = clientRepository.findByPairedClientId(callingClient.id!!).awaitSingleOrNull()

        return if (frontendClient != null) {
            logger.debug("Resolved backend ${callingClient.clientId} to frontend ${frontendClient.clientId}")
            frontendClient
        } else {
            // If this client has a paired backend, it's already a frontend
            // Or it's a standalone client
            callingClient
        }
    }

    /**
     * Verify that the JWT caller has access to onboard users.
     * Access is granted if the caller is authenticated with a valid client credentials token.
     */
    suspend fun verifyOnboardAccess(jwt: Jwt): Boolean {
        val azp = jwt.getClaimAsString("azp") ?: return false
        val client = clientRepository.findByKeycloakId(azp).awaitSingleOrNull() ?: return false

        // The client must have service accounts enabled (backend client)
        return client.serviceAccountsEnabled
    }
}

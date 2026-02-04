package com.keeplearning.auth.public.controller

import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.KcUserClientRepository
import com.keeplearning.auth.domain.repository.UserRepository
import com.keeplearning.auth.public.dto.PublicUserResponse
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/**
 * Public endpoint for the Keycloak SPI to validate users.
 * No authentication required - SPI calls this without a token.
 */
@RestController
@RequestMapping("/api/public/users")
class PublicUserController(
    private val userRepository: UserRepository,
    private val realmRepository: KcRealmRepository,
    private val clientRepository: KcClientRepository,
    private val userClientRepository: KcUserClientRepository
) {
    private val logger = LoggerFactory.getLogger(PublicUserController::class.java)

    /**
     * Get user by email for Keycloak SPI validation.
     *
     * @param email User's email address
     * @param realmName Keycloak realm name (required)
     * @param clientId Optional client ID - if provided, checks user-client authorization
     */
    @GetMapping("/{email}")
    suspend fun getUserByEmail(
        @PathVariable email: String,
        @RequestHeader("X-Realm-Name") realmName: String,
        @RequestHeader("X-Client-Id", required = false) clientId: String?
    ): PublicUserResponse {
        logger.debug("SPI user lookup: email=$email, realm=$realmName, clientId=$clientId")

        // Find the realm
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm not found")

        // Find user in the realm
        val user = userRepository.findByRealmIdAndEmail(realm.id!!, email).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        // Check if user is enabled
        if (user.status != "ACTIVE") {
            logger.warn("User $email is not active (status=${user.status})")
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }

        // If client ID is provided, check client-specific authorization
        if (!clientId.isNullOrBlank()) {
            val client = clientRepository.findByRealmIdAndClientId(realm.id, clientId).awaitSingleOrNull()

            if (client != null) {
                // Check if user is authorized for this client
                val isAuthorized = userClientRepository
                    .existsByClientIdAndUserEmail(client.id!!, email)
                    .awaitSingle()

                if (!isAuthorized) {
                    // Also check by keycloak ID if available
                    val isAuthorizedById = userClientRepository
                        .existsByClientIdAndUserKeycloakId(client.id, user.keycloakUserId)
                        .awaitSingle()

                    if (!isAuthorizedById) {
                        logger.info("User $email not authorized for client $clientId")
                        throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
                    }
                }
            }
            // If client not found in DB, proceed without client-specific check
            // This allows realm-wide users to still authenticate
        }

        logger.debug("User $email validated for realm $realmName")

        return PublicUserResponse(
            id = user.keycloakUserId,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            displayName = user.displayName,
            enabled = user.status == "ACTIVE",
            emailVerified = true,
            attributes = buildAttributes(user)
        )
    }

    private fun buildAttributes(user: com.keeplearning.auth.domain.entity.User): Map<String, List<String>> {
        val attrs = mutableMapOf<String, List<String>>()

        user.phone?.let { attrs["phone"] = listOf(it) }
        user.department?.let { attrs["department"] = listOf(it) }
        user.jobTitle?.let { attrs["job_title"] = listOf(it) }
        user.avatarUrl?.let { attrs["avatar_url"] = listOf(it) }

        return attrs
    }
}

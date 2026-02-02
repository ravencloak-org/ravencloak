package com.keeplearning.auth.realm.service

import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.IdentityProviderRepresentation
import com.keeplearning.auth.realm.dto.*
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class IdpService(
    private val keycloakClient: KeycloakAdminClient,
    private val realmRepository: KcRealmRepository
) {
    private val logger = LoggerFactory.getLogger(IdpService::class.java)

    suspend fun createIdentityProvider(realmName: String, request: CreateIdpRequest): IdpResponse {
        realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Create in Keycloak
        val idpRep = IdentityProviderRepresentation(
            alias = request.alias,
            displayName = request.displayName,
            providerId = request.providerId,
            enabled = request.enabled,
            trustEmail = request.trustEmail,
            config = request.config
        )
        keycloakClient.createIdentityProvider(realmName, idpRep)

        logger.info("Created identity provider: ${request.alias} in realm: $realmName")
        return IdpResponse(
            alias = request.alias,
            displayName = request.displayName,
            providerId = request.providerId,
            enabled = request.enabled,
            trustEmail = request.trustEmail,
            config = request.config ?: emptyMap()
        )
    }

    suspend fun getIdentityProvider(realmName: String, alias: String): IdpResponse {
        realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val idp = keycloakClient.getIdentityProvider(realmName, alias)

        return IdpResponse(
            alias = idp.alias,
            displayName = idp.displayName,
            providerId = idp.providerId,
            enabled = idp.enabled,
            trustEmail = idp.trustEmail,
            config = idp.config ?: emptyMap()
        )
    }

    suspend fun listIdentityProviders(realmName: String): List<IdpResponse> {
        realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val idps = keycloakClient.getIdentityProviders(realmName)

        return idps.map {
            IdpResponse(
                alias = it.alias,
                displayName = it.displayName,
                providerId = it.providerId,
                enabled = it.enabled,
                trustEmail = it.trustEmail,
                config = it.config ?: emptyMap()
            )
        }
    }

    suspend fun updateIdentityProvider(realmName: String, alias: String, request: UpdateIdpRequest): IdpResponse {
        realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Get current IDP from Keycloak
        val currentIdp = keycloakClient.getIdentityProvider(realmName, alias)

        // Update in Keycloak
        val updatedIdpRep = currentIdp.copy(
            displayName = request.displayName ?: currentIdp.displayName,
            enabled = request.enabled ?: currentIdp.enabled,
            trustEmail = request.trustEmail ?: currentIdp.trustEmail,
            config = request.config ?: currentIdp.config
        )
        keycloakClient.updateIdentityProvider(realmName, alias, updatedIdpRep)

        logger.info("Updated identity provider: $alias in realm: $realmName")
        return IdpResponse(
            alias = updatedIdpRep.alias,
            displayName = updatedIdpRep.displayName,
            providerId = updatedIdpRep.providerId,
            enabled = updatedIdpRep.enabled,
            trustEmail = updatedIdpRep.trustEmail,
            config = updatedIdpRep.config ?: emptyMap()
        )
    }

    suspend fun deleteIdentityProvider(realmName: String, alias: String) {
        realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Delete from Keycloak
        keycloakClient.deleteIdentityProvider(realmName, alias)

        logger.info("Deleted identity provider: $alias from realm: $realmName")
    }
}

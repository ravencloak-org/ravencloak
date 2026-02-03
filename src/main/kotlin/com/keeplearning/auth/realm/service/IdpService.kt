package com.keeplearning.auth.realm.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityType
import com.keeplearning.auth.audit.service.AuditService
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.IdentityProviderRepresentation
import com.keeplearning.auth.realm.dto.*
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class IdpService(
    private val keycloakClient: KeycloakAdminClient,
    private val realmRepository: KcRealmRepository,
    private val auditService: AuditService
) {
    private val logger = LoggerFactory.getLogger(IdpService::class.java)

    suspend fun createIdentityProvider(
        realmName: String,
        request: CreateIdpRequest,
        actor: JwtAuthenticationToken? = null
    ): IdpResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
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

        val response = IdpResponse(
            alias = request.alias,
            displayName = request.displayName,
            providerId = request.providerId,
            enabled = request.enabled,
            trustEmail = request.trustEmail,
            config = request.config ?: emptyMap()
        )

        // Log audit trail (use deterministic UUID based on alias for IDP)
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.CREATE,
                entityType = EntityType.IDP,
                entityId = generateIdpUuid(realmName, request.alias),
                entityName = request.alias,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = request.alias,
                afterState = response.toAuditState()
            )
        }

        logger.info("Created identity provider: ${request.alias} in realm: $realmName")
        return response
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

    suspend fun updateIdentityProvider(
        realmName: String,
        alias: String,
        request: UpdateIdpRequest,
        actor: JwtAuthenticationToken? = null
    ): IdpResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Get current IDP from Keycloak
        val currentIdp = keycloakClient.getIdentityProvider(realmName, alias)

        // Capture before state for audit
        val beforeState = IdpResponse(
            alias = currentIdp.alias,
            displayName = currentIdp.displayName,
            providerId = currentIdp.providerId,
            enabled = currentIdp.enabled,
            trustEmail = currentIdp.trustEmail,
            config = currentIdp.config ?: emptyMap()
        ).toAuditState()

        // Update in Keycloak
        val updatedIdpRep = currentIdp.copy(
            displayName = request.displayName ?: currentIdp.displayName,
            enabled = request.enabled ?: currentIdp.enabled,
            trustEmail = request.trustEmail ?: currentIdp.trustEmail,
            config = request.config ?: currentIdp.config
        )
        keycloakClient.updateIdentityProvider(realmName, alias, updatedIdpRep)

        val response = IdpResponse(
            alias = updatedIdpRep.alias,
            displayName = updatedIdpRep.displayName,
            providerId = updatedIdpRep.providerId,
            enabled = updatedIdpRep.enabled,
            trustEmail = updatedIdpRep.trustEmail,
            config = updatedIdpRep.config ?: emptyMap()
        )

        // Log audit trail
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.UPDATE,
                entityType = EntityType.IDP,
                entityId = generateIdpUuid(realmName, alias),
                entityName = alias,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = alias,
                beforeState = beforeState,
                afterState = response.toAuditState()
            )
        }

        logger.info("Updated identity provider: $alias in realm: $realmName")
        return response
    }

    suspend fun deleteIdentityProvider(
        realmName: String,
        alias: String,
        actor: JwtAuthenticationToken? = null
    ) {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Get current IDP for audit before delete
        val currentIdp = keycloakClient.getIdentityProvider(realmName, alias)
        val beforeState = IdpResponse(
            alias = currentIdp.alias,
            displayName = currentIdp.displayName,
            providerId = currentIdp.providerId,
            enabled = currentIdp.enabled,
            trustEmail = currentIdp.trustEmail,
            config = currentIdp.config ?: emptyMap()
        ).toAuditState()

        // Delete from Keycloak
        keycloakClient.deleteIdentityProvider(realmName, alias)

        // Log audit trail
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.DELETE,
                entityType = EntityType.IDP,
                entityId = generateIdpUuid(realmName, alias),
                entityName = alias,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = alias,
                beforeState = beforeState
            )
        }

        logger.info("Deleted identity provider: $alias from realm: $realmName")
    }

    private fun generateIdpUuid(realmName: String, alias: String): UUID {
        // Generate deterministic UUID from realm and alias
        return UUID.nameUUIDFromBytes("idp:$realmName:$alias".toByteArray())
    }

    private fun IdpResponse.toAuditState(): Map<String, Any?> = mapOf(
        "alias" to alias,
        "displayName" to displayName,
        "providerId" to providerId,
        "enabled" to enabled,
        "trustEmail" to trustEmail,
        "config" to config
    )
}

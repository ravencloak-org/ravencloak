package com.keeplearning.auth.realm.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityType
import com.keeplearning.auth.audit.service.AuditService
import com.keeplearning.auth.domain.entity.KcClient
import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.ClientRepresentation
import com.keeplearning.auth.realm.dto.*
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import tools.jackson.core.type.TypeReference
import java.time.Instant

@Service
class ClientService(
    private val keycloakClient: KeycloakAdminClient,
    private val clientRepository: KcClientRepository,
    private val realmRepository: KcRealmRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    @Value("\${KEYCLOAK_ISSUER_PREFIX}") private val keycloakIssuerPrefix: String
) {

    // Extract base Keycloak URL from issuer prefix (remove /realms/ suffix)
    private val keycloakBaseUrl: String
        get() = keycloakIssuerPrefix.removeSuffix("/").removeSuffix("realms").removeSuffix("/")
    private val logger = LoggerFactory.getLogger(ClientService::class.java)

    suspend fun createClient(
        realmName: String,
        request: CreateClientRequest,
        actor: JwtAuthenticationToken? = null
    ): ClientDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Check if client already exists
        val existingClient = clientRepository.findByRealmIdAndClientId(realm.id!!, request.clientId).awaitSingleOrNull()
        if (existingClient != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Client '${request.clientId}' already exists")
        }

        // Create in Keycloak
        val clientRep = ClientRepresentation(
            clientId = request.clientId,
            name = request.name,
            description = request.description,
            publicClient = request.publicClient,
            standardFlowEnabled = request.standardFlowEnabled,
            directAccessGrantsEnabled = request.directAccessGrantsEnabled,
            serviceAccountsEnabled = if (request.publicClient) false else request.serviceAccountsEnabled,
            rootUrl = request.rootUrl,
            baseUrl = request.baseUrl,
            redirectUris = request.redirectUris.ifEmpty { null },
            webOrigins = request.webOrigins.ifEmpty { null }
        )

        val keycloakId = keycloakClient.createClient(realmName, clientRep)

        // Save to local database
        val client = clientRepository.save(
            KcClient(
                realmId = realm.id,
                clientId = request.clientId,
                name = request.name,
                description = request.description,
                enabled = true,
                publicClient = request.publicClient,
                rootUrl = request.rootUrl,
                baseUrl = request.baseUrl,
                redirectUris = Json.of(objectMapper.writeValueAsString(request.redirectUris)),
                webOrigins = Json.of(objectMapper.writeValueAsString(request.webOrigins)),
                standardFlowEnabled = request.standardFlowEnabled,
                directAccessGrantsEnabled = request.directAccessGrantsEnabled,
                serviceAccountsEnabled = if (request.publicClient) false else request.serviceAccountsEnabled,
                keycloakId = keycloakId
            )
        ).awaitSingle()

        // Log audit trail
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.CREATE,
                entityType = EntityType.CLIENT,
                entityId = client.id!!,
                entityName = client.clientId,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = keycloakId,
                afterState = client.toAuditState()
            )
        }

        logger.info("Created client: ${request.clientId} in realm: $realmName")
        return client.toDetailResponse()
    }

    suspend fun getClient(realmName: String, clientId: String): ClientDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val client = clientRepository.findByRealmIdAndClientId(realm.id!!, clientId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found")

        return client.toDetailResponse()
    }

    companion object {
        // Default Keycloak clients that should be hidden from the dashboard
        private val DEFAULT_KEYCLOAK_CLIENTS = setOf(
            "account",
            "account-console",
            "admin-cli",
            "broker",
            "realm-management",
            "security-admin-console"
        )
    }

    suspend fun listClients(realmName: String): List<ClientResponse> {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        return clientRepository.findByRealmId(realm.id!!)
            .collectList()
            .awaitSingle()
            .filter { it.clientId !in DEFAULT_KEYCLOAK_CLIENTS }
            .map { it.toResponse() }
    }

    suspend fun updateClient(
        realmName: String,
        clientId: String,
        request: UpdateClientRequest,
        actor: JwtAuthenticationToken? = null
    ): ClientDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val client = clientRepository.findByRealmIdAndClientId(realm.id!!, clientId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found")

        // Capture before state for audit
        val beforeState = client.toAuditState()

        // Get current client from Keycloak
        val currentClient = keycloakClient.getClient(realmName, client.keycloakId)

        // Update in Keycloak
        val updatedClientRep = currentClient.copy(
            name = request.name ?: currentClient.name,
            description = request.description ?: currentClient.description,
            enabled = request.enabled ?: currentClient.enabled,
            publicClient = request.publicClient ?: currentClient.publicClient,
            standardFlowEnabled = request.standardFlowEnabled ?: currentClient.standardFlowEnabled,
            directAccessGrantsEnabled = request.directAccessGrantsEnabled ?: currentClient.directAccessGrantsEnabled,
            serviceAccountsEnabled = request.serviceAccountsEnabled ?: currentClient.serviceAccountsEnabled,
            rootUrl = request.rootUrl ?: currentClient.rootUrl,
            baseUrl = request.baseUrl ?: currentClient.baseUrl,
            redirectUris = request.redirectUris ?: currentClient.redirectUris,
            webOrigins = request.webOrigins ?: currentClient.webOrigins
        )

        keycloakClient.updateClient(realmName, client.keycloakId, updatedClientRep)

        // Update local database
        val updatedClient = clientRepository.save(
            client.copy(
                name = request.name ?: client.name,
                description = request.description ?: client.description,
                enabled = request.enabled ?: client.enabled,
                publicClient = request.publicClient ?: client.publicClient,
                standardFlowEnabled = request.standardFlowEnabled ?: client.standardFlowEnabled,
                directAccessGrantsEnabled = request.directAccessGrantsEnabled ?: client.directAccessGrantsEnabled,
                serviceAccountsEnabled = request.serviceAccountsEnabled ?: client.serviceAccountsEnabled,
                rootUrl = request.rootUrl ?: client.rootUrl,
                baseUrl = request.baseUrl ?: client.baseUrl,
                redirectUris = request.redirectUris?.let { Json.of(objectMapper.writeValueAsString(it)) } ?: client.redirectUris,
                webOrigins = request.webOrigins?.let { Json.of(objectMapper.writeValueAsString(it)) } ?: client.webOrigins,
                updatedAt = Instant.now()
            )
        ).awaitSingle()

        // Log audit trail
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.UPDATE,
                entityType = EntityType.CLIENT,
                entityId = updatedClient.id!!,
                entityName = updatedClient.clientId,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = updatedClient.keycloakId,
                beforeState = beforeState,
                afterState = updatedClient.toAuditState()
            )
        }

        logger.info("Updated client: $clientId in realm: $realmName")
        return updatedClient.toDetailResponse()
    }

    suspend fun deleteClient(
        realmName: String,
        clientId: String,
        actor: JwtAuthenticationToken? = null
    ) {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val client = clientRepository.findByRealmIdAndClientId(realm.id!!, clientId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found")

        // Capture before state for audit
        val beforeState = client.toAuditState()

        // Delete from Keycloak
        keycloakClient.deleteClient(realmName, client.keycloakId)

        // Delete from local database
        clientRepository.delete(client).awaitSingleOrNull()

        // Log audit trail
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.DELETE,
                entityType = EntityType.CLIENT,
                entityId = client.id!!,
                entityName = client.clientId,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = client.keycloakId,
                beforeState = beforeState
            )
        }

        logger.info("Deleted client: $clientId from realm: $realmName")
    }

    suspend fun getClientSecret(realmName: String, clientId: String): ClientSecretResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val client = clientRepository.findByRealmIdAndClientId(realm.id!!, clientId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found")

        if (client.publicClient) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Public clients do not have a secret")
        }

        val secret = keycloakClient.getClientSecret(realmName, client.keycloakId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client secret not found")

        return ClientSecretResponse(secret)
    }

    suspend fun regenerateClientSecret(realmName: String, clientId: String): ClientSecretResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val client = clientRepository.findByRealmIdAndClientId(realm.id!!, clientId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found")

        if (client.publicClient) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Public clients do not have a secret")
        }

        val secret = keycloakClient.regenerateClientSecret(realmName, client.keycloakId)
        logger.info("Regenerated client secret for: $clientId in realm: $realmName")

        return ClientSecretResponse(secret)
    }

    /**
     * Creates a full-stack application with paired frontend and backend clients
     */
    suspend fun createApplication(
        realmName: String,
        request: CreateApplicationRequest,
        actor: JwtAuthenticationToken? = null
    ): ApplicationResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val webClientId = "${request.applicationName}-web"
        val backendClientId = "${request.applicationName}-backend"

        // Check if clients already exist
        if (request.applicationType != ApplicationType.BACKEND_ONLY) {
            val existingWeb = clientRepository.findByRealmIdAndClientId(realm.id!!, webClientId).awaitSingleOrNull()
            if (existingWeb != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Client '$webClientId' already exists")
            }
        }
        if (request.applicationType != ApplicationType.FRONTEND_ONLY) {
            val existingBackend = clientRepository.findByRealmIdAndClientId(realm.id!!, backendClientId).awaitSingleOrNull()
            if (existingBackend != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Client '$backendClientId' already exists")
            }
        }

        var frontendClient: KcClient? = null
        var backendClient: KcClient? = null

        // Create frontend client (public)
        if (request.applicationType != ApplicationType.BACKEND_ONLY) {
            val webClientRep = ClientRepresentation(
                clientId = webClientId,
                name = request.displayName?.let { "$it (Web)" },
                description = request.description,
                publicClient = true,
                standardFlowEnabled = true,
                directAccessGrantsEnabled = false,
                serviceAccountsEnabled = false,
                rootUrl = request.rootUrl,
                baseUrl = request.baseUrl,
                redirectUris = request.redirectUris.ifEmpty { null },
                webOrigins = request.webOrigins.ifEmpty { null }
            )

            val webKeycloakId = keycloakClient.createClient(realmName, webClientRep)

            frontendClient = clientRepository.save(
                KcClient(
                    realmId = realm.id!!,
                    clientId = webClientId,
                    name = webClientRep.name,
                    description = request.description,
                    enabled = true,
                    publicClient = true,
                    rootUrl = request.rootUrl,
                    baseUrl = request.baseUrl,
                    redirectUris = Json.of(objectMapper.writeValueAsString(request.redirectUris)),
                    webOrigins = Json.of(objectMapper.writeValueAsString(request.webOrigins)),
                    standardFlowEnabled = true,
                    directAccessGrantsEnabled = false,
                    serviceAccountsEnabled = false,
                    keycloakId = webKeycloakId
                )
            ).awaitSingle()

            logger.info("Created frontend client: $webClientId in realm: $realmName")
        }

        // Create backend client (confidential)
        if (request.applicationType != ApplicationType.FRONTEND_ONLY) {
            val backendClientRep = ClientRepresentation(
                clientId = backendClientId,
                name = request.displayName?.let { "$it (Backend)" },
                description = request.description,
                publicClient = false,
                standardFlowEnabled = false,
                directAccessGrantsEnabled = false,
                serviceAccountsEnabled = true,
                rootUrl = null,
                baseUrl = null,
                redirectUris = null,
                webOrigins = null
            )

            val backendKeycloakId = keycloakClient.createClient(realmName, backendClientRep)

            backendClient = clientRepository.save(
                KcClient(
                    realmId = realm.id!!,
                    clientId = backendClientId,
                    name = backendClientRep.name,
                    description = request.description,
                    enabled = true,
                    publicClient = false,
                    rootUrl = null,
                    baseUrl = null,
                    redirectUris = Json.of("[]"),
                    webOrigins = Json.of("[]"),
                    standardFlowEnabled = false,
                    directAccessGrantsEnabled = false,
                    serviceAccountsEnabled = true,
                    keycloakId = backendKeycloakId,
                    pairedClientId = frontendClient?.id
                )
            ).awaitSingle()

            logger.info("Created backend client: $backendClientId in realm: $realmName")
        }

        // Link frontend to backend
        if (frontendClient != null && backendClient != null) {
            frontendClient = clientRepository.save(
                frontendClient.copy(pairedClientId = backendClient.id)
            ).awaitSingle()
        }

        // Log audit trails
        if (actor != null) {
            if (frontendClient != null) {
                auditService.logAction(
                    actor = actor,
                    actionType = ActionType.CREATE,
                    entityType = EntityType.CLIENT,
                    entityId = frontendClient.id!!,
                    entityName = frontendClient.clientId,
                    realmName = realmName,
                    realmId = realm.id,
                    entityKeycloakId = frontendClient.keycloakId,
                    afterState = frontendClient.toAuditState()
                )
            }
            if (backendClient != null) {
                auditService.logAction(
                    actor = actor,
                    actionType = ActionType.CREATE,
                    entityType = EntityType.CLIENT,
                    entityId = backendClient.id!!,
                    entityName = backendClient.clientId,
                    realmName = realmName,
                    realmId = realm.id,
                    entityKeycloakId = backendClient.keycloakId,
                    afterState = backendClient.toAuditState()
                )
            }
        }

        return ApplicationResponse(
            frontendClient = frontendClient?.toDetailResponse(),
            backendClient = backendClient?.toDetailResponse()
        )
    }

    /**
     * Generate integration code snippets for a client (frontend or backend)
     */
    suspend fun getIntegrationSnippets(realmName: String, clientId: String): IntegrationSnippetsResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val client = clientRepository.findByRealmIdAndClientId(realm.id!!, clientId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client '$clientId' not found")

        return if (client.publicClient) {
            // Frontend client - generate JS/React/Vue snippets
            val snippets = IntegrationSnippetGenerator.generateFrontend(keycloakBaseUrl, realmName, clientId)
            IntegrationSnippetsResponse(
                keycloakUrl = keycloakBaseUrl,
                realmName = realmName,
                clientId = clientId,
                isPublicClient = true,
                snippets = snippets
            )
        } else {
            // Backend client - generate Spring Boot snippets
            // Assume auth backend URL is same origin or configured
            val authBackendUrl = "\${AUTH_BACKEND_URL:http://localhost:8080}"
            val backendSnippets = IntegrationSnippetGenerator.generateBackend(
                keycloakBaseUrl, realmName, clientId, authBackendUrl
            )
            IntegrationSnippetsResponse(
                keycloakUrl = keycloakBaseUrl,
                realmName = realmName,
                clientId = clientId,
                isPublicClient = false,
                backendSnippets = backendSnippets
            )
        }
    }

    private fun KcClient.toResponse() = ClientResponse(
        id = id!!,
        clientId = clientId,
        name = name,
        enabled = enabled,
        publicClient = publicClient
    )

    private suspend fun KcClient.toDetailResponse(): ClientDetailResponse {
        val redirectUrisList = try {
            objectMapper.readValue(redirectUris.asString(), object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            emptyList()
        }
        val webOriginsList = try {
            objectMapper.readValue(webOrigins.asString(), object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            emptyList()
        }

        // Get paired client info if exists
        val pairedClientClientId = pairedClientId?.let { id ->
            clientRepository.findById(id).awaitSingleOrNull()?.clientId
        }

        return ClientDetailResponse(
            id = id!!,
            clientId = clientId,
            name = name,
            description = description,
            enabled = enabled,
            publicClient = publicClient,
            standardFlowEnabled = standardFlowEnabled,
            directAccessGrantsEnabled = directAccessGrantsEnabled,
            serviceAccountsEnabled = serviceAccountsEnabled,
            rootUrl = rootUrl,
            baseUrl = baseUrl,
            redirectUris = redirectUrisList,
            webOrigins = webOriginsList,
            pairedClientId = pairedClientId,
            pairedClientClientId = pairedClientClientId,
            createdAt = createdAt
        )
    }

    private fun KcClient.toAuditState(): Map<String, Any?> {
        val redirectUrisList = try {
            objectMapper.readValue(redirectUris.asString(), object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            emptyList<String>()
        }
        val webOriginsList = try {
            objectMapper.readValue(webOrigins.asString(), object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            emptyList<String>()
        }

        return mapOf(
            "id" to id,
            "clientId" to clientId,
            "name" to name,
            "description" to description,
            "enabled" to enabled,
            "publicClient" to publicClient,
            "standardFlowEnabled" to standardFlowEnabled,
            "directAccessGrantsEnabled" to directAccessGrantsEnabled,
            "serviceAccountsEnabled" to serviceAccountsEnabled,
            "rootUrl" to rootUrl,
            "baseUrl" to baseUrl,
            "redirectUris" to redirectUrisList,
            "webOrigins" to webOriginsList,
            "keycloakId" to keycloakId
        )
    }
}

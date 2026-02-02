package com.keeplearning.auth.realm.service

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.keeplearning.auth.config.KeycloakSpiProperties
import com.keeplearning.auth.domain.entity.KcRealm
import com.keeplearning.auth.domain.repository.*
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.ClientRepresentation
import com.keeplearning.auth.keycloak.client.dto.RealmRepresentation
import com.keeplearning.auth.keycloak.client.dto.RoleRepresentation
import com.keeplearning.auth.keycloak.sync.KeycloakSyncService
import com.keeplearning.auth.realm.dto.*
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class RealmService(
    private val keycloakClient: KeycloakAdminClient,
    private val syncService: KeycloakSyncService,
    private val spiProperties: KeycloakSpiProperties,
    private val realmRepository: KcRealmRepository,
    private val clientRepository: KcClientRepository,
    private val roleRepository: KcRoleRepository,
    private val groupRepository: KcGroupRepository,
    private val userStorageProviderRepository: KcUserStorageProviderRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(RealmService::class.java)

    suspend fun createRealm(request: CreateRealmRequest): RealmResponse {
        // Check if realm already exists
        if (realmRepository.existsByRealmName(request.realmName).awaitSingle()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Realm '${request.realmName}' already exists")
        }

        // Create realm in Keycloak
        val realmRep = RealmRepresentation(
            realm = request.realmName,
            displayName = request.displayName,
            enabled = true,
            loginWithEmailAllowed = true,
            registrationAllowed = false,
            resetPasswordAllowed = true
        )

        keycloakClient.createRealm(realmRep)

        // Get the created realm to obtain its ID
        val createdRealm = keycloakClient.getRealm(request.realmName)

        // Create default roles if specified
        request.defaultRoles?.forEach { roleName ->
            keycloakClient.createRealmRole(
                request.realmName,
                RoleRepresentation(name = roleName)
            )
        }

        // Create default clients if specified
        request.defaultClients?.forEach { clientReq ->
            val clientRep = ClientRepresentation(
                clientId = clientReq.clientId,
                name = clientReq.name,
                publicClient = clientReq.publicClient,
                redirectUris = clientReq.redirectUris,
                webOrigins = clientReq.webOrigins,
                standardFlowEnabled = true,
                directAccessGrantsEnabled = true
            )
            keycloakClient.createClient(request.realmName, clientRep)
        }

        // Enable User Storage SPI if requested
        val spiApiUrl = if (request.enableUserStorageSpi) {
            val apiUrl = request.spiApiUrl ?: spiProperties.defaultApiUrl
            keycloakClient.createUserStorageProvider(
                request.realmName,
                "kos-auth-storage",
                apiUrl
            )
            apiUrl
        } else null

        // Save realm to local database
        val realm = realmRepository.save(
            KcRealm(
                accountId = request.accountId,
                realmName = request.realmName,
                displayName = request.displayName,
                enabled = true,
                spiEnabled = request.enableUserStorageSpi,
                spiApiUrl = spiApiUrl,
                attributes = objectMapper.writeValueAsString(request.attributes ?: emptyMap<String, String>()),
                keycloakId = createdRealm.id!!,
                syncedAt = Instant.now()
            )
        ).awaitSingle()

        // Sync all entities for the new realm
        syncService.syncRealm(request.realmName)

        logger.info("Created realm: ${request.realmName}")
        return realm.toResponse()
    }

    suspend fun listRealms(): List<RealmResponse> {
        return realmRepository.findAll()
            .collectList()
            .awaitSingle()
            .map { it.toResponse() }
    }

    suspend fun getRealm(realmName: String): RealmDetailResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val clients = clientRepository.findByRealmId(realm.id!!)
            .collectList().awaitSingle()
        val roles = roleRepository.findByRealmIdAndClientIdIsNull(realm.id)
            .collectList().awaitSingle()
        val groups = groupRepository.findByRealmIdAndParentIdIsNull(realm.id)
            .collectList().awaitSingle()
        val providers = userStorageProviderRepository.findByRealmId(realm.id)
            .collectList().awaitSingle()

        val attributes: Map<String, Any>? = if (realm.attributes != "{}") {
            objectMapper.readValue(realm.attributes, object : TypeReference<Map<String, Any>>() {})
        } else null

        return RealmDetailResponse(
            id = realm.id,
            realmName = realm.realmName,
            displayName = realm.displayName,
            enabled = realm.enabled,
            spiEnabled = realm.spiEnabled,
            spiApiUrl = realm.spiApiUrl,
            accountId = realm.accountId,
            attributes = attributes,
            clients = clients.map { client ->
                ClientResponse(
                    id = client.id!!,
                    clientId = client.clientId,
                    name = client.name,
                    enabled = client.enabled,
                    publicClient = client.publicClient
                )
            },
            roles = roles.map { role ->
                RoleResponse(
                    id = role.id!!,
                    name = role.name,
                    description = role.description,
                    composite = role.composite
                )
            },
            groups = groups.map { group ->
                GroupResponse(
                    id = group.id!!,
                    name = group.name,
                    path = group.path
                )
            },
            userStorageProviders = providers.map { provider ->
                UserStorageProviderResponse(
                    id = provider.id!!,
                    name = provider.name,
                    providerId = provider.providerId,
                    priority = provider.priority
                )
            },
            createdAt = realm.createdAt,
            syncedAt = realm.syncedAt
        )
    }

    suspend fun updateRealm(realmName: String, request: UpdateRealmRequest): RealmResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Update in Keycloak
        val currentRealm = keycloakClient.getRealm(realmName)
        val updatedRealmRep = currentRealm.copy(
            displayName = request.displayName ?: currentRealm.displayName,
            enabled = request.enabled ?: currentRealm.enabled
        )
        keycloakClient.updateRealm(realmName, updatedRealmRep)

        // Handle SPI enablement changes
        var spiApiUrl = realm.spiApiUrl
        if (request.spiEnabled == true && !realm.spiEnabled) {
            // Enable SPI
            spiApiUrl = request.spiApiUrl ?: spiProperties.defaultApiUrl
            keycloakClient.createUserStorageProvider(realmName, "kos-auth-storage", spiApiUrl)
        } else if (request.spiEnabled == false && realm.spiEnabled) {
            // Disable SPI - find and delete the component
            val providers = keycloakClient.getUserStorageProviders(realmName)
            providers.forEach { provider ->
                if (provider.providerId == spiProperties.providerId) {
                    keycloakClient.deleteComponent(realmName, provider.id!!)
                }
            }
            spiApiUrl = null
        }

        // Update local database
        val attributes = if (request.attributes != null) {
            objectMapper.writeValueAsString(request.attributes)
        } else realm.attributes

        val updatedRealm = realmRepository.save(
            realm.copy(
                displayName = request.displayName ?: realm.displayName,
                enabled = request.enabled ?: realm.enabled,
                spiEnabled = request.spiEnabled ?: realm.spiEnabled,
                spiApiUrl = spiApiUrl,
                attributes = attributes,
                updatedAt = Instant.now(),
                syncedAt = Instant.now()
            )
        ).awaitSingle()

        logger.info("Updated realm: $realmName")
        return updatedRealm.toResponse()
    }

    suspend fun deleteRealm(realmName: String) {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Delete from Keycloak
        keycloakClient.deleteRealm(realmName)

        // Delete from local database (cascade will clean up related entities)
        realmRepository.delete(realm).awaitSingleOrNull()

        logger.info("Deleted realm: $realmName")
    }

    suspend fun enableSpi(realmName: String, request: EnableSpiRequest): RealmResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        if (realm.spiEnabled) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "SPI is already enabled for realm '$realmName'")
        }

        val apiUrl = request.apiUrl ?: spiProperties.defaultApiUrl
        keycloakClient.createUserStorageProvider(realmName, "kos-auth-storage", apiUrl)

        val updatedRealm = realmRepository.save(
            realm.copy(
                spiEnabled = true,
                spiApiUrl = apiUrl,
                updatedAt = Instant.now(),
                syncedAt = Instant.now()
            )
        ).awaitSingle()

        // Sync to get the provider ID
        syncService.syncRealm(realmName)

        logger.info("Enabled SPI for realm: $realmName")
        return updatedRealm.toResponse()
    }

    suspend fun triggerSync(realmName: String): SyncResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val result = syncService.syncRealm(realmName)

        // Update sync timestamp
        realmRepository.save(realm.copy(syncedAt = Instant.now())).awaitSingle()

        return SyncResponse(
            realmName = realmName,
            clientsProcessed = result.clients.count,
            rolesProcessed = result.roles.count,
            groupsProcessed = result.groups.count,
            userStorageProvidersProcessed = result.userStorageProviders.count,
            success = result.success
        )
    }

    private fun KcRealm.toResponse() = RealmResponse(
        id = id!!,
        realmName = realmName,
        displayName = displayName,
        enabled = enabled,
        spiEnabled = spiEnabled,
        accountId = accountId,
        createdAt = createdAt
    )
}

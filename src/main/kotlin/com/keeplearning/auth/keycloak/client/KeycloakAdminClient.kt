package com.keeplearning.auth.keycloak.client

import com.keeplearning.auth.config.KeycloakAdminProperties
import com.keeplearning.auth.config.KeycloakSpiProperties
import com.keeplearning.auth.keycloak.client.dto.*
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Service
class KeycloakAdminClient(
    private val webClient: WebClient,
    private val adminProperties: KeycloakAdminProperties,
    private val spiProperties: KeycloakSpiProperties
) {
    private val logger = LoggerFactory.getLogger(KeycloakAdminClient::class.java)

    private data class CachedToken(val token: String, val expiresAt: Instant)
    private val tokenCache = AtomicReference<CachedToken?>(null)

    private suspend fun getAccessToken(): String {
        val cached = tokenCache.get()
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(30))) {
            return cached.token
        }

        val response = webClient.post()
            .uri("${adminProperties.baseUrl}/realms/${adminProperties.realm}/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("grant_type", "client_credentials")
                    .with("client_id", adminProperties.clientId)
                    .with("client_secret", adminProperties.clientSecret)
            )
            .retrieve()
            .bodyToMono<TokenResponse>()
            .awaitSingle()

        val expiresAt = Instant.now().plusSeconds(response.expiresIn - 60)
        tokenCache.set(CachedToken(response.accessToken, expiresAt))
        return response.accessToken
    }

    private suspend fun adminClient(): WebClient {
        val token = getAccessToken()
        return webClient.mutate()
            .defaultHeader("Authorization", "Bearer $token")
            .build()
    }

    // ==================== REALM OPERATIONS ====================

    suspend fun getRealms(): List<RealmRepresentation> {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms")
            .retrieve()
            .bodyToFlux<RealmRepresentation>()
            .collectList()
            .awaitSingle()
    }

    suspend fun getRealm(realmName: String): RealmRepresentation {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName")
            .retrieve()
            .bodyToMono<RealmRepresentation>()
            .awaitSingle()
    }

    suspend fun getRealmOrNull(realmName: String): RealmRepresentation? {
        return try {
            getRealm(realmName)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createRealm(realm: RealmRepresentation) {
        val client = adminClient()
        client.post()
            .uri("${adminProperties.baseUrl}/admin/realms")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(realm)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Created realm: ${realm.realm}")
    }

    suspend fun updateRealm(realmName: String, realm: RealmRepresentation) {
        val client = adminClient()
        client.put()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(realm)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
    }

    suspend fun deleteRealm(realmName: String) {
        val client = adminClient()
        client.delete()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName")
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Deleted realm: $realmName")
    }

    // ==================== CLIENT OPERATIONS ====================

    suspend fun getClients(realmName: String): List<ClientRepresentation> {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients")
            .retrieve()
            .bodyToFlux<ClientRepresentation>()
            .collectList()
            .awaitSingle()
    }

    suspend fun createClient(realmName: String, clientRep: ClientRepresentation): String {
        val client = adminClient()
        val response = client.post()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(clientRep)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()

        logger.info("Created client: ${clientRep.clientId} in realm: $realmName")
        return response.headers.location?.path?.substringAfterLast("/") ?: ""
    }

    // ==================== ROLE OPERATIONS ====================

    suspend fun getRealmRoles(realmName: String): List<RoleRepresentation> {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/roles")
            .retrieve()
            .bodyToFlux<RoleRepresentation>()
            .collectList()
            .awaitSingle()
    }

    suspend fun getClientRoles(realmName: String, clientId: String): List<RoleRepresentation> {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients/$clientId/roles")
            .retrieve()
            .bodyToFlux<RoleRepresentation>()
            .collectList()
            .awaitSingle()
    }

    suspend fun createRealmRole(realmName: String, role: RoleRepresentation) {
        val client = adminClient()
        client.post()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/roles")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(role)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
    }

    // ==================== GROUP OPERATIONS ====================

    suspend fun getGroups(realmName: String): List<GroupRepresentation> {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/groups")
            .retrieve()
            .bodyToFlux<GroupRepresentation>()
            .collectList()
            .awaitSingle()
    }

    // ==================== USER STORAGE PROVIDER OPERATIONS ====================

    suspend fun getComponents(realmName: String, type: String? = null): List<ComponentRepresentation> {
        val client = adminClient()
        val uri = if (type != null) {
            "${adminProperties.baseUrl}/admin/realms/$realmName/components?type=$type"
        } else {
            "${adminProperties.baseUrl}/admin/realms/$realmName/components"
        }
        return client.get()
            .uri(uri)
            .retrieve()
            .bodyToFlux<ComponentRepresentation>()
            .collectList()
            .awaitSingle()
    }

    suspend fun getUserStorageProviders(realmName: String): List<ComponentRepresentation> {
        return getComponents(realmName, "org.keycloak.storage.UserStorageProvider")
    }

    suspend fun createUserStorageProvider(
        realmName: String,
        name: String,
        apiUrl: String
    ): String {
        val realm = getRealm(realmName)
        val component = ComponentRepresentation(
            name = name,
            providerId = spiProperties.providerId,
            providerType = "org.keycloak.storage.UserStorageProvider",
            parentId = realm.id,
            config = mapOf(
                "priority" to listOf("0"),
                "enabled" to listOf("true"),
                "cachePolicy" to listOf("DEFAULT"),
                "apiUrl" to listOf(apiUrl)
            )
        )

        val client = adminClient()
        val response = client.post()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/components")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(component)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()

        logger.info("Created user storage provider in realm: $realmName")
        return response.headers.location?.path?.substringAfterLast("/") ?: ""
    }

    suspend fun deleteComponent(realmName: String, componentId: String) {
        val client = adminClient()
        client.delete()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/components/$componentId")
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
    }

    // ==================== USER OPERATIONS ====================

    suspend fun getUsers(realmName: String, max: Int = 100): List<UserRepresentation> {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/users?max=$max")
            .retrieve()
            .bodyToFlux<UserRepresentation>()
            .collectList()
            .awaitSingle()
    }

    suspend fun getUserByEmail(realmName: String, email: String): UserRepresentation? {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/users?email=$email&exact=true")
            .retrieve()
            .bodyToFlux<UserRepresentation>()
            .next()
            .awaitSingleOrNull()
    }

    // ==================== HEALTH CHECK ====================

    suspend fun healthCheck(): Boolean {
        return try {
            val client = adminClient()
            client.get()
                .uri("${adminProperties.baseUrl}/admin/realms")
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
            true
        } catch (e: Exception) {
            logger.warn("Keycloak health check failed: ${e.message}")
            false
        }
    }
}

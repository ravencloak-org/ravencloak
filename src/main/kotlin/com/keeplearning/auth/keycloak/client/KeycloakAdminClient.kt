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

    suspend fun getClient(realmName: String, clientUuid: String): ClientRepresentation {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients/$clientUuid")
            .retrieve()
            .bodyToMono<ClientRepresentation>()
            .awaitSingle()
    }

    suspend fun getClientByClientId(realmName: String, clientId: String): ClientRepresentation? {
        val clients = getClients(realmName)
        return clients.find { it.clientId == clientId }
    }

    suspend fun updateClient(realmName: String, clientUuid: String, clientRep: ClientRepresentation) {
        val client = adminClient()
        client.put()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients/$clientUuid")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(clientRep)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Updated client: ${clientRep.clientId} in realm: $realmName")
    }

    suspend fun deleteClient(realmName: String, clientUuid: String) {
        val client = adminClient()
        client.delete()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients/$clientUuid")
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Deleted client: $clientUuid from realm: $realmName")
    }

    suspend fun getClientSecret(realmName: String, clientUuid: String): String? {
        val client = adminClient()
        return try {
            val response = client.get()
                .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients/$clientUuid/client-secret")
                .retrieve()
                .bodyToMono<ClientSecretResponse>()
                .awaitSingle()
            response.value
        } catch (e: Exception) {
            logger.warn("Failed to get client secret: ${e.message}")
            null
        }
    }

    suspend fun regenerateClientSecret(realmName: String, clientUuid: String): String {
        val client = adminClient()
        val response = client.post()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients/$clientUuid/client-secret")
            .retrieve()
            .bodyToMono<ClientSecretResponse>()
            .awaitSingle()
        logger.info("Regenerated client secret for client: $clientUuid in realm: $realmName")
        return response.value
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
        logger.info("Created realm role: ${role.name} in realm: $realmName")
    }

    suspend fun getRealmRole(realmName: String, roleName: String): RoleRepresentation {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/roles/$roleName")
            .retrieve()
            .bodyToMono<RoleRepresentation>()
            .awaitSingle()
    }

    suspend fun updateRealmRole(realmName: String, roleName: String, role: RoleRepresentation) {
        val client = adminClient()
        client.put()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/roles/$roleName")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(role)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Updated realm role: $roleName in realm: $realmName")
    }

    suspend fun deleteRealmRole(realmName: String, roleName: String) {
        val client = adminClient()
        client.delete()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/roles/$roleName")
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Deleted realm role: $roleName from realm: $realmName")
    }

    suspend fun createClientRole(realmName: String, clientUuid: String, role: RoleRepresentation) {
        val client = adminClient()
        client.post()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients/$clientUuid/roles")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(role)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Created client role: ${role.name} for client: $clientUuid in realm: $realmName")
    }

    suspend fun deleteClientRole(realmName: String, clientUuid: String, roleName: String) {
        val client = adminClient()
        client.delete()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/clients/$clientUuid/roles/$roleName")
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Deleted client role: $roleName from client: $clientUuid in realm: $realmName")
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

    suspend fun getGroup(realmName: String, groupId: String): GroupRepresentation {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/groups/$groupId")
            .retrieve()
            .bodyToMono<GroupRepresentation>()
            .awaitSingle()
    }

    suspend fun createGroup(realmName: String, group: GroupRepresentation): String {
        val client = adminClient()
        val response = client.post()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/groups")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(group)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Created group: ${group.name} in realm: $realmName")
        return response.headers.location?.path?.substringAfterLast("/") ?: ""
    }

    suspend fun createSubgroup(realmName: String, parentGroupId: String, group: GroupRepresentation): String {
        val client = adminClient()
        val response = client.post()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/groups/$parentGroupId/children")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(group)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Created subgroup: ${group.name} under parent: $parentGroupId in realm: $realmName")
        return response.headers.location?.path?.substringAfterLast("/") ?: ""
    }

    suspend fun updateGroup(realmName: String, groupId: String, group: GroupRepresentation) {
        val client = adminClient()
        client.put()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/groups/$groupId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(group)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Updated group: $groupId in realm: $realmName")
    }

    suspend fun deleteGroup(realmName: String, groupId: String) {
        val client = adminClient()
        client.delete()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/groups/$groupId")
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Deleted group: $groupId from realm: $realmName")
    }

    // ==================== IDENTITY PROVIDER OPERATIONS ====================

    suspend fun getIdentityProviders(realmName: String): List<IdentityProviderRepresentation> {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/identity-provider/instances")
            .retrieve()
            .bodyToFlux<IdentityProviderRepresentation>()
            .collectList()
            .awaitSingle()
    }

    suspend fun getIdentityProvider(realmName: String, alias: String): IdentityProviderRepresentation {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/identity-provider/instances/$alias")
            .retrieve()
            .bodyToMono<IdentityProviderRepresentation>()
            .awaitSingle()
    }

    suspend fun createIdentityProvider(realmName: String, idp: IdentityProviderRepresentation) {
        val client = adminClient()
        client.post()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/identity-provider/instances")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(idp)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Created identity provider: ${idp.alias} in realm: $realmName")
    }

    suspend fun updateIdentityProvider(realmName: String, alias: String, idp: IdentityProviderRepresentation) {
        val client = adminClient()
        client.put()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/identity-provider/instances/$alias")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(idp)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Updated identity provider: $alias in realm: $realmName")
    }

    suspend fun deleteIdentityProvider(realmName: String, alias: String) {
        val client = adminClient()
        client.delete()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/identity-provider/instances/$alias")
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Deleted identity provider: $alias from realm: $realmName")
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

    // ==================== USER ROLE MAPPING OPERATIONS ====================

    suspend fun getUserClientRoleMappings(
        realmName: String,
        userId: String,
        clientUuid: String
    ): List<RoleRepresentation> {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/users/$userId/role-mappings/clients/$clientUuid")
            .retrieve()
            .bodyToFlux<RoleRepresentation>()
            .collectList()
            .awaitSingle()
    }

    suspend fun addUserClientRoleMappings(
        realmName: String,
        userId: String,
        clientUuid: String,
        roles: List<RoleRepresentation>
    ) {
        val client = adminClient()
        client.post()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/users/$userId/role-mappings/clients/$clientUuid")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(roles)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Added ${roles.size} client role(s) to user: $userId in realm: $realmName")
    }

    suspend fun removeUserClientRoleMappings(
        realmName: String,
        userId: String,
        clientUuid: String,
        roles: List<RoleRepresentation>
    ) {
        val client = adminClient()
        client.method(org.springframework.http.HttpMethod.DELETE)
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/users/$userId/role-mappings/clients/$clientUuid")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(roles)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Removed ${roles.size} client role(s) from user: $userId in realm: $realmName")
    }

    // ==================== SINGLE USER OPERATIONS ====================

    suspend fun getUser(realmName: String, userId: String): UserRepresentation {
        val client = adminClient()
        return client.get()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/users/$userId")
            .retrieve()
            .bodyToMono<UserRepresentation>()
            .awaitSingle()
    }

    suspend fun updateUser(realmName: String, userId: String, user: UserRepresentation) {
        val client = adminClient()
        client.put()
            .uri("${adminProperties.baseUrl}/admin/realms/$realmName/users/$userId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(user)
            .retrieve()
            .toBodilessEntity()
            .awaitSingle()
        logger.info("Updated user: $userId in realm: $realmName")
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

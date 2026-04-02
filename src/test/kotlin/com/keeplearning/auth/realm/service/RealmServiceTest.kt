package com.keeplearning.auth.realm.service

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.keeplearning.auth.config.KeycloakSpiProperties
import com.keeplearning.auth.domain.entity.*
import com.keeplearning.auth.domain.repository.*
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.ComponentRepresentation
import com.keeplearning.auth.keycloak.client.dto.RealmRepresentation
import com.keeplearning.auth.keycloak.client.dto.UserRepresentation
import com.keeplearning.auth.keycloak.sync.EntitySyncResult
import com.keeplearning.auth.keycloak.sync.KeycloakSyncService
import com.keeplearning.auth.keycloak.sync.RealmSyncResult
import com.keeplearning.auth.realm.dto.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class RealmServiceTest {

    @MockK
    lateinit var keycloakClient: KeycloakAdminClient

    @MockK
    lateinit var syncService: KeycloakSyncService

    @MockK
    lateinit var spiProperties: KeycloakSpiProperties

    @MockK
    lateinit var realmRepository: KcRealmRepository

    @MockK
    lateinit var clientRepository: KcClientRepository

    @MockK
    lateinit var roleRepository: KcRoleRepository

    @MockK
    lateinit var groupRepository: KcGroupRepository

    @MockK
    lateinit var userStorageProviderRepository: KcUserStorageProviderRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var objectMapper: ObjectMapper

    private lateinit var service: RealmService

    private val realmId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val now = Instant.parse("2024-06-01T10:00:00Z")

    private val realm = KcRealm(
        id = realmId,
        accountId = accountId,
        realmName = "test-realm",
        displayName = "Test Realm",
        enabled = true,
        spiEnabled = false,
        spiApiUrl = null,
        attributes = Json.of("{}"),
        keycloakId = "kc-realm-id-123",
        syncedAt = now,
        createdAt = now
    )

    private val realmRepresentation = RealmRepresentation(
        id = "kc-realm-id-123",
        realm = "test-realm",
        displayName = "Test Realm",
        enabled = true
    )

    private val defaultSyncResult = RealmSyncResult(
        realmName = "test-realm",
        clients = EntitySyncResult(SyncEntityType.CLIENT, 2, true),
        roles = EntitySyncResult(SyncEntityType.ROLE, 3, true),
        groups = EntitySyncResult(SyncEntityType.GROUP, 1, true),
        userStorageProviders = EntitySyncResult(SyncEntityType.USER, 0, true),
        success = true
    )

    @BeforeEach
    fun setup() {
        service = RealmService(
            keycloakClient,
            syncService,
            spiProperties,
            realmRepository,
            clientRepository,
            roleRepository,
            groupRepository,
            userStorageProviderRepository,
            userRepository,
            objectMapper
        )
    }

    // --- createRealm ---

    @Test
    fun `createRealm creates realm successfully with default roles and clients`() = runTest {
        val request = CreateRealmRequest(
            realmName = "new-realm",
            displayName = "New Realm",
            accountId = accountId,
            enableUserStorageSpi = false,
            defaultRoles = listOf("admin", "user"),
            defaultClients = listOf(
                DefaultClientRequest(
                    clientId = "my-app",
                    name = "My App",
                    publicClient = true,
                    redirectUris = listOf("http://localhost:3000/*"),
                    webOrigins = listOf("http://localhost:3000")
                )
            )
        )

        val savedRealm = KcRealm(
            id = realmId,
            accountId = accountId,
            realmName = "new-realm",
            displayName = "New Realm",
            enabled = true,
            spiEnabled = false,
            spiApiUrl = null,
            attributes = Json.of("{}"),
            keycloakId = "kc-new-realm-id",
            syncedAt = now,
            createdAt = now
        )

        every { realmRepository.existsByRealmName("new-realm") } returns Mono.just(false)
        coEvery { keycloakClient.createRealm(any()) } returns Unit
        coEvery { keycloakClient.getRealm("new-realm") } returns RealmRepresentation(
            id = "kc-new-realm-id",
            realm = "new-realm",
            displayName = "New Realm",
            enabled = true
        )
        coEvery { keycloakClient.createRealmRole("new-realm", any()) } returns Unit
        coEvery { keycloakClient.createClient("new-realm", any()) } returns "client-id"
        every { objectMapper.writeValueAsString(any<Map<*, *>>()) } returns "{}"
        every { realmRepository.save(any()) } returns Mono.just(savedRealm)
        coEvery { syncService.syncRealm("new-realm") } returns defaultSyncResult

        val result = service.createRealm(request)

        assertEquals(realmId, result.id)
        assertEquals("new-realm", result.realmName)
        assertEquals("New Realm", result.displayName)
        assertTrue(result.enabled)
        assertEquals(false, result.spiEnabled)
        assertEquals(accountId, result.accountId)

        coVerify(exactly = 2) { keycloakClient.createRealmRole("new-realm", any()) }
        coVerify(exactly = 1) { keycloakClient.createClient("new-realm", any()) }
    }

    @Test
    fun `createRealm throws 409 when realm already exists`() = runTest {
        val request = CreateRealmRequest(realmName = "existing-realm")

        every { realmRepository.existsByRealmName("existing-realm") } returns Mono.just(true)

        val ex = assertThrows<ResponseStatusException> {
            service.createRealm(request)
        }
        assertEquals(409, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("already exists"))
    }

    @Test
    fun `createRealm creates realm with SPI enabled and syncs users`() = runTest {
        val request = CreateRealmRequest(
            realmName = "spi-realm",
            displayName = "SPI Realm",
            accountId = accountId,
            enableUserStorageSpi = true,
            spiApiUrl = "http://custom-api:8080/api/users"
        )

        val savedRealm = KcRealm(
            id = realmId,
            accountId = accountId,
            realmName = "spi-realm",
            displayName = "SPI Realm",
            enabled = true,
            spiEnabled = true,
            spiApiUrl = "http://custom-api:8080/api/users",
            attributes = Json.of("{}"),
            keycloakId = "kc-spi-realm-id",
            syncedAt = now,
            createdAt = now
        )

        every { realmRepository.existsByRealmName("spi-realm") } returns Mono.just(false)
        coEvery { keycloakClient.createRealm(any()) } returns Unit
        coEvery { keycloakClient.getRealm("spi-realm") } returns RealmRepresentation(
            id = "kc-spi-realm-id",
            realm = "spi-realm",
            displayName = "SPI Realm",
            enabled = true
        )
        coEvery { keycloakClient.createUserStorageProvider("spi-realm", "kos-auth-storage", "http://custom-api:8080/api/users") } returns "provider-id"
        every { objectMapper.writeValueAsString(any<Map<*, *>>()) } returns "{}"
        every { realmRepository.save(any()) } returns Mono.just(savedRealm)
        coEvery { syncService.syncRealm("spi-realm") } returns defaultSyncResult.copy(realmName = "spi-realm")
        // Mock syncUsersFromKeycloak internals
        coEvery { keycloakClient.getUsers("spi-realm", max = 10000) } returns emptyList()

        val result = service.createRealm(request)

        assertEquals(realmId, result.id)
        assertEquals("spi-realm", result.realmName)
        assertTrue(result.spiEnabled)

        coVerify { keycloakClient.createUserStorageProvider("spi-realm", "kos-auth-storage", "http://custom-api:8080/api/users") }
        coVerify { keycloakClient.getUsers("spi-realm", max = 10000) }
    }

    @Test
    fun `createRealm creates realm without optional fields`() = runTest {
        val request = CreateRealmRequest(
            realmName = "minimal-realm"
        )

        val savedRealm = KcRealm(
            id = realmId,
            accountId = null,
            realmName = "minimal-realm",
            displayName = null,
            enabled = true,
            spiEnabled = false,
            spiApiUrl = null,
            attributes = Json.of("{}"),
            keycloakId = "kc-minimal-id",
            syncedAt = now,
            createdAt = now
        )

        every { realmRepository.existsByRealmName("minimal-realm") } returns Mono.just(false)
        coEvery { keycloakClient.createRealm(any()) } returns Unit
        coEvery { keycloakClient.getRealm("minimal-realm") } returns RealmRepresentation(
            id = "kc-minimal-id",
            realm = "minimal-realm",
            enabled = true
        )
        every { objectMapper.writeValueAsString(any<Map<*, *>>()) } returns "{}"
        every { realmRepository.save(any()) } returns Mono.just(savedRealm)
        coEvery { syncService.syncRealm("minimal-realm") } returns defaultSyncResult.copy(realmName = "minimal-realm")

        val result = service.createRealm(request)

        assertEquals("minimal-realm", result.realmName)
        assertEquals(false, result.spiEnabled)

        // No roles or clients created
        coVerify(exactly = 0) { keycloakClient.createRealmRole(any(), any()) }
        coVerify(exactly = 0) { keycloakClient.createClient(any(), any()) }
        coVerify(exactly = 0) { keycloakClient.createUserStorageProvider(any(), any(), any()) }
    }

    // --- listRealms ---

    @Test
    fun `listRealms returns all realms mapped to responses`() = runTest {
        val realm2 = realm.copy(
            id = UUID.randomUUID(),
            realmName = "second-realm",
            displayName = "Second Realm"
        )

        every { realmRepository.findAll() } returns Flux.just(realm, realm2)

        val result = service.listRealms()

        assertEquals(2, result.size)
        assertEquals("test-realm", result[0].realmName)
        assertEquals("Test Realm", result[0].displayName)
        assertEquals("second-realm", result[1].realmName)
        assertEquals("Second Realm", result[1].displayName)
    }

    @Test
    fun `listRealms returns empty list when no realms`() = runTest {
        every { realmRepository.findAll() } returns Flux.empty()

        val result = service.listRealms()

        assertTrue(result.isEmpty())
    }

    // --- getRealm ---

    @Test
    fun `getRealm returns detail response with clients roles groups and providers`() = runTest {
        val clientId = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val providerId = UUID.randomUUID()

        val client = KcClient(
            id = clientId,
            realmId = realmId,
            clientId = "my-app",
            name = "My App",
            enabled = true,
            publicClient = true,
            keycloakId = "kc-client-id"
        )
        val role = KcRole(
            id = roleId,
            realmId = realmId,
            clientId = null,
            name = "admin",
            description = "Admin role",
            composite = false,
            keycloakId = "kc-role-id"
        )
        val group = KcGroup(
            id = groupId,
            realmId = realmId,
            parentId = null,
            name = "staff",
            path = "/staff",
            keycloakId = "kc-group-id"
        )
        val provider = KcUserStorageProvider(
            id = providerId,
            realmId = realmId,
            name = "kos-auth-storage",
            providerId = "kos-auth-storage",
            priority = 0,
            keycloakId = "kc-provider-id"
        )

        val realmWithAttrs = realm.copy(attributes = Json.of("""{"env":"prod"}"""))

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realmWithAttrs)
        every { clientRepository.findByRealmId(realmId) } returns Flux.just(client)
        every { roleRepository.findByRealmIdAndClientIdIsNull(realmId) } returns Flux.just(role)
        every { groupRepository.findByRealmIdAndParentIdIsNull(realmId) } returns Flux.just(group)
        every { userStorageProviderRepository.findByRealmId(realmId) } returns Flux.just(provider)
        every { objectMapper.readValue(any<String>(), any<TypeReference<Map<String, Any>>>()) } returns mapOf("env" to "prod")

        val result = service.getRealm("test-realm")

        assertEquals(realmId, result.id)
        assertEquals("test-realm", result.realmName)
        assertEquals("Test Realm", result.displayName)
        assertTrue(result.enabled)
        assertEquals(1, result.clients.size)
        assertEquals("my-app", result.clients[0].clientId)
        assertEquals("My App", result.clients[0].name)
        assertEquals(1, result.roles.size)
        assertEquals("admin", result.roles[0].name)
        assertEquals("Admin role", result.roles[0].description)
        assertEquals(1, result.groups.size)
        assertEquals("staff", result.groups[0].name)
        assertEquals("/staff", result.groups[0].path)
        assertEquals(1, result.userStorageProviders.size)
        assertEquals("kos-auth-storage", result.userStorageProviders[0].name)
        assertNotNull(result.attributes)
        assertEquals("prod", result.attributes!!["env"])
    }

    @Test
    fun `getRealm throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.getRealm("nonexistent")
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("not found"))
    }

    @Test
    fun `getRealm filters out default Keycloak clients`() = runTest {
        val customClient = KcClient(
            id = UUID.randomUUID(),
            realmId = realmId,
            clientId = "my-custom-app",
            name = "Custom App",
            enabled = true,
            publicClient = true,
            keycloakId = "kc-custom-client"
        )
        val defaultClient = KcClient(
            id = UUID.randomUUID(),
            realmId = realmId,
            clientId = "account",
            name = "Account",
            enabled = true,
            publicClient = false,
            keycloakId = "kc-account-client"
        )
        val adminCliClient = KcClient(
            id = UUID.randomUUID(),
            realmId = realmId,
            clientId = "admin-cli",
            name = "Admin CLI",
            enabled = true,
            publicClient = false,
            keycloakId = "kc-admin-cli-client"
        )

        val realmWithEmptyAttrs = realm.copy(attributes = Json.of("{}"))

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realmWithEmptyAttrs)
        every { clientRepository.findByRealmId(realmId) } returns Flux.just(customClient, defaultClient, adminCliClient)
        every { roleRepository.findByRealmIdAndClientIdIsNull(realmId) } returns Flux.empty()
        every { groupRepository.findByRealmIdAndParentIdIsNull(realmId) } returns Flux.empty()
        every { userStorageProviderRepository.findByRealmId(realmId) } returns Flux.empty()

        val result = service.getRealm("test-realm")

        assertEquals(1, result.clients.size)
        assertEquals("my-custom-app", result.clients[0].clientId)
    }

    // --- updateRealm ---

    @Test
    fun `updateRealm updates realm fields successfully`() = runTest {
        val request = UpdateRealmRequest(
            displayName = "Updated Realm",
            enabled = false,
            attributes = mapOf("env" to "prod")
        )

        val updatedRealm = realm.copy(
            displayName = "Updated Realm",
            enabled = false,
            attributes = Json.of("""{"env":"prod"}"""),
            updatedAt = Instant.now()
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        coEvery { keycloakClient.getRealm("test-realm") } returns realmRepresentation
        coEvery { keycloakClient.updateRealm("test-realm", any()) } returns Unit
        every { objectMapper.writeValueAsString(mapOf("env" to "prod")) } returns """{"env":"prod"}"""
        every { realmRepository.save(any()) } returns Mono.just(updatedRealm)

        val result = service.updateRealm("test-realm", request)

        assertEquals("Updated Realm", result.displayName)
        assertEquals(false, result.enabled)

        coVerify { keycloakClient.updateRealm("test-realm", any()) }
    }

    @Test
    fun `updateRealm enables SPI when previously disabled`() = runTest {
        val request = UpdateRealmRequest(
            spiEnabled = true,
            spiApiUrl = "http://custom-api:8080/api/users"
        )

        val updatedRealm = realm.copy(
            spiEnabled = true,
            spiApiUrl = "http://custom-api:8080/api/users",
            updatedAt = Instant.now()
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        coEvery { keycloakClient.getRealm("test-realm") } returns realmRepresentation
        coEvery { keycloakClient.updateRealm("test-realm", any()) } returns Unit
        coEvery { keycloakClient.createUserStorageProvider("test-realm", "kos-auth-storage", "http://custom-api:8080/api/users") } returns "provider-id"
        every { realmRepository.save(any()) } returns Mono.just(updatedRealm)
        // Mock syncUsersFromKeycloak
        coEvery { keycloakClient.getUsers("test-realm", max = 10000) } returns emptyList()

        val result = service.updateRealm("test-realm", request)

        assertTrue(result.spiEnabled)
        coVerify { keycloakClient.createUserStorageProvider("test-realm", "kos-auth-storage", "http://custom-api:8080/api/users") }
    }

    @Test
    fun `updateRealm disables SPI when previously enabled`() = runTest {
        val spiRealm = realm.copy(
            spiEnabled = true,
            spiApiUrl = "http://api:8080/api/users"
        )

        val request = UpdateRealmRequest(spiEnabled = false)

        val updatedRealm = spiRealm.copy(
            spiEnabled = false,
            spiApiUrl = null,
            updatedAt = Instant.now()
        )

        val providerComponent = ComponentRepresentation(
            id = "provider-component-id",
            name = "kos-auth-storage",
            providerId = "kos-auth-storage"
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(spiRealm)
        coEvery { keycloakClient.getRealm("test-realm") } returns realmRepresentation
        coEvery { keycloakClient.updateRealm("test-realm", any()) } returns Unit
        coEvery { keycloakClient.getUserStorageProviders("test-realm") } returns listOf(providerComponent)
        every { spiProperties.providerId } returns "kos-auth-storage"
        coEvery { keycloakClient.deleteComponent("test-realm", "provider-component-id") } returns Unit
        every { realmRepository.save(any()) } returns Mono.just(updatedRealm)

        val result = service.updateRealm("test-realm", request)

        assertEquals(false, result.spiEnabled)
        coVerify { keycloakClient.deleteComponent("test-realm", "provider-component-id") }
    }

    @Test
    fun `updateRealm throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val request = UpdateRealmRequest(displayName = "Updated")

        val ex = assertThrows<ResponseStatusException> {
            service.updateRealm("nonexistent", request)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("not found"))
    }

    @Test
    fun `updateRealm preserves existing fields when request fields are null`() = runTest {
        val request = UpdateRealmRequest(
            displayName = null,
            enabled = null,
            spiEnabled = null,
            spiApiUrl = null,
            attributes = null
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        coEvery { keycloakClient.getRealm("test-realm") } returns realmRepresentation
        coEvery { keycloakClient.updateRealm("test-realm", any()) } returns Unit
        every { realmRepository.save(any()) } answers { Mono.just(firstArg<KcRealm>()) }

        val result = service.updateRealm("test-realm", request)

        assertEquals("Test Realm", result.displayName)
        assertTrue(result.enabled)
        assertEquals(false, result.spiEnabled)
    }

    // --- deleteRealm ---

    @Test
    fun `deleteRealm deletes realm from Keycloak and DB`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        coEvery { keycloakClient.deleteRealm("test-realm") } returns Unit
        every { realmRepository.delete(realm) } returns Mono.empty()

        service.deleteRealm("test-realm")

        coVerify { keycloakClient.deleteRealm("test-realm") }
    }

    @Test
    fun `deleteRealm throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.deleteRealm("nonexistent")
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("not found"))
    }

    // --- enableSpi ---

    @Test
    fun `enableSpi enables SPI for realm successfully`() = runTest {
        val request = EnableSpiRequest(apiUrl = "http://custom-api:8080/api/users")

        val updatedRealm = realm.copy(
            spiEnabled = true,
            spiApiUrl = "http://custom-api:8080/api/users",
            updatedAt = Instant.now()
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        coEvery { keycloakClient.createUserStorageProvider("test-realm", "kos-auth-storage", "http://custom-api:8080/api/users") } returns "provider-id"
        every { realmRepository.save(any()) } returns Mono.just(updatedRealm)
        coEvery { syncService.syncRealm("test-realm") } returns defaultSyncResult
        // Mock syncUsersFromKeycloak
        coEvery { keycloakClient.getUsers("test-realm", max = 10000) } returns emptyList()

        val result = service.enableSpi("test-realm", request)

        assertTrue(result.spiEnabled)
        coVerify { keycloakClient.createUserStorageProvider("test-realm", "kos-auth-storage", "http://custom-api:8080/api/users") }
        coVerify { syncService.syncRealm("test-realm") }
    }

    @Test
    fun `enableSpi throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val request = EnableSpiRequest(apiUrl = "http://api:8080")

        val ex = assertThrows<ResponseStatusException> {
            service.enableSpi("nonexistent", request)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("not found"))
    }

    @Test
    fun `enableSpi throws 409 when SPI already enabled`() = runTest {
        val spiRealm = realm.copy(spiEnabled = true, spiApiUrl = "http://api:8080/api/users")

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(spiRealm)

        val request = EnableSpiRequest(apiUrl = "http://api:8080")

        val ex = assertThrows<ResponseStatusException> {
            service.enableSpi("test-realm", request)
        }
        assertEquals(409, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("already enabled"))
    }

    // --- triggerSync ---

    @Test
    fun `triggerSync triggers sync and returns response`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        coEvery { syncService.syncRealm("test-realm") } returns defaultSyncResult
        // After sync, re-fetch realm and save with updated syncedAt
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { realmRepository.save(any()) } returns Mono.just(realm.copy(syncedAt = Instant.now()))

        val result = service.triggerSync("test-realm")

        assertEquals("test-realm", result.realmName)
        assertEquals(2, result.clientsProcessed)
        assertEquals(3, result.rolesProcessed)
        assertEquals(1, result.groupsProcessed)
        assertEquals(0, result.userStorageProvidersProcessed)
        assertTrue(result.success)
    }

    @Test
    fun `triggerSync throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.triggerSync("nonexistent")
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("not found"))
    }

    // --- syncAllRealms ---

    @Test
    fun `syncAllRealms imports new realm from Keycloak`() = runTest {
        val newKeycloakRealm = RealmRepresentation(
            id = "kc-new-id",
            realm = "new-kc-realm",
            displayName = "New KC Realm",
            enabled = true
        )

        val savedRealm = KcRealm(
            id = UUID.randomUUID(),
            accountId = null,
            realmName = "new-kc-realm",
            displayName = "New KC Realm",
            enabled = true,
            spiEnabled = false,
            spiApiUrl = null,
            attributes = Json.of("{}"),
            keycloakId = "kc-new-id",
            syncedAt = now,
            createdAt = now
        )

        coEvery { keycloakClient.getRealms() } returns listOf(newKeycloakRealm)
        every { realmRepository.findByRealmName("new-kc-realm") } returns Mono.empty()
        every { realmRepository.save(any()) } returns Mono.just(savedRealm)
        coEvery { syncService.syncRealm("new-kc-realm") } returns defaultSyncResult.copy(realmName = "new-kc-realm")

        val result = service.syncAllRealms()

        assertEquals(1, result.totalProcessed)
        assertEquals(1, result.imported)
        assertEquals(0, result.updated)
        assertEquals(0, result.failed)
        assertEquals(1, result.results.size)
        assertEquals("new-kc-realm", result.results[0].realmName)
        assertTrue(result.results[0].success)
    }

    @Test
    fun `syncAllRealms updates existing realm`() = runTest {
        val existingKeycloakRealm = RealmRepresentation(
            id = "kc-realm-id-123",
            realm = "test-realm",
            displayName = "Updated Test Realm",
            enabled = true
        )

        coEvery { keycloakClient.getRealms() } returns listOf(existingKeycloakRealm)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { realmRepository.save(any()) } returns Mono.just(realm.copy(displayName = "Updated Test Realm"))
        coEvery { syncService.syncRealm("test-realm") } returns defaultSyncResult

        val result = service.syncAllRealms()

        assertEquals(1, result.totalProcessed)
        assertEquals(0, result.imported)
        assertEquals(1, result.updated)
        assertEquals(0, result.failed)
        assertEquals(1, result.results.size)
    }

    @Test
    fun `syncAllRealms handles sync failure gracefully`() = runTest {
        val failingRealm = RealmRepresentation(
            id = "kc-fail-id",
            realm = "failing-realm",
            displayName = "Failing Realm",
            enabled = true
        )

        coEvery { keycloakClient.getRealms() } returns listOf(failingRealm)
        every { realmRepository.findByRealmName("failing-realm") } throws RuntimeException("DB connection failed")

        val result = service.syncAllRealms()

        assertEquals(1, result.totalProcessed)
        assertEquals(0, result.imported)
        assertEquals(0, result.updated)
        assertEquals(1, result.failed)
        assertTrue(result.results.isEmpty())
    }
}

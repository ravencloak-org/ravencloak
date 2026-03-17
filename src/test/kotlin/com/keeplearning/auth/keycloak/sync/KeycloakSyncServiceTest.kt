package com.keeplearning.auth.keycloak.sync

import tools.jackson.databind.ObjectMapper
import com.keeplearning.auth.domain.entity.*
import com.keeplearning.auth.domain.repository.*
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class KeycloakSyncServiceTest {

    @MockK
    lateinit var keycloakClient: KeycloakAdminClient

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
    lateinit var syncLogRepository: KcSyncLogRepository

    @MockK
    lateinit var objectMapper: ObjectMapper

    private lateinit var service: KeycloakSyncService

    private val realmId = UUID.randomUUID()
    private val syncLogId = UUID.randomUUID()

    private val realm = KcRealm(
        id = realmId,
        realmName = "test-realm",
        displayName = "Test Realm",
        enabled = true,
        keycloakId = "kc-realm-id-1",
        syncedAt = Instant.parse("2024-01-15T10:00:00Z")
    )

    private val syncLog = KcSyncLog(
        id = syncLogId,
        realmId = realmId,
        entityType = SyncEntityType.REALM.name,
        syncDirection = SyncDirection.FROM_KC.name,
        status = SyncStatus.STARTED.name,
        startedAt = Instant.now()
    )

    @BeforeEach
    fun setup() {
        service = KeycloakSyncService(
            keycloakClient,
            realmRepository,
            clientRepository,
            roleRepository,
            groupRepository,
            userStorageProviderRepository,
            syncLogRepository,
            objectMapper
        )
    }

    /**
     * Helper to mock the syncLogRepository.save() calls that logSync() makes for both
     * STARTED and COMPLETED/FAILED status saves.
     */
    private fun mockSyncLog(realmId: UUID? = this.realmId) {
        every { syncLogRepository.save(any()) } answers {
            val log = firstArg<KcSyncLog>()
            Mono.just(log.copy(id = syncLogId))
        }
    }

    // --- syncRealms ---

    @Test
    fun `syncRealms updates existing realm when keycloakId matches`() = runTest {
        val realmRep = RealmRepresentation(
            id = "kc-realm-id-1",
            realm = "test-realm",
            displayName = "Updated Realm",
            enabled = true
        )

        mockSyncLog(realmId = null)
        coEvery { keycloakClient.getRealms() } returns listOf(realmRep)
        every { realmRepository.findByKeycloakId("kc-realm-id-1") } returns Mono.just(realm)
        every { realmRepository.save(any()) } returns Mono.just(realm)

        val result = service.syncRealms()

        assertEquals(SyncEntityType.REALM, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        verify {
            realmRepository.save(match {
                it.id == realmId &&
                    it.realmName == "test-realm" &&
                    it.displayName == "Updated Realm" &&
                    it.keycloakId == "kc-realm-id-1"
            })
        }
    }

    @Test
    fun `syncRealms creates new realm when not found in DB`() = runTest {
        val realmRep = RealmRepresentation(
            id = "kc-new-realm-id",
            realm = "new-realm",
            displayName = "New Realm",
            enabled = true
        )

        val newRealm = KcRealm(
            id = UUID.randomUUID(),
            realmName = "new-realm",
            displayName = "New Realm",
            enabled = true,
            keycloakId = "kc-new-realm-id"
        )

        mockSyncLog(realmId = null)
        coEvery { keycloakClient.getRealms() } returns listOf(realmRep)
        every { realmRepository.findByKeycloakId("kc-new-realm-id") } returns Mono.empty()
        every { realmRepository.save(any()) } returns Mono.just(newRealm)

        val result = service.syncRealms()

        assertEquals(SyncEntityType.REALM, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        verify {
            realmRepository.save(match {
                it.id == null &&
                    it.realmName == "new-realm" &&
                    it.displayName == "New Realm" &&
                    it.keycloakId == "kc-new-realm-id"
            })
        }
    }

    @Test
    fun `syncRealms filters out master realm`() = runTest {
        val masterRealm = RealmRepresentation(
            id = "master-id",
            realm = "master",
            displayName = "Master",
            enabled = true
        )
        val appRealm = RealmRepresentation(
            id = "app-realm-id",
            realm = "app-realm",
            displayName = "App Realm",
            enabled = true
        )

        val savedRealm = KcRealm(
            id = UUID.randomUUID(),
            realmName = "app-realm",
            displayName = "App Realm",
            keycloakId = "app-realm-id"
        )

        mockSyncLog(realmId = null)
        coEvery { keycloakClient.getRealms() } returns listOf(masterRealm, appRealm)
        every { realmRepository.findByKeycloakId("app-realm-id") } returns Mono.empty()
        every { realmRepository.save(any()) } returns Mono.just(savedRealm)

        val result = service.syncRealms()

        assertEquals(1, result.count)
        assertTrue(result.success)

        // Should never look up master realm
        verify(exactly = 0) { realmRepository.findByKeycloakId("master-id") }
    }

    // --- syncClients ---

    @Test
    fun `syncClients updates existing client`() = runTest {
        val clientId = UUID.randomUUID()
        val existingClient = KcClient(
            id = clientId,
            realmId = realmId,
            clientId = "my-app",
            name = "Old Name",
            keycloakId = "kc-client-id-1"
        )

        val clientRep = ClientRepresentation(
            id = "kc-client-id-1",
            clientId = "my-app",
            name = "Updated App",
            description = "Updated description",
            enabled = true,
            publicClient = true,
            protocol = "openid-connect",
            rootUrl = "https://app.example.com",
            baseUrl = "/",
            redirectUris = listOf("https://app.example.com/*"),
            webOrigins = listOf("https://app.example.com")
        )

        mockSyncLog()
        coEvery { keycloakClient.getClients("test-realm") } returns listOf(clientRep)
        every { clientRepository.findByKeycloakId("kc-client-id-1") } returns Mono.just(existingClient)
        every { objectMapper.writeValueAsString(any<Any>()) } returns "[]"
        every { clientRepository.save(any()) } returns Mono.just(existingClient)

        val result = service.syncClients(realm)

        assertEquals(SyncEntityType.CLIENT, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        verify {
            clientRepository.save(match {
                it.id == clientId &&
                    it.clientId == "my-app" &&
                    it.name == "Updated App" &&
                    it.description == "Updated description" &&
                    it.publicClient == true &&
                    it.rootUrl == "https://app.example.com"
            })
        }
    }

    @Test
    fun `syncClients creates new client`() = runTest {
        val clientRep = ClientRepresentation(
            id = "kc-new-client-id",
            clientId = "new-app",
            name = "New Application",
            description = "A new application",
            enabled = true,
            publicClient = false,
            protocol = "openid-connect"
        )

        val savedClient = KcClient(
            id = UUID.randomUUID(),
            realmId = realmId,
            clientId = "new-app",
            name = "New Application",
            keycloakId = "kc-new-client-id"
        )

        mockSyncLog()
        coEvery { keycloakClient.getClients("test-realm") } returns listOf(clientRep)
        every { clientRepository.findByKeycloakId("kc-new-client-id") } returns Mono.empty()
        every { objectMapper.writeValueAsString(any<Any>()) } returns "[]"
        every { clientRepository.save(any()) } returns Mono.just(savedClient)

        val result = service.syncClients(realm)

        assertEquals(SyncEntityType.CLIENT, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        verify {
            clientRepository.save(match {
                it.id == null &&
                    it.realmId == realmId &&
                    it.clientId == "new-app" &&
                    it.name == "New Application" &&
                    it.keycloakId == "kc-new-client-id"
            })
        }
    }

    @Test
    fun `syncClients handles empty client list`() = runTest {
        mockSyncLog()
        coEvery { keycloakClient.getClients("test-realm") } returns emptyList()

        val result = service.syncClients(realm)

        assertEquals(SyncEntityType.CLIENT, result.entityType)
        assertEquals(0, result.count)
        assertTrue(result.success)

        verify(exactly = 0) { clientRepository.save(any()) }
    }

    // --- syncRealmRoles ---

    @Test
    fun `syncRealmRoles updates existing role`() = runTest {
        val roleId = UUID.randomUUID()
        val existingRole = KcRole(
            id = roleId,
            realmId = realmId,
            name = "admin",
            description = "Old description",
            keycloakId = "kc-role-id-1"
        )

        val roleRep = RoleRepresentation(
            id = "kc-role-id-1",
            name = "admin",
            description = "Updated admin role",
            composite = true
        )

        mockSyncLog()
        coEvery { keycloakClient.getRealmRoles("test-realm") } returns listOf(roleRep)
        every { roleRepository.findByKeycloakId("kc-role-id-1") } returns Mono.just(existingRole)
        every { roleRepository.save(any()) } returns Mono.just(existingRole)

        val result = service.syncRealmRoles(realm)

        assertEquals(SyncEntityType.ROLE, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        verify {
            roleRepository.save(match {
                it.id == roleId &&
                    it.name == "admin" &&
                    it.description == "Updated admin role" &&
                    it.composite == true
            })
        }
    }

    @Test
    fun `syncRealmRoles creates new role`() = runTest {
        val roleRep = RoleRepresentation(
            id = "kc-new-role-id",
            name = "editor",
            description = "Editor role",
            composite = false
        )

        val savedRole = KcRole(
            id = UUID.randomUUID(),
            realmId = realmId,
            name = "editor",
            keycloakId = "kc-new-role-id"
        )

        mockSyncLog()
        coEvery { keycloakClient.getRealmRoles("test-realm") } returns listOf(roleRep)
        every { roleRepository.findByKeycloakId("kc-new-role-id") } returns Mono.empty()
        every { roleRepository.save(any()) } returns Mono.just(savedRole)

        val result = service.syncRealmRoles(realm)

        assertEquals(SyncEntityType.ROLE, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        verify {
            roleRepository.save(match {
                it.id == null &&
                    it.realmId == realmId &&
                    it.clientId == null &&
                    it.name == "editor" &&
                    it.description == "Editor role" &&
                    it.keycloakId == "kc-new-role-id"
            })
        }
    }

    // --- syncGroups ---

    @Test
    fun `syncGroups syncs root-level groups`() = runTest {
        val groupRep = GroupRepresentation(
            id = "kc-group-id-1",
            name = "Engineering",
            path = "/Engineering",
            attributes = mapOf("dept" to listOf("eng")),
            subGroups = emptyList()
        )

        val savedGroup = KcGroup(
            id = UUID.randomUUID(),
            realmId = realmId,
            name = "Engineering",
            path = "/Engineering",
            keycloakId = "kc-group-id-1"
        )

        mockSyncLog()
        coEvery { keycloakClient.getGroups("test-realm") } returns listOf(groupRep)
        every { groupRepository.findByKeycloakId("kc-group-id-1") } returns Mono.empty()
        every { objectMapper.writeValueAsString(any<Any>()) } returns "{\"dept\":[\"eng\"]}"
        every { groupRepository.save(any()) } returns Mono.just(savedGroup)

        val result = service.syncGroups(realm)

        assertEquals(SyncEntityType.GROUP, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        verify {
            groupRepository.save(match {
                it.id == null &&
                    it.realmId == realmId &&
                    it.parentId == null &&
                    it.name == "Engineering" &&
                    it.path == "/Engineering" &&
                    it.keycloakId == "kc-group-id-1"
            })
        }
    }

    @Test
    fun `syncGroups syncs groups with nested subgroups recursively`() = runTest {
        val parentGroupId = UUID.randomUUID()
        val childGroupRep = GroupRepresentation(
            id = "kc-child-group-id",
            name = "Backend",
            path = "/Engineering/Backend",
            attributes = null,
            subGroups = emptyList()
        )

        val parentGroupRep = GroupRepresentation(
            id = "kc-parent-group-id",
            name = "Engineering",
            path = "/Engineering",
            attributes = null,
            subGroups = listOf(childGroupRep)
        )

        val savedParentGroup = KcGroup(
            id = parentGroupId,
            realmId = realmId,
            name = "Engineering",
            path = "/Engineering",
            keycloakId = "kc-parent-group-id"
        )

        val savedChildGroup = KcGroup(
            id = UUID.randomUUID(),
            realmId = realmId,
            parentId = parentGroupId,
            name = "Backend",
            path = "/Engineering/Backend",
            keycloakId = "kc-child-group-id"
        )

        mockSyncLog()
        coEvery { keycloakClient.getGroups("test-realm") } returns listOf(parentGroupRep)
        every { objectMapper.writeValueAsString(any<Any>()) } returns "{}"
        every { groupRepository.findByKeycloakId("kc-parent-group-id") } returns Mono.empty()
        every { groupRepository.findByKeycloakId("kc-child-group-id") } returns Mono.empty()
        every { groupRepository.save(match { it.name == "Engineering" }) } returns Mono.just(savedParentGroup)
        every { groupRepository.save(match { it.name == "Backend" }) } returns Mono.just(savedChildGroup)

        val result = service.syncGroups(realm)

        assertEquals(SyncEntityType.GROUP, result.entityType)
        assertEquals(2, result.count)
        assertTrue(result.success)

        // Verify parent group saved with no parentId
        verify {
            groupRepository.save(match {
                it.name == "Engineering" && it.parentId == null
            })
        }

        // Verify child group saved with correct parentId
        verify {
            groupRepository.save(match {
                it.name == "Backend" && it.parentId == parentGroupId
            })
        }
    }

    // --- syncUserStorageProviders ---

    @Test
    fun `syncUserStorageProviders updates existing provider`() = runTest {
        val providerId = UUID.randomUUID()
        val existingProvider = KcUserStorageProvider(
            id = providerId,
            realmId = realmId,
            name = "my-spi",
            providerId = "custom-user-provider",
            priority = 0,
            keycloakId = "kc-provider-id-1"
        )

        val componentRep = ComponentRepresentation(
            id = "kc-provider-id-1",
            name = "my-spi-updated",
            providerId = "custom-user-provider",
            config = mapOf("priority" to listOf("10"), "apiUrl" to listOf("https://api.example.com"))
        )

        mockSyncLog()
        coEvery { keycloakClient.getUserStorageProviders("test-realm") } returns listOf(componentRep)
        every { userStorageProviderRepository.findByRealmId(realmId) } returns Flux.just(existingProvider)
        every { userStorageProviderRepository.findByKeycloakId("kc-provider-id-1") } returns Mono.just(existingProvider)
        every { objectMapper.writeValueAsString(any<Any>()) } returns "{\"priority\":[\"10\"],\"apiUrl\":[\"https://api.example.com\"]}"
        every { userStorageProviderRepository.save(any()) } returns Mono.just(existingProvider)
        // spiEnabled update — realm already has spiEnabled=false by default, providers exist → set to true
        every { realmRepository.save(any()) } returns Mono.just(realm.copy(spiEnabled = true))

        val result = service.syncUserStorageProviders(realm)

        assertEquals(SyncEntityType.USER, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        verify {
            userStorageProviderRepository.save(match {
                it.id == providerId &&
                    it.name == "my-spi-updated" &&
                    it.priority == 10
            })
        }
    }

    @Test
    fun `syncUserStorageProviders creates new provider`() = runTest {
        val componentRep = ComponentRepresentation(
            id = "kc-new-provider-id",
            name = "new-spi",
            providerId = "custom-user-provider",
            config = mapOf("priority" to listOf("5"))
        )

        val savedProvider = KcUserStorageProvider(
            id = UUID.randomUUID(),
            realmId = realmId,
            name = "new-spi",
            providerId = "custom-user-provider",
            priority = 5,
            keycloakId = "kc-new-provider-id"
        )

        mockSyncLog()
        coEvery { keycloakClient.getUserStorageProviders("test-realm") } returns listOf(componentRep)
        every { userStorageProviderRepository.findByRealmId(realmId) } returns Flux.empty()
        every { userStorageProviderRepository.findByKeycloakId("kc-new-provider-id") } returns Mono.empty()
        every { objectMapper.writeValueAsString(any<Any>()) } returns "{\"priority\":[\"5\"]}"
        every { userStorageProviderRepository.save(any()) } returns Mono.just(savedProvider)
        every { realmRepository.save(any()) } returns Mono.just(realm.copy(spiEnabled = true))

        val result = service.syncUserStorageProviders(realm)

        assertEquals(SyncEntityType.USER, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        verify {
            userStorageProviderRepository.save(match {
                it.id == null &&
                    it.realmId == realmId &&
                    it.name == "new-spi" &&
                    it.providerId == "custom-user-provider" &&
                    it.priority == 5 &&
                    it.keycloakId == "kc-new-provider-id"
            })
        }
    }

    @Test
    fun `syncUserStorageProviders deletes stale providers not in Keycloak`() = runTest {
        val staleProviderId = UUID.randomUUID()
        val staleProvider = KcUserStorageProvider(
            id = staleProviderId,
            realmId = realmId,
            name = "stale-spi",
            providerId = "old-provider",
            keycloakId = "kc-stale-provider-id"
        )

        val activeComponentRep = ComponentRepresentation(
            id = "kc-active-provider-id",
            name = "active-spi",
            providerId = "custom-user-provider",
            config = mapOf("priority" to listOf("0"))
        )

        val savedProvider = KcUserStorageProvider(
            id = UUID.randomUUID(),
            realmId = realmId,
            name = "active-spi",
            providerId = "custom-user-provider",
            keycloakId = "kc-active-provider-id"
        )

        mockSyncLog()
        coEvery { keycloakClient.getUserStorageProviders("test-realm") } returns listOf(activeComponentRep)
        // DB has the stale provider
        every { userStorageProviderRepository.findByRealmId(realmId) } returns Flux.just(staleProvider)
        every { userStorageProviderRepository.delete(staleProvider) } returns Mono.empty()
        every { userStorageProviderRepository.findByKeycloakId("kc-active-provider-id") } returns Mono.empty()
        every { objectMapper.writeValueAsString(any<Any>()) } returns "{\"priority\":[\"0\"]}"
        every { userStorageProviderRepository.save(any()) } returns Mono.just(savedProvider)
        every { realmRepository.save(any()) } returns Mono.just(realm.copy(spiEnabled = true))

        val result = service.syncUserStorageProviders(realm)

        assertEquals(SyncEntityType.USER, result.entityType)
        assertEquals(1, result.count)
        assertTrue(result.success)

        // Verify the stale provider was deleted
        verify { userStorageProviderRepository.delete(staleProvider) }

        // Verify the active provider was created
        verify {
            userStorageProviderRepository.save(match {
                it.name == "active-spi" && it.keycloakId == "kc-active-provider-id"
            })
        }
    }

    // --- syncRealm ---

    @Test
    fun `syncRealm syncs existing realm entities`() = runTest {
        // syncRealm finds existing realm, then delegates to syncRealmEntities
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)

        // Mock all four entity syncs invoked by syncRealmEntities
        mockSyncLog()

        // syncClients
        coEvery { keycloakClient.getClients("test-realm") } returns emptyList()

        // syncRealmRoles
        coEvery { keycloakClient.getRealmRoles("test-realm") } returns emptyList()

        // syncGroups
        coEvery { keycloakClient.getGroups("test-realm") } returns emptyList()

        // syncUserStorageProviders
        coEvery { keycloakClient.getUserStorageProviders("test-realm") } returns emptyList()
        every { userStorageProviderRepository.findByRealmId(realmId) } returns Flux.empty()
        // spiEnabled is already false and providers list is empty → no realm save needed

        val result = service.syncRealm("test-realm")

        assertEquals("test-realm", result.realmName)
        assertTrue(result.success)
        assertTrue(result.clients.success)
        assertTrue(result.roles.success)
        assertTrue(result.groups.success)
        assertTrue(result.userStorageProviders.success)
    }

    @Test
    fun `syncRealm creates realm from Keycloak when not in DB`() = runTest {
        val realmRep = RealmRepresentation(
            id = "kc-new-realm-id",
            realm = "new-realm",
            displayName = "New Realm",
            enabled = true
        )

        val newRealm = KcRealm(
            id = UUID.randomUUID(),
            realmName = "new-realm",
            displayName = "New Realm",
            enabled = true,
            keycloakId = "kc-new-realm-id"
        )
        val newRealmId = newRealm.id!!

        every { realmRepository.findByRealmName("new-realm") } returns Mono.empty()
        coEvery { keycloakClient.getRealm("new-realm") } returns realmRep
        every { realmRepository.save(any()) } returns Mono.just(newRealm)

        // Mock all four entity syncs invoked by syncRealmEntities
        mockSyncLog()

        // syncClients
        coEvery { keycloakClient.getClients("new-realm") } returns emptyList()

        // syncRealmRoles
        coEvery { keycloakClient.getRealmRoles("new-realm") } returns emptyList()

        // syncGroups
        coEvery { keycloakClient.getGroups("new-realm") } returns emptyList()

        // syncUserStorageProviders
        coEvery { keycloakClient.getUserStorageProviders("new-realm") } returns emptyList()
        every { userStorageProviderRepository.findByRealmId(newRealmId) } returns Flux.empty()

        val result = service.syncRealm("new-realm")

        assertEquals("new-realm", result.realmName)
        assertTrue(result.success)

        // Verify realm was created from Keycloak data
        verify {
            realmRepository.save(match {
                it.realmName == "new-realm" &&
                    it.displayName == "New Realm" &&
                    it.keycloakId == "kc-new-realm-id"
            })
        }
    }
}

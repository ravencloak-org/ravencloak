package com.keeplearning.auth.realm.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityActionLog
import com.keeplearning.auth.audit.domain.EntityType
import com.keeplearning.auth.audit.service.AuditService
import com.keeplearning.auth.domain.entity.KcClient
import com.keeplearning.auth.domain.entity.KcRealm
import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.ClientRepresentation
import com.keeplearning.auth.realm.dto.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ClientServiceTest {

    @MockK
    lateinit var keycloakClient: KeycloakAdminClient

    @MockK
    lateinit var clientRepository: KcClientRepository

    @MockK
    lateinit var realmRepository: KcRealmRepository

    @MockK
    lateinit var auditService: AuditService

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private lateinit var service: ClientService

    private val keycloakIssuerPrefix = "http://localhost:8080/realms/"

    private val realmId = UUID.randomUUID()
    private val clientUUID = UUID.randomUUID()
    private val keycloakClientId = "kc-client-uuid-123"
    private val realmName = "test-realm"

    private val realm = KcRealm(
        id = realmId,
        realmName = realmName,
        keycloakId = "kc-realm-id"
    )

    private val kcClient = KcClient(
        id = clientUUID,
        realmId = realmId,
        clientId = "my-app",
        name = "My Application",
        description = "A test application",
        enabled = true,
        publicClient = false,
        rootUrl = "http://localhost:3000",
        baseUrl = "/",
        redirectUris = Json.of("""["http://localhost:3000/*"]"""),
        webOrigins = Json.of("""["http://localhost:3000"]"""),
        standardFlowEnabled = true,
        directAccessGrantsEnabled = false,
        serviceAccountsEnabled = true,
        keycloakId = keycloakClientId,
        createdAt = Instant.parse("2024-01-15T10:30:00Z")
    )

    private val publicKcClient = KcClient(
        id = clientUUID,
        realmId = realmId,
        clientId = "my-public-app",
        name = "My Public App",
        description = "A public test application",
        enabled = true,
        publicClient = true,
        rootUrl = "http://localhost:3000",
        baseUrl = "/",
        redirectUris = Json.of("""["http://localhost:3000/*"]"""),
        webOrigins = Json.of("""["http://localhost:3000"]"""),
        standardFlowEnabled = true,
        directAccessGrantsEnabled = false,
        serviceAccountsEnabled = false,
        keycloakId = keycloakClientId,
        createdAt = Instant.parse("2024-01-15T10:30:00Z")
    )

    private val mockActor: JwtAuthenticationToken = mockk(relaxed = true)

    private val dummyAuditLog = EntityActionLog(
        id = UUID.randomUUID(),
        actorKeycloakId = "actor-kc-id",
        actionType = ActionType.CREATE,
        entityType = EntityType.CLIENT,
        entityId = clientUUID,
        entityName = "my-app",
        realmName = realmName
    )

    @BeforeEach
    fun setup() {
        service = ClientService(
            keycloakClient,
            clientRepository,
            realmRepository,
            auditService,
            objectMapper,
            keycloakIssuerPrefix
        )
    }

    // --- createClient ---

    @Test
    fun `createClient creates client successfully with audit logging`() = runTest {
        val request = CreateClientRequest(
            clientId = "new-app",
            name = "New App",
            description = "A new application",
            publicClient = false,
            standardFlowEnabled = true,
            directAccessGrantsEnabled = false,
            serviceAccountsEnabled = true,
            rootUrl = "http://localhost:3000",
            baseUrl = "/",
            redirectUris = listOf("http://localhost:3000/*"),
            webOrigins = listOf("http://localhost:3000")
        )

        val savedClient = KcClient(
            id = clientUUID,
            realmId = realmId,
            clientId = "new-app",
            name = "New App",
            description = "A new application",
            enabled = true,
            publicClient = false,
            rootUrl = "http://localhost:3000",
            baseUrl = "/",
            redirectUris = Json.of("""["http://localhost:3000/*"]"""),
            webOrigins = Json.of("""["http://localhost:3000"]"""),
            standardFlowEnabled = true,
            directAccessGrantsEnabled = false,
            serviceAccountsEnabled = true,
            keycloakId = "new-kc-id"
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "new-app") } returns Mono.empty()
        coEvery { keycloakClient.createClient(realmName, any()) } returns "new-kc-id"
        every { clientRepository.save(any()) } returns Mono.just(savedClient)
        // For toDetailResponse — no paired client
        every { clientRepository.findById(any<UUID>()) } returns Mono.empty()
        coEvery { auditService.logAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns dummyAuditLog

        val result = service.createClient(realmName, request, mockActor)

        assertEquals(clientUUID, result.id)
        assertEquals("new-app", result.clientId)
        assertEquals("New App", result.name)
        assertEquals("A new application", result.description)
        assertTrue(result.standardFlowEnabled)
        assertTrue(result.serviceAccountsEnabled)

        coVerify { auditService.logAction(
            actor = mockActor,
            actionType = ActionType.CREATE,
            entityType = EntityType.CLIENT,
            entityId = clientUUID,
            entityName = "new-app",
            realmName = realmName,
            realmId = realmId,
            entityKeycloakId = "new-kc-id",
            afterState = any()
        ) }
    }

    @Test
    fun `createClient throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val request = CreateClientRequest(clientId = "new-app")

        val ex = assertThrows<ResponseStatusException> {
            service.createClient("nonexistent", request)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm"))
    }

    @Test
    fun `createClient throws 409 for duplicate clientId`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-app") } returns Mono.just(kcClient)

        val request = CreateClientRequest(clientId = "my-app")

        val ex = assertThrows<ResponseStatusException> {
            service.createClient(realmName, request)
        }
        assertEquals(409, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("already exists"))
    }

    // --- getClient ---

    @Test
    fun `getClient returns client detail response`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-app") } returns Mono.just(kcClient)

        val result = service.getClient(realmName, "my-app")

        assertEquals(clientUUID, result.id)
        assertEquals("my-app", result.clientId)
        assertEquals("My Application", result.name)
        assertEquals("A test application", result.description)
        assertTrue(result.enabled)
        assertEquals(false, result.publicClient)
        assertTrue(result.standardFlowEnabled)
        assertTrue(result.serviceAccountsEnabled)
        assertEquals("http://localhost:3000", result.rootUrl)
        assertEquals("/", result.baseUrl)
        assertEquals(listOf("http://localhost:3000/*"), result.redirectUris)
        assertEquals(listOf("http://localhost:3000"), result.webOrigins)
        assertNull(result.pairedClientId)
        assertNull(result.pairedClientClientId)
    }

    @Test
    fun `getClient throws 404 for missing realm or client`() = runTest {
        // Missing realm
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex1 = assertThrows<ResponseStatusException> {
            service.getClient("nonexistent", "my-app")
        }
        assertEquals(404, ex1.statusCode.value())
        assertTrue(ex1.reason!!.contains("Realm"))

        // Missing client
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "missing-app") } returns Mono.empty()

        val ex2 = assertThrows<ResponseStatusException> {
            service.getClient(realmName, "missing-app")
        }
        assertEquals(404, ex2.statusCode.value())
        assertTrue(ex2.reason!!.contains("Client"))
    }

    // --- listClients ---

    @Test
    fun `listClients returns clients filtered excluding default KC clients`() = runTest {
        val defaultClient = KcClient(
            id = UUID.randomUUID(),
            realmId = realmId,
            clientId = "account",
            name = "Account",
            enabled = true,
            keycloakId = "kc-account"
        )
        val adminCliClient = KcClient(
            id = UUID.randomUUID(),
            realmId = realmId,
            clientId = "admin-cli",
            name = "Admin CLI",
            enabled = true,
            keycloakId = "kc-admin-cli"
        )
        val customClient = kcClient

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmId(realmId) } returns Flux.just(defaultClient, adminCliClient, customClient)

        val result = service.listClients(realmName)

        assertEquals(1, result.size)
        assertEquals("my-app", result[0].clientId)
        assertEquals("My Application", result[0].name)
        assertEquals(false, result[0].publicClient)
    }

    @Test
    fun `listClients returns empty list when no custom clients`() = runTest {
        val defaultClient = KcClient(
            id = UUID.randomUUID(),
            realmId = realmId,
            clientId = "account",
            name = "Account",
            enabled = true,
            keycloakId = "kc-account"
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmId(realmId) } returns Flux.just(defaultClient)

        val result = service.listClients(realmName)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listClients throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.listClients("nonexistent")
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm"))
    }

    // --- updateClient ---

    @Test
    fun `updateClient updates client fields in Keycloak and DB`() = runTest {
        val request = UpdateClientRequest(
            name = "Updated App",
            description = "Updated description",
            enabled = false,
            rootUrl = "http://localhost:4000",
            redirectUris = listOf("http://localhost:4000/*"),
            webOrigins = listOf("http://localhost:4000")
        )

        val currentKcClient = ClientRepresentation(
            id = keycloakClientId,
            clientId = "my-app",
            name = "My Application",
            description = "A test application",
            enabled = true,
            publicClient = false,
            standardFlowEnabled = true,
            directAccessGrantsEnabled = false,
            serviceAccountsEnabled = true,
            rootUrl = "http://localhost:3000",
            baseUrl = "/",
            redirectUris = listOf("http://localhost:3000/*"),
            webOrigins = listOf("http://localhost:3000")
        )

        val updatedClient = kcClient.copy(
            name = "Updated App",
            description = "Updated description",
            enabled = false,
            rootUrl = "http://localhost:4000",
            redirectUris = Json.of("""["http://localhost:4000/*"]"""),
            webOrigins = Json.of("""["http://localhost:4000"]"""),
            updatedAt = Instant.now()
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-app") } returns Mono.just(kcClient)
        coEvery { keycloakClient.getClient(realmName, keycloakClientId) } returns currentKcClient
        coEvery { keycloakClient.updateClient(realmName, keycloakClientId, any()) } returns Unit
        every { clientRepository.save(any()) } returns Mono.just(updatedClient)
        coEvery { auditService.logAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns dummyAuditLog

        val result = service.updateClient(realmName, "my-app", request, mockActor)

        assertEquals("Updated App", result.name)
        assertEquals("Updated description", result.description)
        assertEquals(false, result.enabled)
        assertEquals("http://localhost:4000", result.rootUrl)
        assertEquals(listOf("http://localhost:4000/*"), result.redirectUris)
        assertEquals(listOf("http://localhost:4000"), result.webOrigins)

        coVerify { keycloakClient.updateClient(realmName, keycloakClientId, any()) }
        coVerify { auditService.logAction(
            actor = mockActor,
            actionType = ActionType.UPDATE,
            entityType = EntityType.CLIENT,
            entityId = clientUUID,
            entityName = "my-app",
            realmName = realmName,
            realmId = realmId,
            entityKeycloakId = keycloakClientId,
            beforeState = any(),
            afterState = any()
        ) }
    }

    @Test
    fun `updateClient preserves fields when request fields are null`() = runTest {
        val request = UpdateClientRequest(
            name = "Only Name Updated"
            // all other fields null — should keep originals
        )

        val currentKcClient = ClientRepresentation(
            id = keycloakClientId,
            clientId = "my-app",
            name = "My Application",
            description = "A test application",
            enabled = true,
            publicClient = false,
            standardFlowEnabled = true,
            directAccessGrantsEnabled = false,
            serviceAccountsEnabled = true,
            rootUrl = "http://localhost:3000",
            baseUrl = "/",
            redirectUris = listOf("http://localhost:3000/*"),
            webOrigins = listOf("http://localhost:3000")
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-app") } returns Mono.just(kcClient)
        coEvery { keycloakClient.getClient(realmName, keycloakClientId) } returns currentKcClient
        coEvery { keycloakClient.updateClient(realmName, keycloakClientId, any()) } returns Unit
        every { clientRepository.save(any()) } answers {
            Mono.just(firstArg<KcClient>())
        }

        val result = service.updateClient(realmName, "my-app", request)

        assertEquals("Only Name Updated", result.name)
        assertEquals("A test application", result.description) // preserved
        assertTrue(result.enabled) // preserved
        assertEquals(false, result.publicClient) // preserved
        assertTrue(result.standardFlowEnabled) // preserved
        assertTrue(result.serviceAccountsEnabled) // preserved
        assertEquals("http://localhost:3000", result.rootUrl) // preserved
        assertEquals("/", result.baseUrl) // preserved
        assertEquals(listOf("http://localhost:3000/*"), result.redirectUris) // preserved
        assertEquals(listOf("http://localhost:3000"), result.webOrigins) // preserved
    }

    @Test
    fun `updateClient throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val request = UpdateClientRequest(name = "test")

        val ex = assertThrows<ResponseStatusException> {
            service.updateClient("nonexistent", "my-app", request)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm"))
    }

    @Test
    fun `updateClient throws 404 for missing client`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "missing-app") } returns Mono.empty()

        val request = UpdateClientRequest(name = "test")

        val ex = assertThrows<ResponseStatusException> {
            service.updateClient(realmName, "missing-app", request)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Client"))
    }

    // --- deleteClient ---

    @Test
    fun `deleteClient deletes client with audit logging`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-app") } returns Mono.just(kcClient)
        coEvery { keycloakClient.deleteClient(realmName, keycloakClientId) } returns Unit
        every { clientRepository.delete(kcClient) } returns Mono.empty()
        coEvery { auditService.logAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns dummyAuditLog

        service.deleteClient(realmName, "my-app", mockActor)

        coVerify { keycloakClient.deleteClient(realmName, keycloakClientId) }
        coVerify { auditService.logAction(
            actor = mockActor,
            actionType = ActionType.DELETE,
            entityType = EntityType.CLIENT,
            entityId = clientUUID,
            entityName = "my-app",
            realmName = realmName,
            realmId = realmId,
            entityKeycloakId = keycloakClientId,
            beforeState = any()
        ) }
    }

    @Test
    fun `deleteClient throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.deleteClient("nonexistent", "my-app")
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm"))
    }

    @Test
    fun `deleteClient throws 404 for missing client`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "missing-app") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.deleteClient(realmName, "missing-app")
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Client"))
    }

    // --- getClientSecret ---

    @Test
    fun `getClientSecret returns client secret`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-app") } returns Mono.just(kcClient)
        coEvery { keycloakClient.getClientSecret(realmName, keycloakClientId) } returns "super-secret-123"

        val result = service.getClientSecret(realmName, "my-app")

        assertEquals("super-secret-123", result.secret)
    }

    @Test
    fun `getClientSecret throws 400 for public client`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-public-app") } returns Mono.just(publicKcClient)

        val ex = assertThrows<ResponseStatusException> {
            service.getClientSecret(realmName, "my-public-app")
        }
        assertEquals(400, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Public clients"))
    }

    @Test
    fun `getClientSecret throws 404 for missing client`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "missing-app") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.getClientSecret(realmName, "missing-app")
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Client"))
    }

    // --- regenerateClientSecret ---

    @Test
    fun `regenerateClientSecret regenerates client secret`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-app") } returns Mono.just(kcClient)
        coEvery { keycloakClient.regenerateClientSecret(realmName, keycloakClientId) } returns "new-secret-456"

        val result = service.regenerateClientSecret(realmName, "my-app")

        assertEquals("new-secret-456", result.secret)
        coVerify { keycloakClient.regenerateClientSecret(realmName, keycloakClientId) }
    }

    @Test
    fun `regenerateClientSecret throws 400 for public client`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-public-app") } returns Mono.just(publicKcClient)

        val ex = assertThrows<ResponseStatusException> {
            service.regenerateClientSecret(realmName, "my-public-app")
        }
        assertEquals(400, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Public clients"))
    }

    // --- createApplication ---

    @Test
    fun `createApplication creates full-stack app with frontend and backend paired`() = runTest {
        val request = CreateApplicationRequest(
            applicationName = "myapp",
            displayName = "My App",
            description = "Full stack app",
            applicationType = ApplicationType.FULL_STACK,
            rootUrl = "http://localhost:3000",
            baseUrl = "/",
            redirectUris = listOf("http://localhost:3000/*"),
            webOrigins = listOf("http://localhost:3000")
        )

        val frontendId = UUID.randomUUID()
        val backendId = UUID.randomUUID()

        val savedFrontend = KcClient(
            id = frontendId,
            realmId = realmId,
            clientId = "myapp-web",
            name = "My App (Web)",
            description = "Full stack app",
            enabled = true,
            publicClient = true,
            rootUrl = "http://localhost:3000",
            baseUrl = "/",
            redirectUris = Json.of("""["http://localhost:3000/*"]"""),
            webOrigins = Json.of("""["http://localhost:3000"]"""),
            standardFlowEnabled = true,
            directAccessGrantsEnabled = false,
            serviceAccountsEnabled = false,
            keycloakId = "kc-frontend-id",
            createdAt = Instant.parse("2024-01-15T10:30:00Z")
        )

        val savedBackend = KcClient(
            id = backendId,
            realmId = realmId,
            clientId = "myapp-backend",
            name = "My App (Backend)",
            description = "Full stack app",
            enabled = true,
            publicClient = false,
            rootUrl = null,
            baseUrl = null,
            redirectUris = Json.of("[]"),
            webOrigins = Json.of("[]"),
            standardFlowEnabled = false,
            directAccessGrantsEnabled = false,
            serviceAccountsEnabled = true,
            keycloakId = "kc-backend-id",
            pairedClientId = frontendId,
            createdAt = Instant.parse("2024-01-15T10:30:00Z")
        )

        val linkedFrontend = savedFrontend.copy(pairedClientId = backendId)

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "myapp-web") } returns Mono.empty()
        every { clientRepository.findByRealmIdAndClientId(realmId, "myapp-backend") } returns Mono.empty()
        coEvery { keycloakClient.createClient(realmName, match { it.clientId == "myapp-web" }) } returns "kc-frontend-id"
        coEvery { keycloakClient.createClient(realmName, match { it.clientId == "myapp-backend" }) } returns "kc-backend-id"

        // First save: frontend client
        // Second save: backend client
        // Third save: frontend client with pairedClientId set
        every { clientRepository.save(match<KcClient> { it.clientId == "myapp-web" && it.pairedClientId == null }) } returns Mono.just(savedFrontend)
        every { clientRepository.save(match<KcClient> { it.clientId == "myapp-backend" }) } returns Mono.just(savedBackend)
        every { clientRepository.save(match<KcClient> { it.clientId == "myapp-web" && it.pairedClientId == backendId }) } returns Mono.just(linkedFrontend)

        // For toDetailResponse — paired client lookups
        every { clientRepository.findById(backendId) } returns Mono.just(savedBackend)
        every { clientRepository.findById(frontendId) } returns Mono.just(savedFrontend)

        coEvery { auditService.logAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns dummyAuditLog

        val result = service.createApplication(realmName, request, mockActor)

        assertNotNull(result.frontendClient)
        assertNotNull(result.backendClient)
        assertEquals("myapp-web", result.frontendClient!!.clientId)
        assertEquals("myapp-backend", result.backendClient!!.clientId)
        assertTrue(result.frontendClient!!.publicClient)
        assertEquals(false, result.backendClient!!.publicClient)
        assertTrue(result.backendClient!!.serviceAccountsEnabled)
        assertEquals(backendId, result.frontendClient!!.pairedClientId)
        assertEquals(frontendId, result.backendClient!!.pairedClientId)

        coVerify(exactly = 2) { auditService.logAction(any(), ActionType.CREATE, EntityType.CLIENT, any(), any(), realmName, realmId, any(), any(), any()) }
    }

    @Test
    fun `createApplication creates frontend only`() = runTest {
        val request = CreateApplicationRequest(
            applicationName = "myapp",
            displayName = "My App",
            description = "Frontend only app",
            applicationType = ApplicationType.FRONTEND_ONLY,
            rootUrl = "http://localhost:3000",
            redirectUris = listOf("http://localhost:3000/*"),
            webOrigins = listOf("http://localhost:3000")
        )

        val frontendId = UUID.randomUUID()

        val savedFrontend = KcClient(
            id = frontendId,
            realmId = realmId,
            clientId = "myapp-web",
            name = "My App (Web)",
            description = "Frontend only app",
            enabled = true,
            publicClient = true,
            rootUrl = "http://localhost:3000",
            baseUrl = null,
            redirectUris = Json.of("""["http://localhost:3000/*"]"""),
            webOrigins = Json.of("""["http://localhost:3000"]"""),
            standardFlowEnabled = true,
            directAccessGrantsEnabled = false,
            serviceAccountsEnabled = false,
            keycloakId = "kc-frontend-id",
            createdAt = Instant.parse("2024-01-15T10:30:00Z")
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "myapp-web") } returns Mono.empty()
        coEvery { keycloakClient.createClient(realmName, match { it.clientId == "myapp-web" }) } returns "kc-frontend-id"
        every { clientRepository.save(any()) } returns Mono.just(savedFrontend)

        val result = service.createApplication(realmName, request)

        assertNotNull(result.frontendClient)
        assertNull(result.backendClient)
        assertEquals("myapp-web", result.frontendClient!!.clientId)
        assertTrue(result.frontendClient!!.publicClient)
        assertNull(result.frontendClient!!.pairedClientId)
    }

    @Test
    fun `createApplication creates backend only`() = runTest {
        val request = CreateApplicationRequest(
            applicationName = "myapp",
            displayName = "My App",
            description = "Backend only app",
            applicationType = ApplicationType.BACKEND_ONLY
        )

        val backendId = UUID.randomUUID()

        val savedBackend = KcClient(
            id = backendId,
            realmId = realmId,
            clientId = "myapp-backend",
            name = "My App (Backend)",
            description = "Backend only app",
            enabled = true,
            publicClient = false,
            rootUrl = null,
            baseUrl = null,
            redirectUris = Json.of("[]"),
            webOrigins = Json.of("[]"),
            standardFlowEnabled = false,
            directAccessGrantsEnabled = false,
            serviceAccountsEnabled = true,
            keycloakId = "kc-backend-id",
            pairedClientId = null,
            createdAt = Instant.parse("2024-01-15T10:30:00Z")
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "myapp-backend") } returns Mono.empty()
        coEvery { keycloakClient.createClient(realmName, match { it.clientId == "myapp-backend" }) } returns "kc-backend-id"
        every { clientRepository.save(any()) } returns Mono.just(savedBackend)

        val result = service.createApplication(realmName, request)

        assertNull(result.frontendClient)
        assertNotNull(result.backendClient)
        assertEquals("myapp-backend", result.backendClient!!.clientId)
        assertEquals(false, result.backendClient!!.publicClient)
        assertTrue(result.backendClient!!.serviceAccountsEnabled)
        assertNull(result.backendClient!!.pairedClientId)
    }

    @Test
    fun `createApplication throws 409 when client already exists`() = runTest {
        val request = CreateApplicationRequest(
            applicationName = "myapp",
            applicationType = ApplicationType.FULL_STACK
        )

        val existingClient = KcClient(
            id = UUID.randomUUID(),
            realmId = realmId,
            clientId = "myapp-web",
            keycloakId = "existing-kc-id"
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "myapp-web") } returns Mono.just(existingClient)

        val ex = assertThrows<ResponseStatusException> {
            service.createApplication(realmName, request)
        }
        assertEquals(409, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("myapp-web"))
        assertTrue(ex.reason!!.contains("already exists"))
    }

    // --- getIntegrationSnippets ---

    @Test
    fun `getIntegrationSnippets returns frontend snippets for public client`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-public-app") } returns Mono.just(publicKcClient)

        val result = service.getIntegrationSnippets(realmName, "my-public-app")

        assertEquals("http://localhost:8080", result.keycloakUrl)
        assertEquals(realmName, result.realmName)
        assertEquals("my-public-app", result.clientId)
        assertTrue(result.isPublicClient)
        assertNotNull(result.snippets)
        assertNull(result.backendSnippets)
        assertTrue(result.snippets!!.vanillaJs.contains("my-public-app"))
        assertTrue(result.snippets!!.react.contains("my-public-app"))
        assertTrue(result.snippets!!.vue.contains("my-public-app"))
    }

    @Test
    fun `getIntegrationSnippets returns backend snippets for confidential client`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "my-app") } returns Mono.just(kcClient)

        val result = service.getIntegrationSnippets(realmName, "my-app")

        assertEquals("http://localhost:8080", result.keycloakUrl)
        assertEquals(realmName, result.realmName)
        assertEquals("my-app", result.clientId)
        assertEquals(false, result.isPublicClient)
        assertNull(result.snippets)
        assertNotNull(result.backendSnippets)
        assertTrue(result.backendSnippets!!.applicationYml.contains("my-app"))
        assertTrue(result.backendSnippets!!.securityConfig.contains("SecurityConfig"))
        assertTrue(result.backendSnippets!!.buildGradle.contains("spring-boot"))
    }
}

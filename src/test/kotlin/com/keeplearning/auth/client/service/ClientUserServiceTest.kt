package com.keeplearning.auth.client.service

import com.keeplearning.auth.client.dto.*
import com.keeplearning.auth.domain.entity.KcClient
import com.keeplearning.auth.domain.entity.KcUserClient
import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.domain.repository.KcUserClientRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ClientUserServiceTest {

    @MockK
    lateinit var clientRepository: KcClientRepository

    @MockK
    lateinit var userClientRepository: KcUserClientRepository

    private lateinit var service: ClientUserService

    private val realmId = UUID.randomUUID()
    private val clientUuid = UUID.randomUUID()
    private val pairedClientUuid = UUID.randomUUID()
    private val actorId = "actor-kc-id-123"

    private val kcClient = KcClient(
        id = clientUuid,
        realmId = realmId,
        clientId = "my-app",
        name = "My Application",
        publicClient = true,
        keycloakId = "kc-client-id-123"
    )

    private val pairedClient = KcClient(
        id = pairedClientUuid,
        realmId = realmId,
        clientId = "my-app-backend",
        name = "My Application Backend",
        publicClient = false,
        keycloakId = "kc-paired-client-id",
        pairedClientId = clientUuid
    )

    private val userClient = KcUserClient(
        id = UUID.randomUUID(),
        realmId = realmId,
        userKeycloakId = "kc-user-123",
        userEmail = "john@example.com",
        clientId = clientUuid,
        assignedByKeycloakId = actorId,
        assignedAt = Instant.parse("2024-01-20T12:00:00Z")
    )

    @BeforeEach
    fun setup() {
        service = ClientUserService(clientRepository, userClientRepository)
    }

    // --- addUsers ---

    @Test
    fun `addUsers adds new users successfully`() = runTest {
        val request = AddUsersRequest(
            users = listOf(
                UserRef(email = "alice@example.com", keycloakId = "kc-alice"),
                UserRef(email = "bob@example.com")
            )
        )

        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        every { userClientRepository.existsByClientIdAndUserEmail(clientUuid, "alice@example.com") } returns Mono.just(false)
        every { userClientRepository.existsByClientIdAndUserEmail(clientUuid, "bob@example.com") } returns Mono.just(false)
        every { userClientRepository.save(match { it.userEmail == "alice@example.com" }) } returns Mono.just(
            userClient.copy(userEmail = "alice@example.com", userKeycloakId = "kc-alice")
        )
        every { userClientRepository.save(match { it.userEmail == "bob@example.com" }) } returns Mono.just(
            userClient.copy(userEmail = "bob@example.com", userKeycloakId = "")
        )

        val result = service.addUsers(clientUuid, request, actorId)

        assertEquals(listOf("alice@example.com", "bob@example.com"), result.added)
        assertTrue(result.alreadyExists.isEmpty())
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun `addUsers reports already existing users`() = runTest {
        val request = AddUsersRequest(
            users = listOf(
                UserRef(email = "existing@example.com")
            )
        )

        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        every { userClientRepository.existsByClientIdAndUserEmail(clientUuid, "existing@example.com") } returns Mono.just(true)

        val result = service.addUsers(clientUuid, request, actorId)

        assertTrue(result.added.isEmpty())
        assertEquals(listOf("existing@example.com"), result.alreadyExists)
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun `addUsers reports failed users on exception`() = runTest {
        val request = AddUsersRequest(
            users = listOf(
                UserRef(email = "fail@example.com")
            )
        )

        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        every { userClientRepository.existsByClientIdAndUserEmail(clientUuid, "fail@example.com") } returns Mono.just(false)
        every { userClientRepository.save(match { it.userEmail == "fail@example.com" }) } returns Mono.error(
            RuntimeException("DB write failed")
        )

        val result = service.addUsers(clientUuid, request, actorId)

        assertTrue(result.added.isEmpty())
        assertTrue(result.alreadyExists.isEmpty())
        assertEquals(listOf("fail@example.com"), result.failed)
    }

    @Test
    fun `addUsers throws 404 for missing client`() = runTest {
        val request = AddUsersRequest(users = listOf(UserRef(email = "alice@example.com")))
        val missingId = UUID.randomUUID()

        every { clientRepository.findById(missingId) } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.addUsers(missingId, request, actorId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Client not found"))
    }

    // --- removeUsers ---

    @Test
    fun `removeUsers removes existing users`() = runTest {
        val request = RemoveUsersRequest(emails = listOf("john@example.com"))

        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        every { userClientRepository.findByClientIdAndUserEmail(clientUuid, "john@example.com") } returns Mono.just(userClient)
        every { userClientRepository.delete(userClient) } returns Mono.empty()

        val result = service.removeUsers(clientUuid, request)

        assertEquals(listOf("john@example.com"), result.removed)
        assertTrue(result.notFound.isEmpty())
    }

    @Test
    fun `removeUsers reports not found users`() = runTest {
        val request = RemoveUsersRequest(emails = listOf("missing@example.com"))

        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        every { userClientRepository.findByClientIdAndUserEmail(clientUuid, "missing@example.com") } returns Mono.empty()

        val result = service.removeUsers(clientUuid, request)

        assertTrue(result.removed.isEmpty())
        assertEquals(listOf("missing@example.com"), result.notFound)
    }

    @Test
    fun `removeUsers throws 404 for missing client`() = runTest {
        val request = RemoveUsersRequest(emails = listOf("john@example.com"))
        val missingId = UUID.randomUUID()

        every { clientRepository.findById(missingId) } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.removeUsers(missingId, request)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Client not found"))
    }

    // --- listUsers ---

    @Test
    fun `listUsers returns all authorized users`() = runTest {
        val userClient2 = KcUserClient(
            id = UUID.randomUUID(),
            realmId = realmId,
            userKeycloakId = "kc-user-456",
            userEmail = "jane@example.com",
            clientId = clientUuid,
            assignedByKeycloakId = actorId,
            assignedAt = Instant.parse("2024-02-01T10:00:00Z")
        )

        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        every { userClientRepository.findByClientId(clientUuid) } returns Flux.just(userClient, userClient2)

        val result = service.listUsers(clientUuid)

        assertEquals(2, result.size)
        assertEquals("john@example.com", result[0].email)
        assertEquals("kc-user-123", result[0].keycloakId)
        assertEquals(actorId, result[0].assignedBy)
        assertEquals(Instant.parse("2024-01-20T12:00:00Z"), result[0].assignedAt)
        assertEquals("jane@example.com", result[1].email)
        assertEquals("kc-user-456", result[1].keycloakId)
    }

    @Test
    fun `listUsers throws 404 for missing client`() = runTest {
        val missingId = UUID.randomUUID()

        every { clientRepository.findById(missingId) } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.listUsers(missingId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Client not found"))
    }

    // --- isUserAuthorized ---

    @Test
    fun `isUserAuthorized returns true when authorized`() = runTest {
        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        every { userClientRepository.existsByClientIdAndUserEmail(clientUuid, "john@example.com") } returns Mono.just(true)

        val result = service.isUserAuthorized(clientUuid, "john@example.com")

        assertTrue(result.authorized)
        assertEquals("john@example.com", result.email)
        assertEquals("my-app", result.clientId)
    }

    @Test
    fun `isUserAuthorized returns false when not authorized`() = runTest {
        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        every { userClientRepository.existsByClientIdAndUserEmail(clientUuid, "unknown@example.com") } returns Mono.just(false)

        val result = service.isUserAuthorized(clientUuid, "unknown@example.com")

        assertFalse(result.authorized)
        assertEquals("unknown@example.com", result.email)
        assertEquals("my-app", result.clientId)
    }

    // --- verifyClientAccess ---

    @Test
    fun `verifyClientAccess returns true for SUPER_ADMIN role`() = runTest {
        val jwt = mockk<Jwt>()
        every { jwt.getClaimAsStringList("realm_access") } returns null
        every { jwt.getClaim<Map<String, Any>>("realm_access") } returns mapOf("roles" to listOf("SUPER_ADMIN"))

        val result = service.verifyClientAccess(jwt, clientUuid)

        assertTrue(result)
    }

    @Test
    fun `verifyClientAccess returns true when azp matches clientId directly`() = runTest {
        val jwt = mockk<Jwt>()
        every { jwt.getClaimAsStringList("realm_access") } returns null
        every { jwt.getClaim<Map<String, Any>>("realm_access") } returns mapOf("roles" to listOf("USER"))
        every { jwt.getClaimAsString("azp") } returns "my-app"

        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)

        val result = service.verifyClientAccess(jwt, clientUuid)

        assertTrue(result)
    }

    @Test
    fun `verifyClientAccess returns true when azp matches paired client`() = runTest {
        // Client has a pairedClientId pointing to pairedClient
        val clientWithPairing = kcClient.copy(pairedClientId = pairedClientUuid)
        val jwt = mockk<Jwt>()
        every { jwt.getClaimAsStringList("realm_access") } returns null
        every { jwt.getClaim<Map<String, Any>>("realm_access") } returns mapOf("roles" to listOf("USER"))
        every { jwt.getClaimAsString("azp") } returns "my-app-backend"

        every { clientRepository.findById(clientUuid) } returns Mono.just(clientWithPairing)
        every { clientRepository.findById(pairedClientUuid) } returns Mono.just(pairedClient)

        val result = service.verifyClientAccess(jwt, clientUuid)

        assertTrue(result)
    }

    @Test
    fun `verifyClientAccess returns true when azp matches reverse paired client`() = runTest {
        // pairedClient has pairedClientId pointing to kcClient (reverse lookup)
        val jwt = mockk<Jwt>()
        every { jwt.getClaimAsStringList("realm_access") } returns null
        every { jwt.getClaim<Map<String, Any>>("realm_access") } returns mapOf("roles" to listOf("USER"))
        every { jwt.getClaimAsString("azp") } returns "my-app-backend"

        // kcClient has no pairedClientId
        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        // Reverse lookup finds pairedClient that has pairedClientId = clientUuid
        every { clientRepository.findByPairedClientId(clientUuid) } returns Mono.just(pairedClient)

        val result = service.verifyClientAccess(jwt, clientUuid)

        assertTrue(result)
    }

    @Test
    fun `verifyClientAccess returns false when no matching access`() = runTest {
        val jwt = mockk<Jwt>()
        every { jwt.getClaimAsStringList("realm_access") } returns null
        every { jwt.getClaim<Map<String, Any>>("realm_access") } returns mapOf("roles" to listOf("USER"))
        every { jwt.getClaimAsString("azp") } returns "some-other-app"

        // kcClient has no pairedClientId
        every { clientRepository.findById(clientUuid) } returns Mono.just(kcClient)
        // No reverse pairing either
        every { clientRepository.findByPairedClientId(clientUuid) } returns Mono.empty()

        val result = service.verifyClientAccess(jwt, clientUuid)

        assertFalse(result)
    }

    @Test
    fun `verifyClientAccess throws 404 for missing client`() = runTest {
        val jwt = mockk<Jwt>()
        every { jwt.getClaimAsStringList("realm_access") } returns null
        every { jwt.getClaim<Map<String, Any>>("realm_access") } returns mapOf("roles" to emptyList<String>())
        every { jwt.getClaimAsString("azp") } returns "my-app"

        val missingId = UUID.randomUUID()
        every { clientRepository.findById(missingId) } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.verifyClientAccess(jwt, missingId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Client not found"))
    }

    // --- getClientUuidByClientId ---

    @Test
    fun `getClientUuidByClientId returns UUID when found and null when not found`() = runTest {
        every { clientRepository.findByKeycloakId("my-app") } returns Mono.just(kcClient)
        every { clientRepository.findByKeycloakId("nonexistent") } returns Mono.empty()

        val foundId = service.getClientUuidByClientId("my-app")
        assertEquals(clientUuid, foundId)

        val notFoundId = service.getClientUuidByClientId("nonexistent")
        assertNull(notFoundId)
    }
}

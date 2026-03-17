package com.keeplearning.auth.realm.service

import com.keeplearning.auth.domain.entity.KcClient
import com.keeplearning.auth.domain.entity.KcRealm
import com.keeplearning.auth.domain.entity.KcUserClient
import com.keeplearning.auth.domain.entity.User
import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.KcUserClientRepository
import com.keeplearning.auth.domain.repository.UserRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.UserRepresentation
import com.keeplearning.auth.realm.dto.AssignClientsRequest
import com.keeplearning.auth.realm.dto.CreateRealmUserRequest
import com.keeplearning.auth.realm.dto.UpdateRealmUserRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
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
class UserServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var realmRepository: KcRealmRepository

    @MockK
    lateinit var clientRepository: KcClientRepository

    @MockK
    lateinit var userClientRepository: KcUserClientRepository

    @MockK
    lateinit var keycloakAdminClient: KeycloakAdminClient

    private lateinit var service: UserService

    private val realmId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val clientUUID = UUID.randomUUID()
    private val actorKeycloakId = "actor-kc-id-123"

    private val realm = KcRealm(
        id = realmId,
        accountId = accountId,
        realmName = "test-realm",
        keycloakId = "test-realm-kc-id"
    )

    private val user = User(
        id = userId,
        keycloakUserId = "kc-user-123",
        email = "john@example.com",
        displayName = "John Doe",
        firstName = "John",
        lastName = "Doe",
        phone = "+1-555-0100",
        jobTitle = "Engineer",
        department = "Engineering",
        accountId = accountId,
        realmId = realmId,
        status = "ACTIVE",
        createdAt = Instant.parse("2024-01-15T10:30:00Z")
    )

    private val kcClient = KcClient(
        id = clientUUID,
        realmId = realmId,
        clientId = "my-app",
        name = "My Application",
        publicClient = true,
        keycloakId = "kc-client-id-123"
    )

    private val userClient = KcUserClient(
        id = UUID.randomUUID(),
        realmId = realmId,
        userKeycloakId = "kc-user-123",
        userEmail = "john@example.com",
        clientId = clientUUID,
        assignedByKeycloakId = actorKeycloakId,
        assignedAt = Instant.parse("2024-01-20T12:00:00Z")
    )

    @BeforeEach
    fun setup() {
        service = UserService(
            userRepository,
            realmRepository,
            clientRepository,
            userClientRepository,
            keycloakAdminClient
        )
    }

    // --- listUsers ---

    @Test
    fun `listUsers returns mapped users from Keycloak`() = runTest {
        val kcUsers = listOf(
            UserRepresentation(
                id = userId.toString(),
                username = "johndoe",
                email = "john@example.com",
                firstName = "John",
                lastName = "Doe",
                enabled = true
            ),
            UserRepresentation(
                id = UUID.randomUUID().toString(),
                username = "janedoe",
                email = "jane@example.com",
                firstName = "Jane",
                lastName = "Doe",
                enabled = false
            )
        )

        coEvery { keycloakAdminClient.getUsers("test-realm", max = 1000) } returns kcUsers

        val result = service.listUsers("test-realm")

        assertEquals(2, result.size)
        assertEquals("john@example.com", result[0].email)
        assertEquals("johndoe", result[0].displayName)
        assertEquals("John", result[0].firstName)
        assertEquals("Doe", result[0].lastName)
        assertEquals("ACTIVE", result[0].status)
        assertEquals(userId, result[0].id)
        assertEquals(userId.toString(), result[0].keycloakUserId)
        assertTrue(result[0].authorizedClients.isEmpty())
    }

    @Test
    fun `listUsers maps enabled false to INACTIVE status`() = runTest {
        val kcUsers = listOf(
            UserRepresentation(
                id = UUID.randomUUID().toString(),
                username = "disabled-user",
                email = "disabled@example.com",
                enabled = false
            )
        )

        coEvery { keycloakAdminClient.getUsers("test-realm", max = 1000) } returns kcUsers

        val result = service.listUsers("test-realm")

        assertEquals(1, result.size)
        assertEquals("INACTIVE", result[0].status)
    }

    @Test
    fun `listUsers returns empty list when no users in Keycloak`() = runTest {
        coEvery { keycloakAdminClient.getUsers("test-realm", max = 1000) } returns emptyList()

        val result = service.listUsers("test-realm")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listUsers handles null email and id gracefully`() = runTest {
        val kcUsers = listOf(
            UserRepresentation(
                id = null,
                username = "no-email-user",
                email = null,
                enabled = true
            )
        )

        coEvery { keycloakAdminClient.getUsers("test-realm", max = 1000) } returns kcUsers

        val result = service.listUsers("test-realm")

        assertEquals(1, result.size)
        assertEquals("", result[0].email)
        assertEquals("", result[0].keycloakUserId)
        assertNotNull(result[0].id) // random UUID assigned as fallback
    }

    // --- getUser ---

    @Test
    fun `getUser returns detail response from Keycloak`() = runTest {
        val kcUser = UserRepresentation(
            id = userId.toString(),
            username = "johndoe",
            email = "john@example.com",
            firstName = "John",
            lastName = "Doe",
            enabled = true
        )

        coEvery { keycloakAdminClient.getUser("test-realm", userId.toString()) } returns kcUser

        val result = service.getUser("test-realm", userId)

        assertEquals(userId, result.id)
        assertEquals(userId.toString(), result.keycloakUserId)
        assertEquals("john@example.com", result.email)
        assertEquals("johndoe", result.displayName)
        assertEquals("John", result.firstName)
        assertEquals("Doe", result.lastName)
        assertEquals("ACTIVE", result.status)
        assertNotNull(result.createdAt)
        assertNotNull(result.updatedAt)
        assertTrue(result.authorizedClients.isEmpty())
    }

    @Test
    fun `getUser throws 404 when Keycloak call fails`() = runTest {
        coEvery {
            keycloakAdminClient.getUser("test-realm", userId.toString())
        } throws RuntimeException("User not found in Keycloak")

        val ex = assertThrows<ResponseStatusException> {
            service.getUser("test-realm", userId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User not found"))
    }

    @Test
    fun `getUser maps disabled user to INACTIVE`() = runTest {
        val kcUser = UserRepresentation(
            id = userId.toString(),
            username = "disabled",
            email = "disabled@example.com",
            enabled = false
        )

        coEvery { keycloakAdminClient.getUser("test-realm", userId.toString()) } returns kcUser

        val result = service.getUser("test-realm", userId)

        assertEquals("INACTIVE", result.status)
    }

    // --- getUserByEmail ---

    @Test
    fun `getUserByEmail returns detail response`() = runTest {
        val kcUser = UserRepresentation(
            id = userId.toString(),
            username = "johndoe",
            email = "john@example.com",
            firstName = "John",
            lastName = "Doe",
            enabled = true
        )

        coEvery { keycloakAdminClient.getUserByEmail("test-realm", "john@example.com") } returns kcUser

        val result = service.getUserByEmail("test-realm", "john@example.com")

        assertEquals(userId, result.id)
        assertEquals(userId.toString(), result.keycloakUserId)
        assertEquals("john@example.com", result.email)
        assertEquals("johndoe", result.displayName)
        assertEquals("John", result.firstName)
        assertEquals("Doe", result.lastName)
        assertEquals("ACTIVE", result.status)
        assertTrue(result.authorizedClients.isEmpty())
    }

    @Test
    fun `getUserByEmail throws 404 when user not found`() = runTest {
        coEvery {
            keycloakAdminClient.getUserByEmail("test-realm", "missing@example.com")
        } returns null

        val ex = assertThrows<ResponseStatusException> {
            service.getUserByEmail("test-realm", "missing@example.com")
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("missing@example.com"))
    }

    @Test
    fun `getUserByEmail falls back to email param when kcUser email is null`() = runTest {
        val kcUser = UserRepresentation(
            id = userId.toString(),
            username = "johndoe",
            email = null,
            enabled = true
        )

        coEvery { keycloakAdminClient.getUserByEmail("test-realm", "john@example.com") } returns kcUser

        val result = service.getUserByEmail("test-realm", "john@example.com")

        assertEquals("john@example.com", result.email)
    }

    @Test
    fun `getUserByEmail generates random UUID when kcUser id is not a valid UUID`() = runTest {
        val kcUser = UserRepresentation(
            id = "not-a-uuid",
            username = "johndoe",
            email = "john@example.com",
            enabled = true
        )

        coEvery { keycloakAdminClient.getUserByEmail("test-realm", "john@example.com") } returns kcUser

        val result = service.getUserByEmail("test-realm", "john@example.com")

        assertNotNull(result.id)
        assertEquals("not-a-uuid", result.keycloakUserId)
    }

    // --- createUser ---

    @Test
    fun `createUser creates user and assigns to clients`() = runTest {
        val request = CreateRealmUserRequest(
            email = "new@example.com",
            displayName = "New User",
            firstName = "New",
            lastName = "User",
            phone = "+1-555-0200",
            jobTitle = "Developer",
            department = "Engineering",
            clientIds = listOf(clientUUID)
        )

        val savedUser = User(
            id = userId,
            keycloakUserId = "",
            email = "new@example.com",
            displayName = "New User",
            firstName = "New",
            lastName = "User",
            phone = "+1-555-0200",
            jobTitle = "Developer",
            department = "Engineering",
            accountId = accountId,
            realmId = realmId,
            status = "ACTIVE"
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmIdAndEmail(realmId, "new@example.com") } returns Mono.empty()
        every { userRepository.save(any()) } returns Mono.just(savedUser)
        every { clientRepository.findById(clientUUID) } returns Mono.just(kcClient)
        every { userClientRepository.save(any()) } returns Mono.just(userClient)
        // For getAuthorizedClientDetailsForUser
        every { userRepository.findById(userId) } returns Mono.just(savedUser)
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "new@example.com") } returns Flux.just(userClient)
        every { clientRepository.findById(clientUUID) } returns Mono.just(kcClient)

        val result = service.createUser("test-realm", request, actorKeycloakId)

        assertEquals(userId, result.id)
        assertEquals("new@example.com", result.email)
        assertEquals("New User", result.displayName)
        assertEquals("New", result.firstName)
        assertEquals("User", result.lastName)
        assertEquals("ACTIVE", result.status)
        assertEquals(1, result.authorizedClients.size)
        assertEquals(clientUUID, result.authorizedClients[0].clientId)
        assertEquals("my-app", result.authorizedClients[0].clientIdName)

        verify { userRepository.save(match { it.email == "new@example.com" && it.realmId == realmId }) }
        verify { userClientRepository.save(any()) }
    }

    @Test
    fun `createUser without client assignments`() = runTest {
        val request = CreateRealmUserRequest(
            email = "solo@example.com",
            displayName = "Solo User",
            clientIds = emptyList()
        )

        val savedUser = User(
            id = userId,
            keycloakUserId = "",
            email = "solo@example.com",
            displayName = "Solo User",
            accountId = accountId,
            realmId = realmId,
            status = "ACTIVE"
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmIdAndEmail(realmId, "solo@example.com") } returns Mono.empty()
        every { userRepository.save(any()) } returns Mono.just(savedUser)
        // For getAuthorizedClientDetailsForUser
        every { userRepository.findById(userId) } returns Mono.just(savedUser)
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "solo@example.com") } returns Flux.empty()

        val result = service.createUser("test-realm", request, actorKeycloakId)

        assertEquals("solo@example.com", result.email)
        assertTrue(result.authorizedClients.isEmpty())
    }

    @Test
    fun `createUser throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val request = CreateRealmUserRequest(email = "new@example.com")

        val ex = assertThrows<ResponseStatusException> {
            service.createUser("nonexistent", request, actorKeycloakId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm"))
    }

    @Test
    fun `createUser throws 409 for duplicate email`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmIdAndEmail(realmId, "john@example.com") } returns Mono.just(user)

        val request = CreateRealmUserRequest(email = "john@example.com")

        val ex = assertThrows<ResponseStatusException> {
            service.createUser("test-realm", request, actorKeycloakId)
        }
        assertEquals(409, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("already exists"))
    }

    @Test
    fun `createUser uses realmId as accountId when realm accountId is null`() = runTest {
        val realmNoAccount = realm.copy(accountId = null)

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realmNoAccount)
        every { userRepository.findByRealmIdAndEmail(realmId, "test@example.com") } returns Mono.empty()
        every { userRepository.save(any()) } answers {
            Mono.just(firstArg<User>().copy(id = userId))
        }
        every { userRepository.findById(userId) } returns Mono.just(
            User(
                id = userId,
                keycloakUserId = "",
                email = "test@example.com",
                accountId = realmId,
                realmId = realmId,
                status = "ACTIVE"
            )
        )
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "test@example.com") } returns Flux.empty()

        val request = CreateRealmUserRequest(email = "test@example.com")
        service.createUser("test-realm", request, actorKeycloakId)

        verify { userRepository.save(match { it.accountId == realmId }) }
    }

    @Test
    fun `createUser skips client not belonging to realm`() = runTest {
        val otherRealmClientId = UUID.randomUUID()
        val otherRealmClient = kcClient.copy(
            id = otherRealmClientId,
            realmId = UUID.randomUUID() // different realm
        )

        val request = CreateRealmUserRequest(
            email = "new@example.com",
            clientIds = listOf(otherRealmClientId)
        )

        val savedUser = User(
            id = userId,
            keycloakUserId = "",
            email = "new@example.com",
            accountId = accountId,
            realmId = realmId,
            status = "ACTIVE"
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmIdAndEmail(realmId, "new@example.com") } returns Mono.empty()
        every { userRepository.save(any()) } returns Mono.just(savedUser)
        every { clientRepository.findById(otherRealmClientId) } returns Mono.just(otherRealmClient)
        every { userRepository.findById(userId) } returns Mono.just(savedUser)
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "new@example.com") } returns Flux.empty()

        val result = service.createUser("test-realm", request, actorKeycloakId)

        assertTrue(result.authorizedClients.isEmpty())
        verify(exactly = 0) { userClientRepository.save(any()) }
    }

    // --- updateUser ---

    @Test
    fun `updateUser updates fields and returns detail response`() = runTest {
        val request = UpdateRealmUserRequest(
            displayName = "John Updated",
            firstName = "Johnny",
            lastName = "Doer",
            phone = "+1-555-9999",
            bio = "A bio",
            jobTitle = "Senior Engineer",
            department = "Platform",
            status = "INACTIVE"
        )

        val updatedUser = user.copy(
            displayName = "John Updated",
            firstName = "Johnny",
            lastName = "Doer",
            phone = "+1-555-9999",
            bio = "A bio",
            jobTitle = "Senior Engineer",
            department = "Platform",
            status = "INACTIVE",
            updatedAt = Instant.now()
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { userRepository.save(any()) } returns Mono.just(updatedUser)
        // For getAuthorizedClientDetailsForUser
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "john@example.com") } returns Flux.empty()

        val result = service.updateUser("test-realm", userId, request)

        assertEquals("John Updated", result.displayName)
        assertEquals("Johnny", result.firstName)
        assertEquals("Doer", result.lastName)
        assertEquals("+1-555-9999", result.phone)
        assertEquals("A bio", result.bio)
        assertEquals("Senior Engineer", result.jobTitle)
        assertEquals("Platform", result.department)
        assertEquals("INACTIVE", result.status)

        verify {
            userRepository.save(match {
                it.displayName == "John Updated" &&
                    it.firstName == "Johnny" &&
                    it.status == "INACTIVE" &&
                    it.updatedAt != null
            })
        }
    }

    @Test
    fun `updateUser preserves existing fields when request fields are null`() = runTest {
        val request = UpdateRealmUserRequest(
            displayName = "Only Name Updated"
            // all other fields null — should keep originals
        )

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { userRepository.save(any()) } answers { Mono.just(firstArg<User>()) }
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "john@example.com") } returns Flux.empty()

        val result = service.updateUser("test-realm", userId, request)

        assertEquals("Only Name Updated", result.displayName)
        assertEquals("John", result.firstName) // preserved
        assertEquals("Doe", result.lastName) // preserved
        assertEquals("+1-555-0100", result.phone) // preserved
        assertEquals("Engineer", result.jobTitle) // preserved

        verify {
            userRepository.save(match {
                it.displayName == "Only Name Updated" &&
                    it.firstName == "John" &&
                    it.lastName == "Doe"
            })
        }
    }

    @Test
    fun `updateUser throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val request = UpdateRealmUserRequest(displayName = "test")

        val ex = assertThrows<ResponseStatusException> {
            service.updateUser("nonexistent", userId, request)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm"))
    }

    @Test
    fun `updateUser throws 404 for missing user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.empty()

        val request = UpdateRealmUserRequest(displayName = "test")

        val ex = assertThrows<ResponseStatusException> {
            service.updateUser("test-realm", userId, request)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User not found"))
    }

    @Test
    fun `updateUser throws 404 for user in wrong realm`() = runTest {
        val wrongRealmUser = user.copy(realmId = UUID.randomUUID())

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(wrongRealmUser)

        val request = UpdateRealmUserRequest(displayName = "test")

        val ex = assertThrows<ResponseStatusException> {
            service.updateUser("test-realm", userId, request)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User not found in realm"))
    }

    // --- deleteUser ---

    @Test
    fun `deleteUser deletes existing user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { userClientRepository.findByClientId(userId) } returns Flux.empty()
        every { userRepository.delete(user) } returns Mono.empty()

        service.deleteUser("test-realm", userId)

        verify { userRepository.delete(user) }
    }

    @Test
    fun `deleteUser throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.deleteUser("nonexistent", userId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm"))
    }

    @Test
    fun `deleteUser throws 404 for missing user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.deleteUser("test-realm", userId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User not found"))
    }

    @Test
    fun `deleteUser throws 404 for user in wrong realm`() = runTest {
        val wrongRealmUser = user.copy(realmId = UUID.randomUUID())

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(wrongRealmUser)

        val ex = assertThrows<ResponseStatusException> {
            service.deleteUser("test-realm", userId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User not found in realm"))
    }

    // --- assignUserToClients ---

    @Test
    fun `assignUserToClients assigns client and returns detail response`() = runTest {
        val request = AssignClientsRequest(clientIds = listOf(clientUUID))

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { clientRepository.findById(clientUUID) } returns Mono.just(kcClient)
        every { userClientRepository.findByClientIdAndUserEmail(clientUUID, "john@example.com") } returns Mono.empty()
        every { userClientRepository.save(any()) } returns Mono.just(userClient)
        // For getAuthorizedClientDetailsForUser
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "john@example.com") } returns Flux.just(userClient)

        val result = service.assignUserToClients("test-realm", userId, request, actorKeycloakId)

        assertEquals(userId, result.id)
        assertEquals(1, result.authorizedClients.size)
        assertEquals(clientUUID, result.authorizedClients[0].clientId)
        assertEquals("my-app", result.authorizedClients[0].clientIdName)
        assertEquals("My Application", result.authorizedClients[0].clientDisplayName)
        assertEquals(true, result.authorizedClients[0].publicClient)

        verify { userClientRepository.save(match { it.userEmail == "john@example.com" && it.clientId == clientUUID }) }
    }

    @Test
    fun `assignUserToClients skips already assigned client`() = runTest {
        val request = AssignClientsRequest(clientIds = listOf(clientUUID))

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { clientRepository.findById(clientUUID) } returns Mono.just(kcClient)
        every { userClientRepository.findByClientIdAndUserEmail(clientUUID, "john@example.com") } returns Mono.just(userClient)
        // For getAuthorizedClientDetailsForUser
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "john@example.com") } returns Flux.just(userClient)

        service.assignUserToClients("test-realm", userId, request, actorKeycloakId)

        // save should NOT be called since user is already assigned
        verify(exactly = 0) { userClientRepository.save(any()) }
    }

    @Test
    fun `assignUserToClients handles multiple clients with mixed assignment`() = runTest {
        val newClientId = UUID.randomUUID()
        val newClient = kcClient.copy(id = newClientId, clientId = "new-app", name = "New App")
        val newUserClient = userClient.copy(clientId = newClientId)

        val request = AssignClientsRequest(clientIds = listOf(clientUUID, newClientId))

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { clientRepository.findById(clientUUID) } returns Mono.just(kcClient)
        every { clientRepository.findById(newClientId) } returns Mono.just(newClient)
        // Already assigned to first client
        every { userClientRepository.findByClientIdAndUserEmail(clientUUID, "john@example.com") } returns Mono.just(userClient)
        // Not yet assigned to second client
        every { userClientRepository.findByClientIdAndUserEmail(newClientId, "john@example.com") } returns Mono.empty()
        every { userClientRepository.save(any()) } returns Mono.just(newUserClient)
        // For getAuthorizedClientDetailsForUser
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "john@example.com") } returns Flux.just(userClient, newUserClient)

        service.assignUserToClients("test-realm", userId, request, actorKeycloakId)

        // save called only once (for the new client)
        verify(exactly = 1) { userClientRepository.save(any()) }
    }

    @Test
    fun `assignUserToClients throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val request = AssignClientsRequest(clientIds = listOf(clientUUID))

        val ex = assertThrows<ResponseStatusException> {
            service.assignUserToClients("nonexistent", userId, request, actorKeycloakId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm"))
    }

    @Test
    fun `assignUserToClients throws 404 for missing user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.empty()

        val request = AssignClientsRequest(clientIds = listOf(clientUUID))

        val ex = assertThrows<ResponseStatusException> {
            service.assignUserToClients("test-realm", userId, request, actorKeycloakId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User not found"))
    }

    @Test
    fun `assignUserToClients throws 404 for user in wrong realm`() = runTest {
        val wrongRealmUser = user.copy(realmId = UUID.randomUUID())

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(wrongRealmUser)

        val request = AssignClientsRequest(clientIds = listOf(clientUUID))

        val ex = assertThrows<ResponseStatusException> {
            service.assignUserToClients("test-realm", userId, request, actorKeycloakId)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User not found in realm"))
    }

    // --- removeUserFromClient ---

    @Test
    fun `removeUserFromClient removes association and returns detail response`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { userClientRepository.deleteByClientIdAndUserEmail(clientUUID, "john@example.com") } returns Mono.empty()
        // For getAuthorizedClientDetailsForUser (after removal, no clients left)
        every { userClientRepository.findByRealmIdAndUserEmail(realmId, "john@example.com") } returns Flux.empty()

        val result = service.removeUserFromClient("test-realm", userId, clientUUID)

        assertEquals(userId, result.id)
        assertEquals("john@example.com", result.email)
        assertTrue(result.authorizedClients.isEmpty())

        verify { userClientRepository.deleteByClientIdAndUserEmail(clientUUID, "john@example.com") }
    }

    @Test
    fun `removeUserFromClient throws 404 for missing realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.removeUserFromClient("nonexistent", userId, clientUUID)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm"))
    }

    @Test
    fun `removeUserFromClient throws 404 for missing user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.removeUserFromClient("test-realm", userId, clientUUID)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User not found"))
    }

    @Test
    fun `removeUserFromClient throws 404 for user in wrong realm`() = runTest {
        val wrongRealmUser = user.copy(realmId = UUID.randomUUID())

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(wrongRealmUser)

        val ex = assertThrows<ResponseStatusException> {
            service.removeUserFromClient("test-realm", userId, clientUUID)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User not found in realm"))
    }
}

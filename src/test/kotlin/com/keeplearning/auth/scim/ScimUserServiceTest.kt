package com.keeplearning.auth.scim

import com.keeplearning.auth.domain.entity.KcRealm
import com.keeplearning.auth.domain.entity.User
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.UserRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ScimUserServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var realmRepository: KcRealmRepository

    @MockK
    lateinit var databaseClient: DatabaseClient

    private lateinit var service: ScimUserService

    private val realmId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val baseUrl = "https://auth.example.com"

    private val realm = KcRealm(
        id = realmId,
        accountId = accountId,
        realmName = "test-realm",
        keycloakId = "test-realm-kc-id"
    )

    private val user = User(
        id = userId,
        keycloakUserId = "kc-123",
        email = "john@example.com",
        displayName = "John Doe",
        firstName = "John",
        lastName = "Doe",
        phone = "+1-555-0100",
        jobTitle = "Engineer",
        accountId = accountId,
        realmId = realmId,
        status = "ACTIVE",
        createdAt = Instant.parse("2024-01-15T10:30:00Z")
    )

    @BeforeEach
    fun setup() {
        service = ScimUserService(userRepository, realmRepository, databaseClient)
    }

    // --- GET single user ---

    @Test
    fun `getUser returns SCIM resource for existing user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)

        val result = service.getUser("test-realm", userId, baseUrl)

        assertEquals(userId.toString(), result.id)
        assertEquals("john@example.com", result.userName)
        assertEquals("John", result.name?.givenName)
        assertEquals("Doe", result.name?.familyName)
        assertTrue(result.active)
    }

    @Test
    fun `getUser throws 404 for non-existent user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.empty()

        val ex = assertThrows<ScimException> {
            service.getUser("test-realm", userId, baseUrl)
        }
        assertEquals(404, ex.status)
    }

    @Test
    fun `getUser throws 404 for non-existent realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex = assertThrows<ScimException> {
            service.getUser("nonexistent", userId, baseUrl)
        }
        assertEquals(404, ex.status)
        assertTrue(ex.detail!!.contains("Realm"))
    }

    // --- LIST users (no filter) ---

    @Test
    fun `listUsers returns paginated results`() = runTest {
        val users = (1..5).map { i ->
            user.copy(id = UUID.randomUUID(), email = "user$i@example.com")
        }

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmId(realmId) } returns Flux.fromIterable(users)

        val result = service.listUsers("test-realm", null, 1, 3, baseUrl)

        assertEquals(5, result.totalResults)
        assertEquals(3, result.itemsPerPage)
        assertEquals(1, result.startIndex)
        assertEquals(3, result.resources.size)
    }

    @Test
    fun `listUsers with startIndex offsets results`() = runTest {
        val users = (1..5).map { i ->
            user.copy(id = UUID.randomUUID(), email = "user$i@example.com")
        }

        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmId(realmId) } returns Flux.fromIterable(users)

        val result = service.listUsers("test-realm", null, 4, 10, baseUrl)

        assertEquals(5, result.totalResults)
        assertEquals(2, result.itemsPerPage) // only 2 remaining from index 4
        assertEquals(4, result.startIndex)
    }

    @Test
    fun `listUsers returns empty for realm with no users`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmId(realmId) } returns Flux.empty()

        val result = service.listUsers("test-realm", null, 1, 100, baseUrl)

        assertEquals(0, result.totalResults)
        assertEquals(0, result.itemsPerPage)
        assertTrue(result.resources.isEmpty())
    }

    // --- CREATE user ---

    @Test
    fun `createUser creates new user and returns SCIM resource`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmIdAndEmail(realmId, "new@example.com") } returns Mono.empty()
        every { userRepository.save(any()) } answers {
            val saved = firstArg<User>()
            Mono.just(saved.copy(id = UUID.randomUUID()))
        }

        val resource = ScimUserResource(
            userName = "new@example.com",
            name = ScimName(givenName = "New", familyName = "User"),
            displayName = "New User",
            title = "Developer",
            active = true
        )

        val result = service.createUser("test-realm", resource, baseUrl)

        assertNotNull(result.id)
        assertEquals("new@example.com", result.userName)
        assertEquals("New", result.name?.givenName)
        assertEquals("Developer", result.title)
        assertTrue(result.active)

        verify { userRepository.save(match { it.email == "new@example.com" && it.realmId == realmId }) }
    }

    @Test
    fun `createUser throws 409 for duplicate userName`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmIdAndEmail(realmId, "john@example.com") } returns Mono.just(user)

        val resource = ScimUserResource(userName = "john@example.com")

        val ex = assertThrows<ScimException> {
            service.createUser("test-realm", resource, baseUrl)
        }
        assertEquals(409, ex.status)
        assertEquals("uniqueness", ex.scimType)
    }

    @Test
    fun `createUser uses accountId from realm`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmIdAndEmail(realmId, "test@example.com") } returns Mono.empty()
        every { userRepository.save(any()) } answers {
            val saved = firstArg<User>()
            Mono.just(saved.copy(id = UUID.randomUUID()))
        }

        val resource = ScimUserResource(userName = "test@example.com")
        service.createUser("test-realm", resource, baseUrl)

        verify { userRepository.save(match { it.accountId == accountId }) }
    }

    // --- REPLACE user (PUT) ---

    @Test
    fun `replaceUser updates all fields`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { userRepository.save(any()) } answers { Mono.just(firstArg<User>()) }

        val resource = ScimUserResource(
            userName = "updated@example.com",
            name = ScimName(givenName = "Updated", familyName = "Name"),
            displayName = "Updated Name",
            phoneNumbers = listOf(ScimPhoneNumber(value = "+1-999-0000")),
            title = "CTO",
            active = false
        )

        val result = service.replaceUser("test-realm", userId, resource, baseUrl)

        assertEquals("updated@example.com", result.userName)
        assertEquals("Updated", result.name?.givenName)
        assertEquals("Name", result.name?.familyName)
        assertEquals("CTO", result.title)
        assertEquals(false, result.active)

        verify {
            userRepository.save(match {
                it.email == "updated@example.com" &&
                    it.firstName == "Updated" &&
                    it.status == "INACTIVE" &&
                    it.updatedAt != null
            })
        }
    }

    @Test
    fun `replaceUser throws 404 for non-existent user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.empty()

        val resource = ScimUserResource(userName = "test@example.com")

        val ex = assertThrows<ScimException> {
            service.replaceUser("test-realm", userId, resource, baseUrl)
        }
        assertEquals(404, ex.status)
    }

    // --- PATCH user ---

    @Test
    fun `patchUser applies operations and returns updated resource`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { userRepository.save(any()) } answers { Mono.just(firstArg<User>()) }

        val request = ScimPatchRequest(
            operations = listOf(
                ScimPatchOperation(op = "replace", path = "displayName", value = "Johnny Doe"),
                ScimPatchOperation(op = "replace", path = "active", value = "false")
            )
        )

        val result = service.patchUser("test-realm", userId, request, baseUrl)

        assertEquals("Johnny Doe", result.displayName)
        assertEquals(false, result.active)

        verify {
            userRepository.save(match {
                it.displayName == "Johnny Doe" && it.status == "INACTIVE"
            })
        }
    }

    @Test
    fun `patchUser throws 404 for non-existent user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.empty()

        val request = ScimPatchRequest(
            operations = listOf(
                ScimPatchOperation(op = "replace", path = "active", value = "false")
            )
        )

        val ex = assertThrows<ScimException> {
            service.patchUser("test-realm", userId, request, baseUrl)
        }
        assertEquals(404, ex.status)
    }

    // --- DELETE user ---

    @Test
    fun `deleteUser removes existing user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)
        every { userRepository.delete(user) } returns Mono.empty()

        service.deleteUser("test-realm", userId)

        verify { userRepository.delete(user) }
    }

    @Test
    fun `deleteUser throws 404 for non-existent user`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.empty()

        val ex = assertThrows<ScimException> {
            service.deleteUser("test-realm", userId)
        }
        assertEquals(404, ex.status)
    }

    @Test
    fun `deleteUser throws 404 for non-existent realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val ex = assertThrows<ScimException> {
            service.deleteUser("nonexistent", userId)
        }
        assertEquals(404, ex.status)
    }

    // --- SCIM resource structure ---

    @Test
    fun `getUser returns correct SCIM schema`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findById(userId) } returns Mono.just(user)

        val result = service.getUser("test-realm", userId, baseUrl)

        assertEquals(listOf(ScimSchemas.USER), result.schemas)
        assertEquals("User", result.meta?.resourceType)
        assertNotNull(result.meta?.location)
        assertTrue(result.meta!!.location!!.contains("/api/scim/v2/Users/"))
    }

    @Test
    fun `listUsers returns correct SCIM list schema`() = runTest {
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { userRepository.findByRealmId(realmId) } returns Flux.just(user)

        val result = service.listUsers("test-realm", null, 1, 100, baseUrl)

        assertEquals(listOf(ScimSchemas.LIST_RESPONSE), result.schemas)
        assertEquals(1, result.totalResults)
    }

    // --- Realm accountId fallback ---

    @Test
    fun `createUser falls back to realmId when accountId is null`() = runTest {
        val realmNoAccount = realm.copy(accountId = null)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realmNoAccount)
        every { userRepository.findByRealmIdAndEmail(realmId, "test@example.com") } returns Mono.empty()
        every { userRepository.save(any()) } answers {
            Mono.just(firstArg<User>().copy(id = UUID.randomUUID()))
        }

        val resource = ScimUserResource(userName = "test@example.com")
        service.createUser("test-realm", resource, baseUrl)

        verify { userRepository.save(match { it.accountId == realmId }) }
    }
}

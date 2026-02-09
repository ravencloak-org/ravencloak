package com.keeplearning.forge.repository

import com.keeplearning.auth.scim.common.*
import com.keeplearning.forge.client.ScimClient
import com.keeplearning.forge.exception.AuthException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultAuthRepositoryTest {

    private lateinit var scimClient: ScimClient
    private lateinit var repository: DefaultAuthRepository<TestUser>

    class TestUser : AuthUser()

    @BeforeEach
    fun setup() {
        scimClient = mockk()
        repository = DefaultAuthRepository(scimClient) { TestUser() }
    }

    @Test
    fun `findById maps SCIM resource to entity`() = runTest {
        val userId = UUID.randomUUID()
        val resource = ScimUserResource(
            id = userId.toString(),
            userName = "john@example.com",
            name = ScimName(givenName = "John", familyName = "Doe"),
            displayName = "John Doe",
            phoneNumbers = listOf(ScimPhoneNumber(value = "+1-555-0100")),
            title = "Engineer",
            active = true,
            meta = ScimMeta(
                created = "2024-01-15T10:30:00Z",
                lastModified = "2024-06-20T14:22:00Z"
            )
        )

        coEvery { scimClient.getUser(userId) } returns resource

        val result = repository.findById(userId.toString())

        assertNotNull(result)
        assertEquals(userId.toString(), result.id)
        assertEquals("john@example.com", result.email)
        assertEquals("John", result.firstName)
        assertEquals("Doe", result.lastName)
        assertEquals("John Doe", result.displayName)
        assertEquals("+1-555-0100", result.phone)
        assertEquals("Engineer", result.jobTitle)
        assertTrue(result.active)
        assertNotNull(result.createdAt)
        assertNotNull(result.updatedAt)
    }

    @Test
    fun `findById returns null on 404`() = runTest {
        val userId = UUID.randomUUID()

        coEvery { scimClient.getUser(userId) } throws AuthException(status = 404)

        val result = repository.findById(userId.toString())

        assertNull(result)
    }

    @Test
    fun `findById rethrows non-404 exceptions`() = runTest {
        val userId = UUID.randomUUID()

        coEvery { scimClient.getUser(userId) } throws AuthException(status = 500)

        assertThrows<AuthException> {
            repository.findById(userId.toString())
        }
    }

    @Test
    fun `findByEmail constructs filter and returns user`() = runTest {
        val resource = ScimUserResource(
            id = UUID.randomUUID().toString(),
            userName = "john@example.com",
            active = true
        )

        coEvery {
            scimClient.listUsers(
                filter = "userName eq \"john@example.com\"",
                count = 1
            )
        } returns ScimListResponse(
            totalResults = 1,
            startIndex = 1,
            itemsPerPage = 1,
            resources = listOf(resource)
        )

        val result = repository.findByEmail("john@example.com")

        assertNotNull(result)
        assertEquals("john@example.com", result.email)
    }

    @Test
    fun `findByEmail returns null when no results`() = runTest {
        coEvery {
            scimClient.listUsers(
                filter = "userName eq \"notfound@example.com\"",
                count = 1
            )
        } returns ScimListResponse(
            totalResults = 0,
            startIndex = 1,
            itemsPerPage = 0,
            resources = emptyList()
        )

        val result = repository.findByEmail("notfound@example.com")

        assertNull(result)
    }

    @Test
    fun `findAll delegates pagination`() = runTest {
        val response = ScimListResponse(
            totalResults = 50,
            startIndex = 11,
            itemsPerPage = 10,
            resources = (1..10).map {
                ScimUserResource(
                    id = UUID.randomUUID().toString(),
                    userName = "user$it@example.com",
                    active = true
                )
            }
        )

        coEvery {
            scimClient.listUsers(filter = null, startIndex = 11, count = 10)
        } returns response

        val result = repository.findAll(startIndex = 11, count = 10)

        assertEquals(50, result.totalResults)
        assertEquals(10, result.resources.size)
    }

    @Test
    fun `create maps entity to SCIM and returns mapped result`() = runTest {
        val user = TestUser().apply {
            email = "new@example.com"
            firstName = "New"
            lastName = "User"
            displayName = "New User"
            active = true
        }

        val createdResource = ScimUserResource(
            id = UUID.randomUUID().toString(),
            userName = "new@example.com",
            name = ScimName(givenName = "New", familyName = "User"),
            displayName = "New User",
            active = true,
            meta = ScimMeta(
                created = "2024-01-15T10:30:00Z",
                lastModified = "2024-01-15T10:30:00Z"
            )
        )

        coEvery { scimClient.createUser(any()) } returns createdResource

        val result = repository.create(user)

        assertNotNull(result.id)
        assertEquals("new@example.com", result.email)
        assertEquals("New", result.firstName)

        coVerify { scimClient.createUser(match { it.userName == "new@example.com" }) }
    }

    @Test
    fun `update requires non-null ID`() = runTest {
        val user = TestUser().apply {
            email = "test@example.com"
        }

        assertThrows<IllegalArgumentException> {
            repository.update(user)
        }
    }

    @Test
    fun `update delegates to replaceUser`() = runTest {
        val userId = UUID.randomUUID()
        val user = TestUser().apply {
            id = userId.toString()
            email = "updated@example.com"
            firstName = "Updated"
            active = true
        }

        val updatedResource = ScimUserResource(
            id = userId.toString(),
            userName = "updated@example.com",
            name = ScimName(givenName = "Updated"),
            active = true
        )

        coEvery { scimClient.replaceUser(userId, any()) } returns updatedResource

        val result = repository.update(user)

        assertEquals("updated@example.com", result.email)
        coVerify { scimClient.replaceUser(userId, any()) }
    }

    @Test
    fun `createAll sends bulk POST request`() = runTest {
        val users = listOf(
            TestUser().apply {
                email = "user1@example.com"
                firstName = "User"
                lastName = "One"
            },
            TestUser().apply {
                email = "user2@example.com"
                firstName = "User"
                lastName = "Two"
            }
        )

        val bulkResponse = ScimBulkResponse(
            operations = listOf(
                ScimBulkOperationResponse(method = "POST", bulkId = "user1@example.com", status = "201"),
                ScimBulkOperationResponse(method = "POST", bulkId = "user2@example.com", status = "201")
            )
        )

        coEvery { scimClient.bulkRequest(any()) } returns bulkResponse

        val result = repository.createAll(users)

        assertEquals(2, result.operations.size)
        assertEquals("201", result.operations[0].status)

        coVerify {
            scimClient.bulkRequest(match { req ->
                req.operations.size == 2 &&
                    req.operations.all { it.method == "POST" && it.path == "/Users" }
            })
        }
    }

    @Test
    fun `updateAll sends bulk PUT request`() = runTest {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val users = listOf(
            TestUser().apply {
                id = id1.toString()
                email = "user1@example.com"
                firstName = "Updated"
                lastName = "One"
            },
            TestUser().apply {
                id = id2.toString()
                email = "user2@example.com"
                firstName = "Updated"
                lastName = "Two"
            }
        )

        val bulkResponse = ScimBulkResponse(
            operations = listOf(
                ScimBulkOperationResponse(method = "PUT", bulkId = "user1@example.com", status = "200"),
                ScimBulkOperationResponse(method = "PUT", bulkId = "user2@example.com", status = "200")
            )
        )

        coEvery { scimClient.bulkRequest(any()) } returns bulkResponse

        val result = repository.updateAll(users)

        assertEquals(2, result.operations.size)

        coVerify {
            scimClient.bulkRequest(match { req ->
                req.operations.size == 2 &&
                    req.operations.all { it.method == "PUT" } &&
                    req.operations[0].path == "/Users/$id1" &&
                    req.operations[1].path == "/Users/$id2"
            })
        }
    }

    @Test
    fun `updateAll requires non-null IDs`() = runTest {
        val users = listOf(
            TestUser().apply { email = "test@example.com" }
        )

        assertThrows<IllegalArgumentException> {
            repository.updateAll(users)
        }
    }

    @Test
    fun `patch delegates to ScimClient`() = runTest {
        val userId = UUID.randomUUID()
        val ops = listOf(
            ScimPatchOperation(op = "replace", path = "displayName", value = "Patched")
        )

        val patchedResource = ScimUserResource(
            id = userId.toString(),
            userName = "john@example.com",
            displayName = "Patched",
            active = true
        )

        coEvery { scimClient.patchUser(userId, any()) } returns patchedResource

        val result = repository.patch(userId.toString(), ops)

        assertEquals("Patched", result.displayName)
    }

    @Test
    fun `delete delegates to ScimClient`() = runTest {
        val userId = UUID.randomUUID()

        coEvery { scimClient.deleteUser(userId) } returns Unit

        repository.delete(userId.toString())

        coVerify { scimClient.deleteUser(userId) }
    }
}

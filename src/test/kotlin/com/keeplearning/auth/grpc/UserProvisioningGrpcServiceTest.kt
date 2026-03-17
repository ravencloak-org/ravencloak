package com.keeplearning.auth.grpc

import com.keeplearning.auth.domain.entity.KcRealm
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.UserRepository
import com.keeplearning.auth.grpc.provisioning.v1.*
import com.keeplearning.auth.scim.ScimChecksumService
import com.keeplearning.auth.scim.ScimException
import com.keeplearning.auth.scim.ScimUserService
import com.keeplearning.auth.scim.common.*
import io.grpc.StatusException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class UserProvisioningGrpcServiceTest {

    @MockK
    lateinit var scimUserService: ScimUserService

    @MockK
    lateinit var checksumService: ScimChecksumService

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var realmRepository: KcRealmRepository

    private lateinit var grpcService: UserProvisioningGrpcService

    private val realmName = "test-realm"
    private val realmId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    private val realm = KcRealm(
        id = realmId,
        accountId = accountId,
        realmName = realmName,
        keycloakId = "test-realm-kc-id"
    )

    private val scimUser = ScimUserResource(
        id = userId.toString(),
        userName = "john@example.com",
        externalId = "ext-123",
        name = ScimName(givenName = "John", familyName = "Doe"),
        displayName = "John Doe",
        phoneNumbers = listOf(ScimPhoneNumber(value = "+1-555-0100")),
        title = "Engineer",
        active = true,
        meta = ScimMeta(
            resourceType = "User",
            created = "2024-01-15T10:30:00Z",
            lastModified = "2024-01-15T10:30:00Z"
        )
    )

    @BeforeEach
    fun setup() {
        grpcService = UserProvisioningGrpcService(
            scimUserService, checksumService, userRepository, realmRepository
        )
    }

    // ==================== CreateUser ====================

    @Test
    fun `createUser returns proto User on success`() = runTest {
        val request = createUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            email = "john@example.com"
            externalId = "ext-123"
            firstName = "John"
            lastName = "Doe"
            displayName = "John Doe"
            phone = "+1-555-0100"
            jobTitle = "Engineer"
            active = true
        }

        coEvery { scimUserService.createUser(realmName, any(), any()) } returns scimUser

        val result = grpcService.createUser(request)

        assertEquals(userId.toString(), result.id)
        assertEquals("john@example.com", result.email)
        assertEquals("John", result.firstName)
        assertEquals("Doe", result.lastName)
        assertEquals("John Doe", result.displayName)
        assertEquals("+1-555-0100", result.phone)
        assertEquals("Engineer", result.jobTitle)
        assertTrue(result.active)

        coVerify { scimUserService.createUser(realmName, match { it.userName == "john@example.com" }, any()) }
    }

    @Test
    fun `createUser throws ALREADY_EXISTS for duplicate email`() = runTest {
        val request = createUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            email = "john@example.com"
            active = true
        }

        coEvery { scimUserService.createUser(realmName, any(), any()) } throws
            ScimException(409, scimType = "uniqueness", detail = "User already exists")

        val ex = assertThrows<StatusException> {
            grpcService.createUser(request)
        }
        assertEquals(io.grpc.Status.ALREADY_EXISTS.code, ex.status.code)
    }

    @Test
    fun `createUser throws NOT_FOUND for non-existent realm`() = runTest {
        val request = createUserRequest {
            this.realmName = "nonexistent"
            email = "john@example.com"
            active = true
        }

        coEvery { scimUserService.createUser("nonexistent", any(), any()) } throws
            ScimException(404, detail = "Realm 'nonexistent' not found")

        val ex = assertThrows<StatusException> {
            grpcService.createUser(request)
        }
        assertEquals(io.grpc.Status.NOT_FOUND.code, ex.status.code)
    }

    // ==================== GetUser ====================

    @Test
    fun `getUser returns proto User for existing user`() = runTest {
        val request = getUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = this@UserProvisioningGrpcServiceTest.userId.toString()
        }

        coEvery { scimUserService.getUser(realmName, userId, any()) } returns scimUser

        val result = grpcService.getUser(request)

        assertEquals(userId.toString(), result.id)
        assertEquals("john@example.com", result.email)
        assertEquals("John", result.firstName)
        assertEquals("Doe", result.lastName)
    }

    @Test
    fun `getUser throws NOT_FOUND for non-existent user`() = runTest {
        val request = getUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = this@UserProvisioningGrpcServiceTest.userId.toString()
        }

        coEvery { scimUserService.getUser(realmName, userId, any()) } throws
            ScimException(404, detail = "User not found")

        val ex = assertThrows<StatusException> {
            grpcService.getUser(request)
        }
        assertEquals(io.grpc.Status.NOT_FOUND.code, ex.status.code)
    }

    @Test
    fun `getUser throws INVALID_ARGUMENT for malformed UUID`() = runTest {
        val request = getUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = "not-a-uuid"
        }

        val ex = assertThrows<StatusException> {
            grpcService.getUser(request)
        }
        assertEquals(io.grpc.Status.INVALID_ARGUMENT.code, ex.status.code)
        assertTrue(ex.status.description!!.contains("Invalid UUID"))
    }

    // ==================== ListUsers ====================

    @Test
    fun `listUsers returns paginated response`() = runTest {
        val scimListResponse = ScimListResponse(
            totalResults = 5,
            startIndex = 1,
            itemsPerPage = 3,
            resources = listOf(
                scimUser,
                scimUser.copy(id = UUID.randomUUID().toString(), userName = "jane@example.com"),
                scimUser.copy(id = UUID.randomUUID().toString(), userName = "bob@example.com")
            )
        )

        val request = listUsersRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            startIndex = 1
            count = 3
        }

        coEvery { scimUserService.listUsers(realmName, null, 1, 3, any()) } returns scimListResponse

        val result = grpcService.listUsers(request)

        assertEquals(5, result.totalResults)
        assertEquals(1, result.startIndex)
        assertEquals(3, result.itemsPerPage)
        assertEquals(3, result.usersList.size)
        assertEquals("john@example.com", result.usersList[0].email)
    }

    @Test
    fun `listUsers with filter passes filter to service`() = runTest {
        val scimListResponse = ScimListResponse(
            totalResults = 1,
            startIndex = 1,
            itemsPerPage = 1,
            resources = listOf(scimUser)
        )

        val request = listUsersRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            filter = """userName eq "john@example.com""""
            startIndex = 1
            count = 100
        }

        coEvery {
            scimUserService.listUsers(realmName, """userName eq "john@example.com"""", 1, 100, any())
        } returns scimListResponse

        val result = grpcService.listUsers(request)

        assertEquals(1, result.totalResults)
        assertEquals("john@example.com", result.usersList[0].email)
    }

    @Test
    fun `listUsers uses default startIndex and count when not specified`() = runTest {
        val scimListResponse = ScimListResponse(
            totalResults = 0,
            startIndex = 1,
            itemsPerPage = 0,
            resources = emptyList()
        )

        val request = listUsersRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            // startIndex and count default to 0 in proto
        }

        coEvery { scimUserService.listUsers(realmName, null, 1, 100, any()) } returns scimListResponse

        val result = grpcService.listUsers(request)

        assertEquals(0, result.totalResults)
    }

    // ==================== UpdateUser ====================

    @Test
    fun `updateUser returns updated proto User`() = runTest {
        val updatedScimUser = scimUser.copy(
            userName = "updated@example.com",
            displayName = "Updated Name",
            title = "CTO"
        )

        val request = updateUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = this@UserProvisioningGrpcServiceTest.userId.toString()
            email = "updated@example.com"
            externalId = "ext-123"
            displayName = "Updated Name"
            jobTitle = "CTO"
            active = true
        }

        coEvery { scimUserService.replaceUser(realmName, userId, any(), any()) } returns updatedScimUser

        val result = grpcService.updateUser(request)

        assertEquals("updated@example.com", result.email)
        assertEquals("Updated Name", result.displayName)
        assertEquals("CTO", result.jobTitle)
    }

    @Test
    fun `updateUser throws NOT_FOUND for non-existent user`() = runTest {
        val request = updateUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = this@UserProvisioningGrpcServiceTest.userId.toString()
            email = "test@example.com"
            active = true
        }

        coEvery { scimUserService.replaceUser(realmName, userId, any(), any()) } throws
            ScimException(404, detail = "User not found")

        val ex = assertThrows<StatusException> {
            grpcService.updateUser(request)
        }
        assertEquals(io.grpc.Status.NOT_FOUND.code, ex.status.code)
    }

    @Test
    fun `updateUser throws INVALID_ARGUMENT for malformed UUID`() = runTest {
        val request = updateUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = "bad-uuid"
            email = "test@example.com"
            active = true
        }

        val ex = assertThrows<StatusException> {
            grpcService.updateUser(request)
        }
        assertEquals(io.grpc.Status.INVALID_ARGUMENT.code, ex.status.code)
    }

    // ==================== DeleteUser ====================

    @Test
    fun `deleteUser returns empty response on success`() = runTest {
        val request = deleteUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = this@UserProvisioningGrpcServiceTest.userId.toString()
        }

        coEvery { scimUserService.deleteUser(realmName, userId) } returns Unit

        val result = grpcService.deleteUser(request)

        assertNotNull(result)
        coVerify { scimUserService.deleteUser(realmName, userId) }
    }

    @Test
    fun `deleteUser throws NOT_FOUND for non-existent user`() = runTest {
        val request = deleteUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = this@UserProvisioningGrpcServiceTest.userId.toString()
        }

        coEvery { scimUserService.deleteUser(realmName, userId) } throws
            ScimException(404, detail = "User not found")

        val ex = assertThrows<StatusException> {
            grpcService.deleteUser(request)
        }
        assertEquals(io.grpc.Status.NOT_FOUND.code, ex.status.code)
    }

    @Test
    fun `deleteUser throws INVALID_ARGUMENT for malformed UUID`() = runTest {
        val request = deleteUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = "invalid"
        }

        val ex = assertThrows<StatusException> {
            grpcService.deleteUser(request)
        }
        assertEquals(io.grpc.Status.INVALID_ARGUMENT.code, ex.status.code)
    }

    // ==================== BulkCreateUsers ====================

    @Test
    fun `bulkCreateUsers returns results for each user`() = runTest {
        val user1Id = UUID.randomUUID()
        val user2Id = UUID.randomUUID()

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)

        coEvery { scimUserService.createUser(realmName, match { it.userName == "user1@example.com" }, any()) } returns
            scimUser.copy(id = user1Id.toString(), userName = "user1@example.com")

        coEvery { scimUserService.createUser(realmName, match { it.userName == "user2@example.com" }, any()) } returns
            scimUser.copy(id = user2Id.toString(), userName = "user2@example.com")

        val request = bulkCreateUsersRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            users += createUserData {
                email = "user1@example.com"
                active = true
            }
            users += createUserData {
                email = "user2@example.com"
                active = true
            }
        }

        val result = grpcService.bulkCreateUsers(request)

        assertEquals(2, result.resultsList.size)
        assertEquals(0, result.resultsList[0].status) // OK
        assertEquals(0, result.resultsList[1].status) // OK
        assertEquals(user1Id.toString(), result.resultsList[0].userId)
        assertEquals(user2Id.toString(), result.resultsList[1].userId)
    }

    @Test
    fun `bulkCreateUsers reports per-item errors without aborting batch`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)

        coEvery { scimUserService.createUser(realmName, match { it.userName == "existing@example.com" }, any()) } throws
            ScimException(409, detail = "User already exists")

        coEvery { scimUserService.createUser(realmName, match { it.userName == "new@example.com" }, any()) } returns
            scimUser.copy(id = UUID.randomUUID().toString(), userName = "new@example.com")

        val request = bulkCreateUsersRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            users += createUserData {
                email = "existing@example.com"
                active = true
            }
            users += createUserData {
                email = "new@example.com"
                active = true
            }
        }

        val result = grpcService.bulkCreateUsers(request)

        assertEquals(2, result.resultsList.size)
        // First user failed with ALREADY_EXISTS
        assertEquals(io.grpc.Status.Code.ALREADY_EXISTS.value(), result.resultsList[0].status)
        assertTrue(result.resultsList[0].errorMessage.contains("already exists"))
        // Second user succeeded
        assertEquals(0, result.resultsList[1].status)
    }

    @Test
    fun `bulkCreateUsers throws NOT_FOUND for non-existent realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val request = bulkCreateUsersRequest {
            this.realmName = "nonexistent"
            users += createUserData {
                email = "user@example.com"
                active = true
            }
        }

        val ex = assertThrows<StatusException> {
            grpcService.bulkCreateUsers(request)
        }
        assertEquals(io.grpc.Status.NOT_FOUND.code, ex.status.code)
    }

    // ==================== BulkUpdateUsers ====================

    @Test
    fun `bulkUpdateUsers returns results for each user`() = runTest {
        val user1Id = UUID.randomUUID()
        val user2Id = UUID.randomUUID()

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)

        coEvery { scimUserService.replaceUser(realmName, user1Id, any(), any()) } returns
            scimUser.copy(id = user1Id.toString(), userName = "updated1@example.com")

        coEvery { scimUserService.replaceUser(realmName, user2Id, any(), any()) } returns
            scimUser.copy(id = user2Id.toString(), userName = "updated2@example.com")

        val request = bulkUpdateUsersRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            users += updateUserData {
                this.userId = user1Id.toString()
                email = "updated1@example.com"
                active = true
            }
            users += updateUserData {
                this.userId = user2Id.toString()
                email = "updated2@example.com"
                active = true
            }
        }

        val result = grpcService.bulkUpdateUsers(request)

        assertEquals(2, result.resultsList.size)
        assertEquals(0, result.resultsList[0].status)
        assertEquals(0, result.resultsList[1].status)
    }

    @Test
    fun `bulkUpdateUsers reports per-item errors without aborting batch`() = runTest {
        val existingId = UUID.randomUUID()
        val missingId = UUID.randomUUID()

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)

        coEvery { scimUserService.replaceUser(realmName, missingId, any(), any()) } throws
            ScimException(404, detail = "User not found")

        coEvery { scimUserService.replaceUser(realmName, existingId, any(), any()) } returns
            scimUser.copy(id = existingId.toString())

        val request = bulkUpdateUsersRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            users += updateUserData {
                this.userId = missingId.toString()
                email = "missing@example.com"
                active = true
            }
            users += updateUserData {
                this.userId = existingId.toString()
                email = "existing@example.com"
                active = true
            }
        }

        val result = grpcService.bulkUpdateUsers(request)

        assertEquals(2, result.resultsList.size)
        assertEquals(io.grpc.Status.Code.NOT_FOUND.value(), result.resultsList[0].status)
        assertEquals(0, result.resultsList[1].status)
    }

    @Test
    fun `bulkUpdateUsers throws NOT_FOUND for non-existent realm`() = runTest {
        every { realmRepository.findByRealmName("nonexistent") } returns Mono.empty()

        val request = bulkUpdateUsersRequest {
            this.realmName = "nonexistent"
            users += updateUserData {
                this.userId = UUID.randomUUID().toString()
                email = "user@example.com"
                active = true
            }
        }

        val ex = assertThrows<StatusException> {
            grpcService.bulkUpdateUsers(request)
        }
        assertEquals(io.grpc.Status.NOT_FOUND.code, ex.status.code)
    }

    // ==================== GetChecksum ====================

    @Test
    fun `getChecksum returns checksum and user count`() = runTest {
        val checksumResponse = ScimChecksumResponse(
            checksum = "abc123def456",
            userCount = 42
        )

        val request = getChecksumRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
        }

        coEvery { checksumService.getChecksum(realmName, any()) } returns checksumResponse

        val result = grpcService.getChecksum(request)

        assertEquals("abc123def456", result.checksum)
        assertEquals(42, result.userCount)
    }

    @Test
    fun `getChecksum throws NOT_FOUND for non-existent realm`() = runTest {
        val request = getChecksumRequest {
            this.realmName = "nonexistent"
        }

        coEvery { checksumService.getChecksum("nonexistent", any()) } throws
            ScimException(404, detail = "Realm 'nonexistent' not found")

        val ex = assertThrows<StatusException> {
            grpcService.getChecksum(request)
        }
        assertEquals(io.grpc.Status.NOT_FOUND.code, ex.status.code)
    }

    // ==================== Error handling ====================

    @Test
    fun `handleScimErrors maps 400 to INVALID_ARGUMENT`() = runTest {
        val request = createUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            email = "bad"
            active = true
        }

        coEvery { scimUserService.createUser(realmName, any(), any()) } throws
            ScimException(400, detail = "Invalid email format")

        val ex = assertThrows<StatusException> {
            grpcService.createUser(request)
        }
        assertEquals(io.grpc.Status.INVALID_ARGUMENT.code, ex.status.code)
        assertTrue(ex.status.description!!.contains("Invalid email"))
    }

    @Test
    fun `handleScimErrors maps unexpected exceptions to INTERNAL`() = runTest {
        val request = createUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            email = "test@example.com"
            active = true
        }

        coEvery { scimUserService.createUser(realmName, any(), any()) } throws
            RuntimeException("Database connection failed")

        val ex = assertThrows<StatusException> {
            grpcService.createUser(request)
        }
        assertEquals(io.grpc.Status.INTERNAL.code, ex.status.code)
    }

    // ==================== Proto mapping ====================

    @Test
    fun `toProtoUser maps all optional fields correctly`() = runTest {
        val request = getUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = this@UserProvisioningGrpcServiceTest.userId.toString()
        }

        coEvery { scimUserService.getUser(realmName, userId, any()) } returns scimUser

        val result = grpcService.getUser(request)

        assertEquals(userId.toString(), result.id)
        assertEquals("john@example.com", result.email)
        assertEquals("ext-123", result.externalId)
        assertEquals("John", result.firstName)
        assertEquals("Doe", result.lastName)
        assertEquals("John Doe", result.displayName)
        assertEquals("+1-555-0100", result.phone)
        assertEquals("Engineer", result.jobTitle)
        assertTrue(result.active)
        assertEquals("2024-01-15T10:30:00Z", result.createdAt)
        assertEquals("2024-01-15T10:30:00Z", result.updatedAt)
    }

    @Test
    fun `toProtoUser handles null optional fields`() = runTest {
        val minimalScimUser = ScimUserResource(
            id = userId.toString(),
            userName = "minimal@example.com",
            active = false,
            meta = ScimMeta(resourceType = "User")
        )

        val request = getUserRequest {
            this.realmName = this@UserProvisioningGrpcServiceTest.realmName
            this.userId = this@UserProvisioningGrpcServiceTest.userId.toString()
        }

        coEvery { scimUserService.getUser(realmName, userId, any()) } returns minimalScimUser

        val result = grpcService.getUser(request)

        assertEquals(userId.toString(), result.id)
        assertEquals("minimal@example.com", result.email)
        assertEquals("", result.externalId)
        assertEquals(false, result.hasFirstName())
        assertEquals(false, result.hasLastName())
        assertEquals(false, result.hasDisplayName())
        assertEquals(false, result.hasPhone())
        assertEquals(false, result.hasJobTitle())
        assertEquals(false, result.active)
    }
}

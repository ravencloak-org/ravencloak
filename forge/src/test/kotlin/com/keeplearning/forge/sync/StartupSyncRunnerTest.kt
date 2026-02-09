package com.keeplearning.forge.sync

import com.keeplearning.auth.scim.common.*
import com.keeplearning.forge.client.ScimClient
import com.keeplearning.forge.repository.AuthUser
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.util.UUID

class StartupSyncRunnerTest {

    private lateinit var scimClient: ScimClient
    private lateinit var authStartupSync: AuthStartupSync<TestLocalUser>
    private lateinit var runner: StartupSyncRunner<TestLocalUser>

    data class TestLocalUser(val email: String, val first: String, val last: String)

    class TestAuthStartupSync(private val users: List<TestLocalUser>) : AuthStartupSync<TestLocalUser>() {
        override suspend fun fetchAllLocalUsers(): List<TestLocalUser> = users

        override fun mapToAuthUser(localUser: TestLocalUser): AuthUser {
            return object : AuthUser() {
                init {
                    email = localUser.email
                    firstName = localUser.first
                    lastName = localUser.last
                    active = true
                }
            }
        }
    }

    @BeforeEach
    fun setup() {
        scimClient = mockk()
    }

    @Test
    fun `checksums match - no sync needed`() = runTest {
        val localUsers = listOf(
            TestLocalUser("alice@example.com", "Alice", "Smith")
        )
        authStartupSync = TestAuthStartupSync(localUsers)
        runner = StartupSyncRunner(authStartupSync, scimClient)

        val localResource = ScimUserResource(
            userName = "alice@example.com",
            name = ScimName(givenName = "Alice", familyName = "Smith"),
            active = true
        )
        val checksum = ScimChecksumUtil.computeChecksum(listOf(localResource))

        coEvery { scimClient.getChecksum() } returns ScimChecksumResponse(
            checksum = checksum,
            userCount = 1
        )

        runner.performSync()

        coVerify(exactly = 0) { scimClient.bulkRequest(any()) }
        coVerify(exactly = 0) { scimClient.listUsers(any(), any(), any()) }
    }

    @Test
    fun `new users are created via bulk POST`() = runTest {
        val localUsers = listOf(
            TestLocalUser("alice@example.com", "Alice", "Smith"),
            TestLocalUser("bob@example.com", "Bob", "Jones")
        )
        authStartupSync = TestAuthStartupSync(localUsers)
        runner = StartupSyncRunner(authStartupSync, scimClient)

        coEvery { scimClient.getChecksum() } returns ScimChecksumResponse(
            checksum = "different-checksum",
            userCount = 0
        )

        coEvery { scimClient.listUsers(startIndex = 1, count = 100) } returns ScimListResponse(
            totalResults = 0,
            startIndex = 1,
            itemsPerPage = 0,
            resources = emptyList()
        )

        coEvery { scimClient.bulkRequest(any()) } returns ScimBulkResponse(
            operations = listOf(
                ScimBulkOperationResponse(method = "POST", bulkId = "alice@example.com", status = "201"),
                ScimBulkOperationResponse(method = "POST", bulkId = "bob@example.com", status = "201")
            )
        )

        runner.performSync()

        coVerify {
            scimClient.bulkRequest(match { req ->
                req.operations.size == 2 &&
                    req.operations.all { it.method == "POST" }
            })
        }
    }

    @Test
    fun `existing users with differences are updated via bulk PUT`() = runTest {
        val localUsers = listOf(
            TestLocalUser("alice@example.com", "Alicia", "Smith")
        )
        authStartupSync = TestAuthStartupSync(localUsers)
        runner = StartupSyncRunner(authStartupSync, scimClient)

        val remoteId = UUID.randomUUID().toString()

        coEvery { scimClient.getChecksum() } returns ScimChecksumResponse(
            checksum = "different-checksum",
            userCount = 1
        )

        coEvery { scimClient.listUsers(startIndex = 1, count = 100) } returns ScimListResponse(
            totalResults = 1,
            startIndex = 1,
            itemsPerPage = 1,
            resources = listOf(
                ScimUserResource(
                    id = remoteId,
                    userName = "alice@example.com",
                    name = ScimName(givenName = "Alice", familyName = "Smith"),
                    active = true
                )
            )
        )

        coEvery { scimClient.bulkRequest(any()) } returns ScimBulkResponse(
            operations = listOf(
                ScimBulkOperationResponse(method = "PUT", bulkId = "alice@example.com", status = "200")
            )
        )

        runner.performSync()

        coVerify {
            scimClient.bulkRequest(match { req ->
                req.operations.size == 1 &&
                    req.operations[0].method == "PUT" &&
                    req.operations[0].path == "/Users/$remoteId"
            })
        }
    }

    @Test
    fun `mixed create and update operations`() = runTest {
        val localUsers = listOf(
            TestLocalUser("alice@example.com", "Alicia", "Smith"),
            TestLocalUser("charlie@example.com", "Charlie", "Brown")
        )
        authStartupSync = TestAuthStartupSync(localUsers)
        runner = StartupSyncRunner(authStartupSync, scimClient)

        val remoteId = UUID.randomUUID().toString()

        coEvery { scimClient.getChecksum() } returns ScimChecksumResponse(
            checksum = "different-checksum",
            userCount = 1
        )

        coEvery { scimClient.listUsers(startIndex = 1, count = 100) } returns ScimListResponse(
            totalResults = 1,
            startIndex = 1,
            itemsPerPage = 1,
            resources = listOf(
                ScimUserResource(
                    id = remoteId,
                    userName = "alice@example.com",
                    name = ScimName(givenName = "Alice", familyName = "Smith"),
                    active = true
                )
            )
        )

        coEvery { scimClient.bulkRequest(any()) } returns ScimBulkResponse(
            operations = listOf(
                ScimBulkOperationResponse(method = "POST", bulkId = "charlie@example.com", status = "201"),
                ScimBulkOperationResponse(method = "PUT", bulkId = "alice@example.com", status = "200")
            )
        )

        runner.performSync()

        coVerify {
            scimClient.bulkRequest(match { req ->
                req.operations.size == 2 &&
                    req.operations.any { it.method == "POST" && it.bulkId == "charlie@example.com" } &&
                    req.operations.any { it.method == "PUT" && it.bulkId == "alice@example.com" }
            })
        }
    }

    @Test
    fun `remote-only users are ignored (no delete)`() = runTest {
        val localUsers = listOf(
            TestLocalUser("alice@example.com", "Alice", "Smith")
        )
        authStartupSync = TestAuthStartupSync(localUsers)
        runner = StartupSyncRunner(authStartupSync, scimClient)

        coEvery { scimClient.getChecksum() } returns ScimChecksumResponse(
            checksum = "different-checksum",
            userCount = 2
        )

        val aliceResource = ScimUserResource(
            userName = "alice@example.com",
            name = ScimName(givenName = "Alice", familyName = "Smith"),
            active = true
        )

        coEvery { scimClient.listUsers(startIndex = 1, count = 100) } returns ScimListResponse(
            totalResults = 2,
            startIndex = 1,
            itemsPerPage = 2,
            resources = listOf(
                aliceResource.copy(id = UUID.randomUUID().toString()),
                ScimUserResource(
                    id = UUID.randomUUID().toString(),
                    userName = "orphan@example.com",
                    name = ScimName(givenName = "Orphan"),
                    active = true
                )
            )
        )

        // Checksums differ because remote has extra user, but local alice matches remote alice
        // The local checksum was computed from just alice, remote has alice + orphan
        // Since alice's canonical forms match, no updates needed. No creates either.
        // So no bulk request should be made.

        runner.performSync()

        coVerify(exactly = 0) { scimClient.bulkRequest(any()) }
    }

    @Test
    fun `exception during sync is non-fatal`() {
        val localUsers = listOf(
            TestLocalUser("alice@example.com", "Alice", "Smith")
        )
        authStartupSync = TestAuthStartupSync(localUsers)
        runner = StartupSyncRunner(authStartupSync, scimClient)

        coEvery { scimClient.getChecksum() } throws RuntimeException("Connection refused")

        // Should not throw - the ApplicationRunner.run wraps in try/catch
        runner.run(DefaultApplicationArguments())
    }

    @Test
    fun `empty local users with non-empty remote triggers no operations`() = runTest {
        authStartupSync = TestAuthStartupSync(emptyList())
        runner = StartupSyncRunner(authStartupSync, scimClient)

        val emptyChecksum = ScimChecksumUtil.computeChecksum(emptyList())

        coEvery { scimClient.getChecksum() } returns ScimChecksumResponse(
            checksum = "different-checksum",
            userCount = 5
        )

        coEvery { scimClient.listUsers(startIndex = 1, count = 100) } returns ScimListResponse(
            totalResults = 1,
            startIndex = 1,
            itemsPerPage = 1,
            resources = listOf(
                ScimUserResource(
                    id = UUID.randomUUID().toString(),
                    userName = "existing@example.com",
                    active = true
                )
            )
        )

        runner.performSync()

        coVerify(exactly = 0) { scimClient.bulkRequest(any()) }
    }
}

package com.keeplearning.auth.keycloak.service

import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.ClientRepresentation
import com.keeplearning.auth.keycloak.client.dto.RoleRepresentation
import com.keeplearning.auth.keycloak.client.dto.UserRepresentation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class AccountUserRoleServiceTest {

    @MockK
    lateinit var keycloakAdminClient: KeycloakAdminClient

    private lateinit var service: AccountUserRoleService

    private val testEmail = "john@example.com"
    private val userId = "user-123"
    private val clientUuid = "ecs-client-uuid"

    private val userRepresentation = UserRepresentation(
        id = userId,
        username = "johndoe",
        email = testEmail,
        firstName = "John",
        lastName = "Doe",
        enabled = true,
        attributes = mapOf(
            "approval_scopes" to listOf("scope1", "scope2")
        )
    )

    private val ecsClient = ClientRepresentation(
        id = clientUuid,
        clientId = "ecs"
    )

    private val adminRole = RoleRepresentation(
        id = "role-1",
        name = "admin",
        description = "Administrator role",
        clientRole = true,
        containerId = clientUuid
    )

    private val editorRole = RoleRepresentation(
        id = "role-2",
        name = "editor",
        description = "Editor role",
        clientRole = true,
        containerId = clientUuid
    )

    private val viewerRole = RoleRepresentation(
        id = "role-3",
        name = "viewer",
        description = "Viewer role",
        clientRole = true,
        containerId = clientUuid
    )

    @BeforeEach
    fun setup() {
        service = AccountUserRoleService(keycloakAdminClient)
    }

    // ==================== getUserRolesByEmail ====================

    @Test
    fun `getUserRolesByEmail returns user roles and approval scopes`() = runTest {
        coEvery { keycloakAdminClient.getUserByEmail("kos", testEmail) } returns userRepresentation
        coEvery { keycloakAdminClient.getClientByClientId("kos", "ecs") } returns ecsClient
        coEvery {
            keycloakAdminClient.getUserClientRoleMappings("kos", userId, clientUuid)
        } returns listOf(adminRole, editorRole)

        val result = service.getUserRolesByEmail(testEmail)

        @Suppress("UNCHECKED_CAST")
        val roles = result["roles"] as List<String>
        @Suppress("UNCHECKED_CAST")
        val approvalScopes = result["approval_scopes"] as List<String>

        assertEquals(2, roles.size)
        assertTrue(roles.contains("admin"))
        assertTrue(roles.contains("editor"))
        assertEquals(2, approvalScopes.size)
        assertTrue(approvalScopes.contains("scope1"))
        assertTrue(approvalScopes.contains("scope2"))

        coVerify { keycloakAdminClient.getUserByEmail("kos", testEmail) }
        coVerify { keycloakAdminClient.getClientByClientId("kos", "ecs") }
        coVerify { keycloakAdminClient.getUserClientRoleMappings("kos", userId, clientUuid) }
    }

    @Test
    fun `getUserRolesByEmail throws 404 when user not found by email`() = runTest {
        coEvery { keycloakAdminClient.getUserByEmail("kos", "missing@example.com") } returns null

        val exception = assertThrows<ResponseStatusException> {
            service.getUserRolesByEmail("missing@example.com")
        }

        assertEquals(404, exception.statusCode.value())
        assertTrue(exception.reason!!.contains("User not found"))
    }

    @Test
    fun `getUserRolesByEmail throws 404 when ECS client not found`() = runTest {
        coEvery { keycloakAdminClient.getUserByEmail("kos", testEmail) } returns userRepresentation
        coEvery { keycloakAdminClient.getClientByClientId("kos", "ecs") } returns null

        val exception = assertThrows<ResponseStatusException> {
            service.getUserRolesByEmail(testEmail)
        }

        assertEquals(404, exception.statusCode.value())
        assertTrue(exception.reason!!.contains("ECS client not found"))
    }

    // ==================== updateUserRolesByEmail ====================

    @Test
    fun `updateUserRolesByEmail updates roles with correct diff - adds new and removes old`() = runTest {
        // User currently has admin and editor; we want editor and viewer
        // So admin should be removed, viewer should be added, editor stays
        coEvery { keycloakAdminClient.getUserByEmail("kos", testEmail) } returns userRepresentation
        coEvery { keycloakAdminClient.getClientByClientId("kos", "ecs") } returns ecsClient
        coEvery { keycloakAdminClient.getClientRoles("kos", clientUuid) } returns listOf(adminRole, editorRole, viewerRole)
        coEvery {
            keycloakAdminClient.getUserClientRoleMappings("kos", userId, clientUuid)
        } returns listOf(adminRole, editorRole)
        coEvery { keycloakAdminClient.removeUserClientRoleMappings("kos", userId, clientUuid, any()) } returns Unit
        coEvery { keycloakAdminClient.addUserClientRoleMappings("kos", userId, clientUuid, any()) } returns Unit
        coEvery { keycloakAdminClient.updateUser("kos", userId, any()) } returns Unit

        val result = service.updateUserRolesByEmail(testEmail, listOf("editor", "viewer"), listOf("scopeA"))

        @Suppress("UNCHECKED_CAST")
        val roles = result["roles"] as List<String>
        @Suppress("UNCHECKED_CAST")
        val approvalScopes = result["approval_scopes"] as List<String>

        assertEquals(listOf("editor", "viewer"), roles)
        assertEquals(listOf("scopeA"), approvalScopes)

        // Verify admin was removed
        coVerify {
            keycloakAdminClient.removeUserClientRoleMappings(
                "kos", userId, clientUuid,
                match { it.size == 1 && it[0].name == "admin" }
            )
        }

        // Verify viewer was added
        coVerify {
            keycloakAdminClient.addUserClientRoleMappings(
                "kos", userId, clientUuid,
                match { it.size == 1 && it[0].name == "viewer" }
            )
        }

        // Verify user attributes were updated
        coVerify {
            keycloakAdminClient.updateUser("kos", userId, match { user ->
                user.attributes != null &&
                    user.attributes!!["approval_scopes"] == listOf("scopeA")
            })
        }
    }

    @Test
    fun `updateUserRolesByEmail handles case where user has no existing roles`() = runTest {
        coEvery { keycloakAdminClient.getUserByEmail("kos", testEmail) } returns userRepresentation
        coEvery { keycloakAdminClient.getClientByClientId("kos", "ecs") } returns ecsClient
        coEvery { keycloakAdminClient.getClientRoles("kos", clientUuid) } returns listOf(adminRole, editorRole, viewerRole)
        coEvery {
            keycloakAdminClient.getUserClientRoleMappings("kos", userId, clientUuid)
        } returns emptyList()
        coEvery { keycloakAdminClient.addUserClientRoleMappings("kos", userId, clientUuid, any()) } returns Unit
        coEvery { keycloakAdminClient.updateUser("kos", userId, any()) } returns Unit

        val result = service.updateUserRolesByEmail(testEmail, listOf("admin", "viewer"), listOf("scopeX"))

        @Suppress("UNCHECKED_CAST")
        val roles = result["roles"] as List<String>
        assertEquals(listOf("admin", "viewer"), roles)

        // No roles to remove since user had none
        coVerify(exactly = 0) {
            keycloakAdminClient.removeUserClientRoleMappings(any(), any(), any(), any())
        }

        // Both roles should be added
        coVerify {
            keycloakAdminClient.addUserClientRoleMappings(
                "kos", userId, clientUuid,
                match { it.size == 2 && it.any { r -> r.name == "admin" } && it.any { r -> r.name == "viewer" } }
            )
        }
    }

    @Test
    fun `updateUserRolesByEmail handles case where desired roles match existing - no changes needed`() = runTest {
        coEvery { keycloakAdminClient.getUserByEmail("kos", testEmail) } returns userRepresentation
        coEvery { keycloakAdminClient.getClientByClientId("kos", "ecs") } returns ecsClient
        coEvery { keycloakAdminClient.getClientRoles("kos", clientUuid) } returns listOf(adminRole, editorRole)
        coEvery {
            keycloakAdminClient.getUserClientRoleMappings("kos", userId, clientUuid)
        } returns listOf(adminRole, editorRole)
        coEvery { keycloakAdminClient.updateUser("kos", userId, any()) } returns Unit

        val result = service.updateUserRolesByEmail(testEmail, listOf("admin", "editor"), listOf("scope1"))

        @Suppress("UNCHECKED_CAST")
        val roles = result["roles"] as List<String>
        assertEquals(listOf("admin", "editor"), roles)

        // No add or remove calls since roles already match
        coVerify(exactly = 0) {
            keycloakAdminClient.addUserClientRoleMappings(any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            keycloakAdminClient.removeUserClientRoleMappings(any(), any(), any(), any())
        }

        // User attributes should still be updated
        coVerify { keycloakAdminClient.updateUser("kos", userId, any()) }
    }

    @Test
    fun `updateUserRolesByEmail updates approval_scopes attribute correctly`() = runTest {
        // User has existing attributes; approval_scopes should be updated
        val userWithAttributes = userRepresentation.copy(
            attributes = mapOf(
                "department" to listOf("Engineering"),
                "approval_scopes" to listOf("old_scope")
            )
        )

        coEvery { keycloakAdminClient.getUserByEmail("kos", testEmail) } returns userWithAttributes
        coEvery { keycloakAdminClient.getClientByClientId("kos", "ecs") } returns ecsClient
        coEvery { keycloakAdminClient.getClientRoles("kos", clientUuid) } returns listOf(adminRole)
        coEvery {
            keycloakAdminClient.getUserClientRoleMappings("kos", userId, clientUuid)
        } returns listOf(adminRole)
        coEvery { keycloakAdminClient.updateUser("kos", userId, any()) } returns Unit

        service.updateUserRolesByEmail(testEmail, listOf("admin"), listOf("new_scope1", "new_scope2"))

        coVerify {
            keycloakAdminClient.updateUser("kos", userId, match { user ->
                user.attributes != null &&
                    user.attributes!!["approval_scopes"] == listOf("new_scope1", "new_scope2") &&
                    user.attributes!!["department"] == listOf("Engineering")
            })
        }
    }

    @Test
    fun `updateUserRolesByEmail removes approval_scopes attribute when empty list provided`() = runTest {
        coEvery { keycloakAdminClient.getUserByEmail("kos", testEmail) } returns userRepresentation
        coEvery { keycloakAdminClient.getClientByClientId("kos", "ecs") } returns ecsClient
        coEvery { keycloakAdminClient.getClientRoles("kos", clientUuid) } returns listOf(adminRole)
        coEvery {
            keycloakAdminClient.getUserClientRoleMappings("kos", userId, clientUuid)
        } returns listOf(adminRole)
        coEvery { keycloakAdminClient.updateUser("kos", userId, any()) } returns Unit

        service.updateUserRolesByEmail(testEmail, listOf("admin"), emptyList())

        coVerify {
            keycloakAdminClient.updateUser("kos", userId, match { user ->
                user.attributes != null &&
                    !user.attributes!!.containsKey("approval_scopes")
            })
        }
    }

    @Test
    fun `updateUserRolesByEmail throws 404 when user not found`() = runTest {
        coEvery { keycloakAdminClient.getUserByEmail("kos", "missing@example.com") } returns null

        val exception = assertThrows<ResponseStatusException> {
            service.updateUserRolesByEmail("missing@example.com", listOf("admin"), emptyList())
        }

        assertEquals(404, exception.statusCode.value())
        assertTrue(exception.reason!!.contains("User not found"))
    }
}

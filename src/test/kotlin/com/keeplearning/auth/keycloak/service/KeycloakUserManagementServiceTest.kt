package com.keeplearning.auth.keycloak.service

import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.*
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
class KeycloakUserManagementServiceTest {

    @MockK
    lateinit var keycloakAdminClient: KeycloakAdminClient

    private lateinit var service: KeycloakUserManagementService

    private val realmName = "test-realm"
    private val userId = "user-123"
    private val clientId = "my-app"
    private val clientUuid = "client-uuid-456"

    private val clientRepresentation = ClientRepresentation(
        id = clientUuid,
        clientId = clientId
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
        service = KeycloakUserManagementService(keycloakAdminClient)
    }

    // ==================== getUserClientRoles ====================

    @Test
    fun `getUserClientRoles returns assigned and available roles`() = runTest {
        coEvery { keycloakAdminClient.getClientByClientId(realmName, clientId) } returns clientRepresentation
        coEvery { keycloakAdminClient.getUserClientRoleMappings(realmName, userId, clientUuid) } returns listOf(adminRole)
        coEvery { keycloakAdminClient.getClientRoles(realmName, clientUuid) } returns listOf(adminRole, editorRole, viewerRole)

        val result = service.getUserClientRoles(realmName, userId, clientId)

        assertEquals(1, result.clientRoles.size)
        assertEquals("admin", result.clientRoles[0].name)
        assertEquals(2, result.availableClientRoles.size)
        assertTrue(result.availableClientRoles.any { it.name == "editor" })
        assertTrue(result.availableClientRoles.any { it.name == "viewer" })

        coVerify { keycloakAdminClient.getClientByClientId(realmName, clientId) }
        coVerify { keycloakAdminClient.getUserClientRoleMappings(realmName, userId, clientUuid) }
        coVerify { keycloakAdminClient.getClientRoles(realmName, clientUuid) }
    }

    @Test
    fun `getUserClientRoles throws 404 when client not found`() = runTest {
        coEvery { keycloakAdminClient.getClientByClientId(realmName, clientId) } returns null

        val exception = assertThrows<ResponseStatusException> {
            service.getUserClientRoles(realmName, userId, clientId)
        }

        assertTrue(exception.message!!.contains("Client '$clientId' not found"))
    }

    @Test
    fun `getUserClientRoles throws 500 when client UUID is null`() = runTest {
        val clientWithoutId = ClientRepresentation(id = null, clientId = clientId)
        coEvery { keycloakAdminClient.getClientByClientId(realmName, clientId) } returns clientWithoutId

        val exception = assertThrows<ResponseStatusException> {
            service.getUserClientRoles(realmName, userId, clientId)
        }

        assertTrue(exception.message!!.contains("Client UUID not available"))
    }

    // ==================== assignClientRoles ====================

    @Test
    fun `assignClientRoles looks up roles and assigns them`() = runTest {
        val request = AssignClientRolesRequest(clientId = clientId, roles = listOf("admin", "editor"))

        coEvery { keycloakAdminClient.getClientByClientId(realmName, clientId) } returns clientRepresentation
        coEvery { keycloakAdminClient.getClientRoles(realmName, clientUuid) } returns listOf(adminRole, editorRole, viewerRole)
        coEvery { keycloakAdminClient.addUserClientRoleMappings(realmName, userId, clientUuid, any()) } returns Unit

        service.assignClientRoles(realmName, userId, request)

        coVerify {
            keycloakAdminClient.addUserClientRoleMappings(
                realmName,
                userId,
                clientUuid,
                match { roles ->
                    roles.size == 2 &&
                        roles.any { it.name == "admin" } &&
                        roles.any { it.name == "editor" }
                }
            )
        }
    }

    @Test
    fun `assignClientRoles throws 404 when role name not found`() = runTest {
        val request = AssignClientRolesRequest(clientId = clientId, roles = listOf("nonexistent-role"))

        coEvery { keycloakAdminClient.getClientByClientId(realmName, clientId) } returns clientRepresentation
        coEvery { keycloakAdminClient.getClientRoles(realmName, clientUuid) } returns listOf(adminRole, editorRole)

        val exception = assertThrows<ResponseStatusException> {
            service.assignClientRoles(realmName, userId, request)
        }

        assertTrue(exception.message!!.contains("Role 'nonexistent-role' not found"))
    }

    @Test
    fun `assignClientRoles throws 404 when client not found`() = runTest {
        val request = AssignClientRolesRequest(clientId = clientId, roles = listOf("admin"))

        coEvery { keycloakAdminClient.getClientByClientId(realmName, clientId) } returns null

        val exception = assertThrows<ResponseStatusException> {
            service.assignClientRoles(realmName, userId, request)
        }

        assertTrue(exception.message!!.contains("Client '$clientId' not found"))
    }

    // ==================== removeClientRoles ====================

    @Test
    fun `removeClientRoles looks up roles and removes them`() = runTest {
        val request = AssignClientRolesRequest(clientId = clientId, roles = listOf("admin"))

        coEvery { keycloakAdminClient.getClientByClientId(realmName, clientId) } returns clientRepresentation
        coEvery { keycloakAdminClient.getClientRoles(realmName, clientUuid) } returns listOf(adminRole, editorRole, viewerRole)
        coEvery { keycloakAdminClient.removeUserClientRoleMappings(realmName, userId, clientUuid, any()) } returns Unit

        service.removeClientRoles(realmName, userId, request)

        coVerify {
            keycloakAdminClient.removeUserClientRoleMappings(
                realmName,
                userId,
                clientUuid,
                match { roles ->
                    roles.size == 1 && roles[0].name == "admin"
                }
            )
        }
    }

    @Test
    fun `removeClientRoles throws 404 when client not found`() = runTest {
        val request = AssignClientRolesRequest(clientId = clientId, roles = listOf("admin"))

        coEvery { keycloakAdminClient.getClientByClientId(realmName, clientId) } returns null

        val exception = assertThrows<ResponseStatusException> {
            service.removeClientRoles(realmName, userId, request)
        }

        assertTrue(exception.message!!.contains("Client '$clientId' not found"))
    }

    @Test
    fun `removeClientRoles throws 404 when role name not found`() = runTest {
        val request = AssignClientRolesRequest(clientId = clientId, roles = listOf("nonexistent-role"))

        coEvery { keycloakAdminClient.getClientByClientId(realmName, clientId) } returns clientRepresentation
        coEvery { keycloakAdminClient.getClientRoles(realmName, clientUuid) } returns listOf(adminRole, editorRole)

        val exception = assertThrows<ResponseStatusException> {
            service.removeClientRoles(realmName, userId, request)
        }

        assertTrue(exception.message!!.contains("Role 'nonexistent-role' not found"))
    }

    // ==================== getUserAttributes ====================

    @Test
    fun `getUserAttributes returns attributes from user`() = runTest {
        val user = UserRepresentation(
            username = "testuser",
            attributes = mapOf(
                "department" to listOf("Engineering"),
                "location" to listOf("New York", "Remote")
            )
        )

        coEvery { keycloakAdminClient.getUser(realmName, userId) } returns user

        val result = service.getUserAttributes(realmName, userId)

        assertEquals(2, result.attributes.size)
        assertEquals(listOf("Engineering"), result.attributes["department"])
        assertEquals(listOf("New York", "Remote"), result.attributes["location"])
    }

    @Test
    fun `getUserAttributes returns empty map when user has no attributes`() = runTest {
        val user = UserRepresentation(
            username = "testuser",
            attributes = null
        )

        coEvery { keycloakAdminClient.getUser(realmName, userId) } returns user

        val result = service.getUserAttributes(realmName, userId)

        assertTrue(result.attributes.isEmpty())
    }

    // ==================== setUserAttribute ====================

    @Test
    fun `setUserAttribute sets attribute and updates user`() = runTest {
        val existingUser = UserRepresentation(
            username = "testuser",
            attributes = mapOf("existing-attr" to listOf("value1"))
        )

        coEvery { keycloakAdminClient.getUser(realmName, userId) } returns existingUser
        coEvery { keycloakAdminClient.updateUser(realmName, userId, any()) } returns Unit

        service.setUserAttribute(realmName, userId, "new-attr", listOf("new-value"))

        coVerify {
            keycloakAdminClient.updateUser(
                realmName,
                userId,
                match { updatedUser ->
                    updatedUser.attributes != null &&
                        updatedUser.attributes!!["existing-attr"] == listOf("value1") &&
                        updatedUser.attributes!!["new-attr"] == listOf("new-value")
                }
            )
        }
    }

    @Test
    fun `setUserAttribute overwrites existing attribute value`() = runTest {
        val existingUser = UserRepresentation(
            username = "testuser",
            attributes = mapOf("department" to listOf("Marketing"))
        )

        coEvery { keycloakAdminClient.getUser(realmName, userId) } returns existingUser
        coEvery { keycloakAdminClient.updateUser(realmName, userId, any()) } returns Unit

        service.setUserAttribute(realmName, userId, "department", listOf("Engineering"))

        coVerify {
            keycloakAdminClient.updateUser(
                realmName,
                userId,
                match { updatedUser ->
                    updatedUser.attributes!!["department"] == listOf("Engineering")
                }
            )
        }
    }

    @Test
    fun `setUserAttribute initializes attributes map when user has null attributes`() = runTest {
        val existingUser = UserRepresentation(
            username = "testuser",
            attributes = null
        )

        coEvery { keycloakAdminClient.getUser(realmName, userId) } returns existingUser
        coEvery { keycloakAdminClient.updateUser(realmName, userId, any()) } returns Unit

        service.setUserAttribute(realmName, userId, "new-attr", listOf("value"))

        coVerify {
            keycloakAdminClient.updateUser(
                realmName,
                userId,
                match { updatedUser ->
                    updatedUser.attributes != null &&
                        updatedUser.attributes!!.size == 1 &&
                        updatedUser.attributes!!["new-attr"] == listOf("value")
                }
            )
        }
    }

    // ==================== removeUserAttribute ====================

    @Test
    fun `removeUserAttribute removes attribute and updates user`() = runTest {
        val existingUser = UserRepresentation(
            username = "testuser",
            attributes = mapOf(
                "department" to listOf("Engineering"),
                "location" to listOf("NYC")
            )
        )

        coEvery { keycloakAdminClient.getUser(realmName, userId) } returns existingUser
        coEvery { keycloakAdminClient.updateUser(realmName, userId, any()) } returns Unit

        service.removeUserAttribute(realmName, userId, "department")

        coVerify {
            keycloakAdminClient.updateUser(
                realmName,
                userId,
                match { updatedUser ->
                    updatedUser.attributes != null &&
                        !updatedUser.attributes!!.containsKey("department") &&
                        updatedUser.attributes!!["location"] == listOf("NYC")
                }
            )
        }
    }

    @Test
    fun `removeUserAttribute handles user with null attributes gracefully`() = runTest {
        val existingUser = UserRepresentation(
            username = "testuser",
            attributes = null
        )

        coEvery { keycloakAdminClient.getUser(realmName, userId) } returns existingUser
        coEvery { keycloakAdminClient.updateUser(realmName, userId, any()) } returns Unit

        service.removeUserAttribute(realmName, userId, "nonexistent")

        coVerify {
            keycloakAdminClient.updateUser(
                realmName,
                userId,
                match { updatedUser ->
                    updatedUser.attributes != null && updatedUser.attributes!!.isEmpty()
                }
            )
        }
    }

    @Test
    fun `removeUserAttribute removes only the specified attribute`() = runTest {
        val existingUser = UserRepresentation(
            username = "testuser",
            attributes = mapOf(
                "attr1" to listOf("val1"),
                "attr2" to listOf("val2"),
                "attr3" to listOf("val3")
            )
        )

        coEvery { keycloakAdminClient.getUser(realmName, userId) } returns existingUser
        coEvery { keycloakAdminClient.updateUser(realmName, userId, any()) } returns Unit

        service.removeUserAttribute(realmName, userId, "attr2")

        coVerify {
            keycloakAdminClient.updateUser(
                realmName,
                userId,
                match { updatedUser ->
                    updatedUser.attributes != null &&
                        updatedUser.attributes!!.size == 2 &&
                        updatedUser.attributes!!["attr1"] == listOf("val1") &&
                        updatedUser.attributes!!["attr3"] == listOf("val3") &&
                        !updatedUser.attributes!!.containsKey("attr2")
                }
            )
        }
    }
}

package com.keeplearning.auth.realm.service

import com.keeplearning.auth.audit.service.AuditService
import com.keeplearning.auth.domain.entity.KcClient
import com.keeplearning.auth.domain.entity.KcRealm
import com.keeplearning.auth.domain.entity.KcRole
import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.KcRoleRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.RoleRepresentation
import com.keeplearning.auth.realm.dto.CreateRoleRequest
import com.keeplearning.auth.realm.dto.RoleResponse
import com.keeplearning.auth.realm.dto.UpdateRoleRequest
import io.mockk.coEvery
import io.mockk.coVerify
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@ExtendWith(MockKExtension::class)
class RoleServiceTest {

    @MockK
    lateinit var keycloakClient: KeycloakAdminClient

    @MockK
    lateinit var roleRepository: KcRoleRepository

    @MockK
    lateinit var realmRepository: KcRealmRepository

    @MockK
    lateinit var clientRepository: KcClientRepository

    @MockK
    lateinit var auditService: AuditService

    private lateinit var roleService: RoleService

    private val realmId = UUID.randomUUID()
    private val clientDbId = UUID.randomUUID()
    private val roleId = UUID.randomUUID()
    private val realmName = "test-realm"
    private val clientIdStr = "my-client"
    private val clientKeycloakId = "kc-client-uuid"

    private val realm = KcRealm(
        id = realmId,
        realmName = realmName,
        keycloakId = "kc-realm-id"
    )

    private val client = KcClient(
        id = clientDbId,
        realmId = realmId,
        clientId = clientIdStr,
        name = "My Client",
        keycloakId = clientKeycloakId
    )

    private val realmRole = KcRole(
        id = roleId,
        realmId = realmId,
        clientId = null,
        name = "admin",
        description = "Administrator role",
        composite = false,
        keycloakId = "kc-role-id"
    )

    private val clientRole = KcRole(
        id = roleId,
        realmId = realmId,
        clientId = clientDbId,
        name = "editor",
        description = "Editor role",
        composite = false,
        keycloakId = "$clientKeycloakId/editor"
    )

    @BeforeEach
    fun setup() {
        roleService = RoleService(
            keycloakClient,
            roleRepository,
            realmRepository,
            clientRepository,
            auditService
        )
    }

    // ==================== createRealmRole ====================

    @Test
    fun `createRealmRole creates role successfully`() = runTest {
        val request = CreateRoleRequest(name = "admin", description = "Administrator role")
        val kcRoleRep = RoleRepresentation(id = "kc-role-id", name = "admin", description = "Administrator role")

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.createRealmRole(realmName, any()) } returns Unit
        coEvery { keycloakClient.getRealmRole(realmName, "admin") } returns kcRoleRep
        every { roleRepository.save(any()) } answers {
            val saved = firstArg<KcRole>()
            Mono.just(saved.copy(id = roleId))
        }

        val response = roleService.createRealmRole(realmName, request)

        assertEquals(roleId, response.id)
        assertEquals("admin", response.name)
        assertEquals("Administrator role", response.description)
        assertFalse(response.composite)

        coVerify { keycloakClient.createRealmRole(realmName, any()) }
        coVerify { keycloakClient.getRealmRole(realmName, "admin") }
        verify { roleRepository.save(any()) }
    }

    @Test
    fun `createRealmRole throws 404 when realm not found`() = runTest {
        val request = CreateRoleRequest(name = "admin", description = "Administrator role")

        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.createRealmRole("unknown-realm", request)
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Realm 'unknown-realm' not found", ex.reason)
    }

    // ==================== createClientRole ====================

    @Test
    fun `createClientRole creates role successfully`() = runTest {
        val request = CreateRoleRequest(name = "editor", description = "Editor role")

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, clientIdStr) } returns Mono.just(client)
        coEvery { keycloakClient.createClientRole(realmName, clientKeycloakId, any()) } returns Unit
        every { roleRepository.save(any()) } answers {
            val saved = firstArg<KcRole>()
            Mono.just(saved.copy(id = roleId))
        }

        val response = roleService.createClientRole(realmName, clientIdStr, request)

        assertEquals(roleId, response.id)
        assertEquals("editor", response.name)
        assertEquals("Editor role", response.description)
        assertFalse(response.composite)

        coVerify { keycloakClient.createClientRole(realmName, clientKeycloakId, any()) }
        verify { roleRepository.save(any()) }
    }

    @Test
    fun `createClientRole throws 404 when realm not found`() = runTest {
        val request = CreateRoleRequest(name = "editor")

        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.createClientRole("unknown-realm", clientIdStr, request)
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Realm 'unknown-realm' not found", ex.reason)
    }

    @Test
    fun `createClientRole throws 404 when client not found`() = runTest {
        val request = CreateRoleRequest(name = "editor")

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "no-such-client") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.createClientRole(realmName, "no-such-client", request)
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Client 'no-such-client' not found", ex.reason)
    }

    // ==================== listRealmRoles ====================

    @Test
    fun `listRealmRoles returns roles for realm`() = runTest {
        val role2Id = UUID.randomUUID()
        val role2 = KcRole(
            id = role2Id,
            realmId = realmId,
            clientId = null,
            name = "user",
            description = "User role",
            composite = false,
            keycloakId = "kc-role-user"
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { roleRepository.findByRealmIdAndClientIdIsNull(realmId) } returns Flux.fromIterable(listOf(realmRole, role2))

        val result = roleService.listRealmRoles(realmName)

        assertEquals(2, result.size)
        assertEquals("admin", result[0].name)
        assertEquals("user", result[1].name)
    }

    @Test
    fun `listRealmRoles throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("missing-realm") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.listRealmRoles("missing-realm")
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Realm 'missing-realm' not found", ex.reason)
    }

    // ==================== listClientRoles ====================

    @Test
    fun `listClientRoles returns roles for client`() = runTest {
        val role2Id = UUID.randomUUID()
        val role2 = KcRole(
            id = role2Id,
            realmId = realmId,
            clientId = clientDbId,
            name = "viewer",
            description = "Viewer role",
            composite = false,
            keycloakId = "$clientKeycloakId/viewer"
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, clientIdStr) } returns Mono.just(client)
        every { roleRepository.findByClientId(clientDbId) } returns Flux.fromIterable(listOf(clientRole, role2))

        val result = roleService.listClientRoles(realmName, clientIdStr)

        assertEquals(2, result.size)
        assertEquals("editor", result[0].name)
        assertEquals("viewer", result[1].name)
    }

    @Test
    fun `listClientRoles throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("missing-realm") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.listClientRoles("missing-realm", clientIdStr)
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Realm 'missing-realm' not found", ex.reason)
    }

    @Test
    fun `listClientRoles throws 404 when client not found`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "unknown-client") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.listClientRoles(realmName, "unknown-client")
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Client 'unknown-client' not found", ex.reason)
    }

    // ==================== getRealmRole ====================

    @Test
    fun `getRealmRole returns role successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { roleRepository.findByRealmIdAndNameAndClientIdIsNull(realmId, "admin") } returns Mono.just(realmRole)

        val response = roleService.getRealmRole(realmName, "admin")

        assertEquals(roleId, response.id)
        assertEquals("admin", response.name)
        assertEquals("Administrator role", response.description)
        assertFalse(response.composite)
    }

    @Test
    fun `getRealmRole throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("no-realm") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.getRealmRole("no-realm", "admin")
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Realm 'no-realm' not found", ex.reason)
    }

    @Test
    fun `getRealmRole throws 404 when role not found`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { roleRepository.findByRealmIdAndNameAndClientIdIsNull(realmId, "nonexistent") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.getRealmRole(realmName, "nonexistent")
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Role 'nonexistent' not found", ex.reason)
    }

    // ==================== updateRealmRole ====================

    @Test
    fun `updateRealmRole updates description successfully`() = runTest {
        val request = UpdateRoleRequest(description = "Updated admin description")

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { roleRepository.findByRealmIdAndNameAndClientIdIsNull(realmId, "admin") } returns Mono.just(realmRole)
        coEvery { keycloakClient.updateRealmRole(realmName, "admin", any()) } returns Unit
        every { roleRepository.save(any()) } answers {
            val saved = firstArg<KcRole>()
            Mono.just(saved)
        }

        val response = roleService.updateRealmRole(realmName, "admin", request)

        assertEquals(roleId, response.id)
        assertEquals("admin", response.name)
        assertEquals("Updated admin description", response.description)
        assertFalse(response.composite)

        coVerify { keycloakClient.updateRealmRole(realmName, "admin", any()) }
        verify { roleRepository.save(match { it.description == "Updated admin description" }) }
    }

    @Test
    fun `updateRealmRole keeps existing description when request description is null`() = runTest {
        val request = UpdateRoleRequest(description = null)

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { roleRepository.findByRealmIdAndNameAndClientIdIsNull(realmId, "admin") } returns Mono.just(realmRole)
        coEvery { keycloakClient.updateRealmRole(realmName, "admin", any()) } returns Unit
        every { roleRepository.save(any()) } answers {
            val saved = firstArg<KcRole>()
            Mono.just(saved)
        }

        val response = roleService.updateRealmRole(realmName, "admin", request)

        assertEquals("Administrator role", response.description)

        verify { roleRepository.save(match { it.description == "Administrator role" }) }
    }

    @Test
    fun `updateRealmRole throws 404 when realm not found`() = runTest {
        val request = UpdateRoleRequest(description = "New description")

        every { realmRepository.findByRealmName("missing") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.updateRealmRole("missing", "admin", request)
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Realm 'missing' not found", ex.reason)
    }

    @Test
    fun `updateRealmRole throws 404 when role not found`() = runTest {
        val request = UpdateRoleRequest(description = "New description")

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { roleRepository.findByRealmIdAndNameAndClientIdIsNull(realmId, "ghost") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.updateRealmRole(realmName, "ghost", request)
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Role 'ghost' not found", ex.reason)
    }

    // ==================== deleteRealmRole ====================

    @Test
    fun `deleteRealmRole deletes role successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { roleRepository.findByRealmIdAndNameAndClientIdIsNull(realmId, "admin") } returns Mono.just(realmRole)
        coEvery { keycloakClient.deleteRealmRole(realmName, "admin") } returns Unit
        every { roleRepository.delete(realmRole) } returns Mono.empty()

        roleService.deleteRealmRole(realmName, "admin")

        coVerify { keycloakClient.deleteRealmRole(realmName, "admin") }
        verify { roleRepository.delete(realmRole) }
    }

    @Test
    fun `deleteRealmRole throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("nope") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.deleteRealmRole("nope", "admin")
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Realm 'nope' not found", ex.reason)
    }

    @Test
    fun `deleteRealmRole throws 404 when role not found`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { roleRepository.findByRealmIdAndNameAndClientIdIsNull(realmId, "missing-role") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.deleteRealmRole(realmName, "missing-role")
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Role 'missing-role' not found", ex.reason)
    }

    // ==================== deleteClientRole ====================

    @Test
    fun `deleteClientRole deletes role successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, clientIdStr) } returns Mono.just(client)
        every { roleRepository.findByClientIdAndName(clientDbId, "editor") } returns Mono.just(clientRole)
        coEvery { keycloakClient.deleteClientRole(realmName, clientKeycloakId, "editor") } returns Unit
        every { roleRepository.delete(clientRole) } returns Mono.empty()

        roleService.deleteClientRole(realmName, clientIdStr, "editor")

        coVerify { keycloakClient.deleteClientRole(realmName, clientKeycloakId, "editor") }
        verify { roleRepository.delete(clientRole) }
    }

    @Test
    fun `deleteClientRole throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("gone") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.deleteClientRole("gone", clientIdStr, "editor")
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Realm 'gone' not found", ex.reason)
    }

    @Test
    fun `deleteClientRole throws 404 when client not found`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, "bad-client") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.deleteClientRole(realmName, "bad-client", "editor")
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Client 'bad-client' not found", ex.reason)
    }

    @Test
    fun `deleteClientRole throws 404 when role not found`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, clientIdStr) } returns Mono.just(client)
        every { roleRepository.findByClientIdAndName(clientDbId, "phantom") } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            roleService.deleteClientRole(realmName, clientIdStr, "phantom")
        }
        assertEquals(404, ex.statusCode.value())
        assertEquals("Role 'phantom' not found", ex.reason)
    }

    // ==================== listRealmRoles empty result ====================

    @Test
    fun `listRealmRoles returns empty list when no roles exist`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { roleRepository.findByRealmIdAndClientIdIsNull(realmId) } returns Flux.empty()

        val result = roleService.listRealmRoles(realmName)

        assertEquals(0, result.size)
    }

    // ==================== listClientRoles empty result ====================

    @Test
    fun `listClientRoles returns empty list when no roles exist`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, clientIdStr) } returns Mono.just(client)
        every { roleRepository.findByClientId(clientDbId) } returns Flux.empty()

        val result = roleService.listClientRoles(realmName, clientIdStr)

        assertEquals(0, result.size)
    }

    // ==================== createRealmRole with null description ====================

    @Test
    fun `createRealmRole with null description`() = runTest {
        val request = CreateRoleRequest(name = "basic-role", description = null)
        val kcRoleRep = RoleRepresentation(id = "kc-basic-id", name = "basic-role")

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.createRealmRole(realmName, any()) } returns Unit
        coEvery { keycloakClient.getRealmRole(realmName, "basic-role") } returns kcRoleRep
        every { roleRepository.save(any()) } answers {
            val saved = firstArg<KcRole>()
            Mono.just(saved.copy(id = roleId))
        }

        val response = roleService.createRealmRole(realmName, request)

        assertEquals("basic-role", response.name)
        assertNull(response.description)
        assertFalse(response.composite)
    }

    // ==================== createRealmRole keycloakId fallback ====================

    @Test
    fun `createRealmRole uses name as keycloakId when keycloak returns null id`() = runTest {
        val request = CreateRoleRequest(name = "fallback-role", description = "test")
        val kcRoleRep = RoleRepresentation(id = null, name = "fallback-role")

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.createRealmRole(realmName, any()) } returns Unit
        coEvery { keycloakClient.getRealmRole(realmName, "fallback-role") } returns kcRoleRep
        every { roleRepository.save(any()) } answers {
            val saved = firstArg<KcRole>()
            Mono.just(saved.copy(id = roleId))
        }

        roleService.createRealmRole(realmName, request)

        verify {
            roleRepository.save(match { it.keycloakId == "fallback-role" })
        }
    }

    // ==================== createClientRole keycloakId format ====================

    @Test
    fun `createClientRole sets keycloakId as clientKeycloakId slash roleName`() = runTest {
        val request = CreateRoleRequest(name = "writer", description = "Writer role")

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { clientRepository.findByRealmIdAndClientId(realmId, clientIdStr) } returns Mono.just(client)
        coEvery { keycloakClient.createClientRole(realmName, clientKeycloakId, any()) } returns Unit
        every { roleRepository.save(any()) } answers {
            val saved = firstArg<KcRole>()
            Mono.just(saved.copy(id = roleId))
        }

        roleService.createClientRole(realmName, clientIdStr, request)

        verify {
            roleRepository.save(match { it.keycloakId == "$clientKeycloakId/writer" })
        }
    }
}
